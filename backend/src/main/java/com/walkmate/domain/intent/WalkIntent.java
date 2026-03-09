package com.walkmate.domain.intent;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FULL IMPLEMENTATION: WalkIntent Aggregate Root for the Coordination Phase.
 * Enforces Invariant 2 and acts as the Single Source of Truth as defined in db.sql.
 */
@Entity
@Table(name = "walk_intent")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // For JPA
public class WalkIntent {

    @Id
    @Column(name = "intent_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    // PostGIS Geography mapping
    @Column(name = "location", columnDefinition = "geography(Point,4326)", nullable = false)
    private Point location;

    @Column(name = "location_lat", nullable = false)
    private Double locationLat;

    @Column(name = "location_lng", nullable = false)
    private Double locationLng;

    @Column(name = "time_window_start", nullable = false)
    private LocalDateTime timeWindowStart;

    @Column(name = "time_window_end", nullable = false)
    private LocalDateTime timeWindowEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private WalkPurpose purpose;

    // Map matchFilter as JSONB
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "match_filter", columnDefinition = "jsonb")
    private MatchFilter matchFilter;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IntentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public WalkIntent(UUID id, UUID userId, Double lat, Double lng, LocalDateTime start, LocalDateTime end, WalkPurpose purpose, MatchFilter matchFilter, LocalDateTime expiresAt) {
        this.id = id;
        this.userId = userId;
        this.locationLat = lat;
        this.locationLng = lng;
        this.timeWindowStart = start;
        this.timeWindowEnd = end;
        this.purpose = purpose;
        this.matchFilter = matchFilter;
        this.status = IntentStatus.OPEN; 
        
        // JTS requires X,Y coordinates -> Longitude, Latitude order
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        this.location = geometryFactory.createPoint(new Coordinate(lng, lat));

        this.expiresAt = expiresAt;

        // DB Constraint check: valid_time_window
        if (end.isBefore(start) || end.isEqual(start)) {
            throw new IllegalArgumentException("Time window end must be after start time");
        }
        
        // DB Constraint check: future_time_window
        if (start.isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new IllegalArgumentException("Time window start must be in the future");
        }
        
        // DB Constraint check: valid_coordinates
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Invalid GPS coordinates");
        }
    }

    // --- DOMAIN BEHAVIORS ---

    public void cancel() {
        if (this.status != IntentStatus.OPEN && this.status != IntentStatus.MATCHED) {
            throw new IllegalStateException("Only OPEN or MATCHED intents can be cancelled");
        }
        this.status = IntentStatus.CANCELLED;
    }

    public void transitionToMatched() {
        if (this.status != IntentStatus.OPEN) {
            throw new IllegalStateException("Only OPEN intents can transition to MATCHED");
        }
        this.status = IntentStatus.MATCHED;
    }

    public void transitionToConfirmed() {
        if (this.status != IntentStatus.MATCHED) {
            throw new IllegalStateException("Only MATCHED intents can transition to CONFIRMED");
        }
        this.status = IntentStatus.CONFIRMED;
        // The coordination logic will now create a WalkSession
    }
}
