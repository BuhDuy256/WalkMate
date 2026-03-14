package com.walkmate.infrastructure.repository.entity;

import com.walkmate.domain.session.AbortReason;
import com.walkmate.domain.session.SessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "walk_session")
@Getter
@Setter
public class WalkSessionJpaEntity {
  @Id
  @Column(name = "session_id", nullable = false)
  private UUID sessionId;

  @Column(name = "user1_id", nullable = false)
  private UUID user1Id;

  @Column(name = "user2_id", nullable = false)
  private UUID user2Id;

  @Column(name = "scheduled_start_time", nullable = false)
  private Instant scheduledStartTime;

  @Column(name = "scheduled_end_time", nullable = false)
  private Instant scheduledEndTime;

  @Column(name = "user1_activated_at")
  private Instant user1ActivatedAt;

  @Column(name = "user2_activated_at")
  private Instant user2ActivatedAt;

  @Column(name = "actual_start_time")
  private Instant actualStartTime;

  @Column(name = "actual_end_time")
  private Instant actualEndTime;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private SessionStatus status;

  @Column(name = "total_distance", nullable = false)
  private BigDecimal totalDistance;

  @Column(name = "total_duration", nullable = false)
  private long totalDuration;

  @Column(name = "cancellation_reason")
  private String cancellationReason;

  @Column(name = "cancelled_by")
  private UUID cancelledBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "abort_reason")
  private AbortReason abortReason;

  @Version
  @Column(name = "version", nullable = false)
  private long version;
}
