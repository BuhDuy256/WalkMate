package com.walkmate.domain.session.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "walk_session_telemetry")
public class WalkSessionTelemetry {

    @Id
    private UUID sessionId; // Shares ID with WalkSession

    @Column(nullable = false)
    private int steps;

    @Column(nullable = false)
    private double distanceMeters;

    @Column(nullable = false)
    private int calories;

    protected WalkSessionTelemetry() {}

    public WalkSessionTelemetry(UUID sessionId) {
        this.sessionId = sessionId;
        this.steps = 0;
        this.distanceMeters = 0.0;
        this.calories = 0;
    }

    // Highly optimized append logic without version locks
    public void incrementSync(int steps, double distanceMeters, int calories) {
        this.steps += steps;
        this.distanceMeters += distanceMeters;
        this.calories += calories;
    }

    public int getSteps() { return steps; }
    public double getDistanceMeters() { return distanceMeters; }
    public int getCalories() { return calories; }
}
