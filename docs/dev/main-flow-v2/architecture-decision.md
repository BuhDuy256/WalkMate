Architectural Proposal: Supabase + MongoDB Atlas + FCM in Spring Boot

  What the Codebase Analysis Revealed

  Current state (confirmed from code):
  - Supabase (PostgreSQL): All business data goes through spring-boot-starter-jdbc + hand-rolled *JdbcRepository
  classes. Spring @Transactional on the DataSourceTransactionManager manages all PostgreSQL operations.
  - FCM: Already fully wired. FirebaseConfig + FcmNotificationProvider + PushNotificationProvider port exist. Only
  sendMatchFound() is currently implemented. The FcmNotificationProvider is the correct infra adapter — no Firebase
  types leak above the infra layer.
  - NotificationPublisher (domain port): NotificationPublisherImpl only persists to the notification table in Supabase.
  FCM is called manually and only in MatchingCommandService.findOrCreateProposal(). SESSION_CONFIRMED, SESSION_ACTIVE,
  REVIEW_REQUESTED are never pushed via FCM — DB-persist only.
  - Chat: Zero code. V101 dropped the SQL tables. No domain model, no repository, no MongoDB dependency in build.gradle.
   The P-3 atomic block in acceptProposal() creates the WalkSession but never initializes a chat room (GAP-8).

  ---
  The Core Architectural Conflict

  The fundamental problem is transaction boundary mismatch:

  ▎ MatchingCommandService.acceptProposal() is a single @Transactional method managing a PostgreSQL JDBC transaction.
  MongoDB has its own transaction mechanism. Attempting to include a MongoDB write inside the same Spring @Transactional
   scope will either silently not participate in the JDBC transaction, or fail entirely if a MongoTransactionManager is
  registered alongside DataSourceTransactionManager.

  A distributed transaction across PostgreSQL + MongoDB (2PC) is over-engineered and fragile — not warranted here.

  ---
  Proposed Architecture

  Principle: PostgreSQL is the Source of Truth. MongoDB is Derived, Fire-and-Continue.

  ---
  1. MongoDB Atlas — Chat Layer

  Data Model (MongoDB chat_rooms collection):
  {
    _id: <sessionId>,           // UUID string, the PK from WalkSession
    status: "OPEN" | "CLOSED",  // convenience field, NOT the source of truth for S-7
    createdAt: ISODate,
    closedAt: ISODate | null
  }
  The messages sub-collection stays separate (chat_messages, with sessionId as a reference field) to avoid unbounded
  document growth.

  Layering:
  domain/chat/ChatRoomRepository (port interface — knows nothing about MongoDB)
      ↑ implements
  infrastructure/repository/chat/MongoChatRoomRepository (@Repository, Spring Data MongoDB)
  The ChatRoom document class (@Document) lives in the infrastructure layer only. The domain/application layer interacts
   only via the ChatRoomRepository port, keeping MongoDB types fully contained in infrastructure.

  Transaction Strategy — afterCommit hook:

  Instead of writing to MongoDB inside @Transactional, use Spring's TransactionSynchronizationManager to register an
  afterCommit callback. MongoDB is written only after the PostgreSQL transaction has durably committed:

  // Inside acceptProposal(), after walkSessionRepository.save(session):
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
          try {
              chatRoomRepository.initRoom(session.getSessionId());
          } catch (Exception e) {
              log.error("Chat room init failed for session {}: {}", session.getSessionId(), e.getMessage());
              // Non-fatal — a reconciliation path handles this
          }
      }
  });

  This guarantees: if the PostgreSQL transaction rolls back, MongoDB is never written. If MongoDB write fails, the
  WalkSession still exists and is the source of truth. The chat write-lock rule (S-7) is enforced by checking
  WalkSession status in PostgreSQL before allowing writes to MongoDB — the MongoDB status field is a cached convenience,
   never the gatekeeper.

  The same afterCommit pattern applies to closeRoom() calls in SessionCommandService.completeSession(), cancelSession(),
   and abortSession().

  ---
  2. FCM — Expand the Existing Two-Channel Architecture

  The current design already has a clean separation:
  - Channel 1: NotificationPublisher (domain port) → DB persist (in-app feed)
  - Channel 2: PushNotificationProvider (application port) → FCM (real-time device push)

  The gap: Only MATCH_FOUND is pushed via FCM. All other lifecycle events (SESSION_CONFIRMED, SESSION_ACTIVE, etc.) go
  to DB only.

  Proposal — Unify dispatch in NotificationPublisherImpl:

  Upgrade NotificationPublisherImpl to dual-dispatch: persist to DB AND dispatch FCM. Add a generic method to
  PushNotificationProvider:

  // PushNotificationProvider (application port — add this method):
  void sendPush(String fcmToken, NotificationType type, Map<String, Object> payload);

  NotificationPublisherImpl is upgraded to:
  // NotificationPublisherImpl — dual-dispatch:
  @Override
  public void publish(Notification notification) {
      // 1. Always persist to DB (in-app feed)
      try { notificationRepository.save(notification); } catch (...) { log.error(...); }

      // 2. Best-effort FCM push (real-time channel)
      // Token lookup stays in the infra layer — no domain pollution
      userRepository.findFcmTokenByUserId(notification.getUserId()).ifPresent(token -> {
          if (!token.isBlank()) {
              pushNotificationProvider.sendPush(token, notification.getType(), notification.getPayload());
          }
      });
  }

  FcmNotificationProvider.sendPush() maps NotificationType → FCM data payload and dispatches. The existing
  sendMatchFound() can be retired or kept for backwards compatibility (it's called directly in findOrCreateProposal()).

  Why this is correct: All application services already call notificationPublisher.publish(). No call site changes
  needed in SessionCommandService or MatchingCommandService. FCM is silently added as a second channel behind the
  existing port.

  ---
  3. Configuration Strategy

  Two new env vars, mirroring the existing Supabase pattern:

  # application.properties additions:

  # MongoDB Atlas
  spring.data.mongodb.uri=${MONGODB_URI}
  spring.data.mongodb.database=${MONGODB_DATABASE:walkmate}

  # No changes needed for FCM — FIREBASE_CREDENTIALS already wired

  No MongoTransactionManager bean. By not registering one, Spring's @Transactional continues to manage only the
  PostgreSQL DataSourceTransactionManager. MongoDB operations are always non-transactional at the Spring level —
  atomicity within a single MongoDB write is guaranteed by MongoDB's own document-level atomicity.

  ---
  4. Dependency additions to build.gradle

  // MongoDB Atlas
  implementation 'org.springframework.boot:spring-boot-starter-data-mongodb'

  // Firebase Admin SDK already present — no change needed

  ---
  Summary: How the Three Data Stores Interact

  Android Client
        │
        ▼
  Spring Boot Backend
        │
        ├─ Supabase (PostgreSQL) ─── Source of Truth for all lifecycle state
        │   └─ @Transactional (JDBC DataSourceTransactionManager)
        │       WalkIntent / MatchProposal / WalkSession / Notification / User
        │
        ├─ MongoDB Atlas ─── Derived, eventual consistency, Chat data
        │   └─ afterCommit hooks (no Spring @Transactional participation)
        │       ChatRoom (keyed by sessionId) / ChatMessage
        │       Write-gate enforced by checking PostgreSQL WalkSession.status
        │
        └─ Firebase (FCM) ─── Fire-and-forget push channel
            └─ Never inside @Transactional
                Called via PushNotificationProvider port
                Failures always swallowed (never roll back business transactions)

  ---
  Key Design Decisions — Rationale

  ┌─────────────────────────────────────┬───────────────────────────────────────────────────────────────────────────┐
  │              Decision               │                                    Why                                    │
  ├─────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ afterCommit hook for MongoDB writes │ Prevents MongoDB from receiving writes from rolled-back PostgreSQL        │
  │                                     │ transactions. No distributed transaction needed.                          │
  ├─────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ MongoDB status field is a cache,    │ PostgreSQL WalkSession.status is always the authoritative check before    │
  │ not the S-7 gatekeeper              │ allowing chat writes. Avoids consistency issues if a closeRoom() call is  │
  │                                     │ delayed.                                                                  │
  ├─────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ No MongoTransactionManager          │ Avoids the ChainedTransactionManager complexity. MongoDB document-level   │
  │                                     │ atomicity is sufficient for single-document chat room operations.         │
  ├─────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ NotificationPublisherImpl owns      │ Zero call-site changes. FCM is transparently added behind the existing    │
  │ dual-dispatch                       │ port. All future notification types are automatically pushed without      │
  │                                     │ touching service code.                                                    │
  ├─────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────┤
  │ PushNotificationProvider stays as   │ Firebase types (Message, FirebaseMessaging) remain confined to            │
  │ application-layer port              │ infrastructure/notification only.                                         │
  └─────────────────────────────────────┴───────────────────────────────────────────────────────────────────────────┘

  ---
  Next steps pending your review: If you approve this proposal, I will proceed to write the detailed implementation
  steps (new files, exact code changes, migration additions) as a new docs/dev/main-flow-v2/architecture_decision.md and
   update implementation_plan.md with the Phase 2 MongoDB/FCM steps.