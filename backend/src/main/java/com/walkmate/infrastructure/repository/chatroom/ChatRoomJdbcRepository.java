package com.walkmate.infrastructure.repository.chatroom;

import com.walkmate.domain.chatroom.ChatMessage;
import com.walkmate.domain.chatroom.ChatRoom;
import com.walkmate.domain.chatroom.ChatRoomRepository;
import com.walkmate.domain.chatroom.ChatRoomStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link ChatRoomRepository}.
 *
 * Pessimistic locking (§9.5 W2 / §9.6):
 *   Both {@link #findById} and {@link #findBySessionId} append {@code FOR UPDATE}
 *   so that the ChatRoom status guard inside the domain entity is evaluated against
 *   the committed DB state, not a stale in-memory snapshot.
 *
 * Message ordering (§9.8):
 *   All message queries use {@code ORDER BY created_at ASC, message_id ASC}.
 *   {@code created_at} is the primary sort key; {@code message_id} (UUID, lexicographic)
 *   is the stable tiebreaker for identical timestamps.
 */
@Repository
@RequiredArgsConstructor
public class ChatRoomJdbcRepository implements ChatRoomRepository {

    private final JdbcClient jdbcClient;

    // -------------------------------------------------------------------------
    // ChatRoom persistence
    // -------------------------------------------------------------------------

    @Override
    public ChatRoom save(ChatRoom chatRoom) {
        final String upsertRoom = """
                INSERT INTO chat_room (chat_room_id, session_id, status, close_at)
                VALUES (:chatRoomId, :sessionId, CAST(:status AS chat_room_status), :closeAt)
                ON CONFLICT (chat_room_id) DO UPDATE SET
                    status   = CAST(EXCLUDED.status AS chat_room_status),
                    close_at = EXCLUDED.close_at
                """;

        jdbcClient.sql(upsertRoom)
                .param("chatRoomId", UUID.fromString(chatRoom.getId()))
                .param("sessionId",  UUID.fromString(chatRoom.getSessionId()))
                .param("status",     chatRoom.getStatus().name())
                .param("closeAt",    toTimestamp(chatRoom.getCloseAt()))
                .update();

        // Persist any messages that were added or modified in the current unit-of-work.
        // ON CONFLICT updates read_at for already-persisted messages (read-receipt flow).
        for (ChatMessage message : chatRoom.getMessages()) {
            saveMessage(message);
        }

        return chatRoom;
    }

    @Override
    public Optional<ChatRoom> findById(String chatRoomId) {
        final String sql = """
                SELECT
                    cr.chat_room_id::text,
                    cr.session_id::text,
                    ws.user1_id::text  AS participant_a,
                    ws.user2_id::text  AS participant_b,
                    cr.status::text,
                    cr.close_at
                FROM chat_room cr
                JOIN walk_session ws ON ws.session_id = cr.session_id
                WHERE cr.chat_room_id = :chatRoomId
                FOR UPDATE OF cr
                """;

        return jdbcClient.sql(sql)
                .param("chatRoomId", UUID.fromString(chatRoomId))
                .query((rs, rowNum) -> mapChatRoomRow(rs))
                .optional();
    }

    @Override
    public Optional<ChatRoom> findBySessionId(String sessionId) {
        final String sql = """
                SELECT
                    cr.chat_room_id::text,
                    cr.session_id::text,
                    ws.user1_id::text  AS participant_a,
                    ws.user2_id::text  AS participant_b,
                    cr.status::text,
                    cr.close_at
                FROM chat_room cr
                JOIN walk_session ws ON ws.session_id = cr.session_id
                WHERE cr.session_id = :sessionId
                FOR UPDATE OF cr
                """;

        return jdbcClient.sql(sql)
                .param("sessionId", UUID.fromString(sessionId))
                .query((rs, rowNum) -> mapChatRoomRow(rs))
                .optional();
    }

    // -------------------------------------------------------------------------
    // Message queries
    // -------------------------------------------------------------------------

    /**
     * Returns messages in the room ordered by {@code (created_at ASC, message_id ASC)}.
     *
     * When {@code afterMessageId} is {@code null}, all messages in the room are returned
     * (used for read-receipt hydration and initial load).
     *
     * When {@code afterMessageId} is provided, only messages strictly after that message
     * are returned (used for the §9.8 catch-up endpoint).
     * The boundary is defined by the tuple {@code (created_at, message_id)} of the anchor message.
     */
    @Override
    public List<ChatMessage> findMessagesAfter(String chatRoomId, String afterMessageId) {
        if (afterMessageId == null) {
            return findAllMessages(chatRoomId);
        }
        return findMessagesAfterAnchor(chatRoomId, afterMessageId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void saveMessage(ChatMessage message) {
        final String upsertMessage = """
                INSERT INTO chat_message (message_id, chat_room_id, sender_id, content, created_at, read_at)
                VALUES (:messageId, :chatRoomId, :senderId, :content, :createdAt, :readAt)
                ON CONFLICT (message_id) DO UPDATE SET
                    read_at = EXCLUDED.read_at
                """;

        jdbcClient.sql(upsertMessage)
                .param("messageId",   UUID.fromString(message.getId()))
                .param("chatRoomId",  UUID.fromString(message.getChatRoomId()))
                .param("senderId",    UUID.fromString(message.getSenderId()))
                .param("content",     message.getContent())
                .param("createdAt",   Timestamp.from(message.getCreatedAt()))
                .param("readAt",      toTimestamp(message.getReadAt()))
                .update();
    }

    private List<ChatMessage> findAllMessages(String chatRoomId) {
        final String sql = """
                SELECT
                    message_id::text,
                    chat_room_id::text,
                    sender_id::text,
                    content,
                    created_at,
                    read_at
                FROM chat_message
                WHERE chat_room_id = :chatRoomId
                ORDER BY created_at ASC, message_id::text ASC
                """;

        return jdbcClient.sql(sql)
                .param("chatRoomId", UUID.fromString(chatRoomId))
                .query((rs, rowNum) -> mapMessageRow(rs))
                .list();
    }

    private List<ChatMessage> findMessagesAfterAnchor(String chatRoomId, String afterMessageId) {
        final String sql = """
                SELECT
                    m.message_id::text,
                    m.chat_room_id::text,
                    m.sender_id::text,
                    m.content,
                    m.created_at,
                    m.read_at
                FROM chat_message m
                WHERE m.chat_room_id = :chatRoomId
                  AND (m.created_at, m.message_id::text) > (
                      SELECT anchor.created_at, anchor.message_id::text
                      FROM chat_message anchor
                      WHERE anchor.message_id = :afterMessageId
                  )
                ORDER BY m.created_at ASC, m.message_id::text ASC
                """;

        return jdbcClient.sql(sql)
                .param("chatRoomId",     UUID.fromString(chatRoomId))
                .param("afterMessageId", UUID.fromString(afterMessageId))
                .query((rs, rowNum) -> mapMessageRow(rs))
                .list();
    }

    private ChatRoom mapChatRoomRow(ResultSet rs) throws SQLException {
        Timestamp closeAtTs = rs.getTimestamp("close_at");
        return new ChatRoom(
                rs.getString("chat_room_id"),
                rs.getString("session_id"),
                rs.getString("participant_a"),
                rs.getString("participant_b"),
                ChatRoomStatus.valueOf(rs.getString("status")),
                closeAtTs != null ? closeAtTs.toInstant() : null,
                Clock.systemUTC()
        );
    }

    private ChatMessage mapMessageRow(ResultSet rs) throws SQLException {
        Timestamp readAtTs = rs.getTimestamp("read_at");
        return new ChatMessage(
                rs.getString("message_id"),
                rs.getString("chat_room_id"),
                rs.getString("sender_id"),
                rs.getString("content"),
                rs.getTimestamp("created_at").toInstant(),
                readAtTs != null ? readAtTs.toInstant() : null
        );
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }
}
