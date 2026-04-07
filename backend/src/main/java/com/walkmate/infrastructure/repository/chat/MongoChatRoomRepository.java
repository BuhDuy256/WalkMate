package com.walkmate.infrastructure.repository.chat;

import com.walkmate.application.chat.ChatRoomRepository;
import com.walkmate.infrastructure.repository.chat.document.ChatRoomDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * MongoDB adapter for the ChatRoomRepository port.
 *
 * Architecture notes:
 * - No MongoTransactionManager is registered. Spring @Transactional manages only the
 *   DataSourceTransactionManager (PostgreSQL). MongoDB writes are dispatched via
 *   afterCommit() hooks and are non-transactional at the Spring level.
 * - initRoom() uses upsert with $setOnInsert for idempotency and safe retries.
 * - closeRoom() uses $set — idempotent; closing an already-closed room updates
 *   closedAt but leaves behaviour correct.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoChatRoomRepository implements ChatRoomRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public void initRoom(String sessionId) {
        // Upsert: create only if not already present (handles retries safely)
        Query query = Query.query(Criteria.where("_id").is(sessionId));
        Update update = new Update()
                .setOnInsert("sessionId", sessionId)
                .setOnInsert("status",    "OPEN")
                .setOnInsert("createdAt", Instant.now())
                .setOnInsert("closedAt",  null);

        mongoTemplate.upsert(query, update, ChatRoomDocument.class);
        log.debug("Chat room initialised: sessionId={}", sessionId);
    }

    @Override
    public void closeRoom(String sessionId) {
        Query query = Query.query(Criteria.where("_id").is(sessionId));
        Update update = new Update()
                .set("status",   "CLOSED")
                .set("closedAt", Instant.now());

        mongoTemplate.updateFirst(query, update, ChatRoomDocument.class);
        log.debug("Chat room closed: sessionId={}", sessionId);
    }
}
