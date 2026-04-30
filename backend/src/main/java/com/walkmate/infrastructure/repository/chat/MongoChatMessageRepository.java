package com.walkmate.infrastructure.repository.chat;

import com.walkmate.domain.chat.ChatMessage;
import com.walkmate.domain.chat.ChatMessageRepository;
import com.walkmate.infrastructure.repository.chat.document.ChatMessageDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MongoDB adapter for the ChatMessageRepository port.
 *
 * Architecture notes:
 * - Non-transactional at the Spring level (same pattern as MongoChatRoomRepository).
 *   The WebSocket handler calls this from a background thread; no @Transactional
 *   needed since MongoDB writes are atomic at the document level.
 * - findLatestBySessionId fetches DESC by sentAt then reverses, giving callers
 *   a chronologically ordered (oldest-first) list without a secondary sort pass.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoChatMessageRepository implements ChatMessageRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public ChatMessage save(ChatMessage message) {
        ChatMessageDocument doc = ChatMessageDocument.from(message);
        mongoTemplate.insert(doc);
        log.debug("Message persisted: messageId={} sessionId={}", message.getMessageId(), message.getSessionId());
        return message;
    }

    @Override
    public List<ChatMessage> findLatestBySessionId(String sessionId, int limit) {
        Query query = Query.query(Criteria.where("sessionId").is(sessionId))
                .with(Sort.by(Sort.Direction.DESC, "sentAt"))
                .limit(limit);

        List<ChatMessageDocument> docs = mongoTemplate.find(query, ChatMessageDocument.class);
        Collections.reverse(docs); // oldest-first for UI chronological display

        return docs.stream()
                .map(doc -> new ChatMessage(
                        doc.getMessageId(),
                        doc.getSessionId(),
                        doc.getSenderId(),
                        doc.getSenderName(),
                        doc.getContent(),
                        doc.getSentAt()))
                .collect(Collectors.toList());
    }
}
