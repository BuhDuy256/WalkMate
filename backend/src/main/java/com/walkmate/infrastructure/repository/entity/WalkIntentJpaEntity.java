package com.walkmate.infrastructure.repository.entity;

import com.walkmate.domain.intent.IntentStatus;
import com.walkmate.domain.intent.WalkPurpose;
import com.walkmate.domain.valueobject.MatchingConstraints;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "walk_intent")
@Getter
@Setter
public class WalkIntentJpaEntity {
  @Id
  @Column(name = "intent_id", nullable = false)
  private UUID intentId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "location", nullable = false, columnDefinition = "geography(Point, 4326)")
  private Point location;

  @Column(name = "location_lat", nullable = false)
  private double locationLat;

  @Column(name = "location_lng", nullable = false)
  private double locationLng;

  @Column(name = "time_window_start", nullable = false)
  private Instant timeWindowStart;

  @Column(name = "time_window_end", nullable = false)
  private Instant timeWindowEnd;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", nullable = false)
  private WalkPurpose purpose;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "matching_constraints", columnDefinition = "jsonb")
  private MatchingConstraints matchingConstraints;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private IntentStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;
}
