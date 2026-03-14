package com.walkmate.infrastructure.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "session_points")
@Getter
@Setter
public class SessionPointJpaEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator
  @Column(name = "point_id", nullable = false)
  private UUID pointId;

  @Column(name = "session_id", nullable = false)
  private UUID sessionId;

  @Column(name = "point_order", nullable = false)
  private int pointOrder;

  @Column(name = "latitude", nullable = false)
  private double latitude;

  @Column(name = "longitude", nullable = false)
  private double longitude;

  @Column(name = "time", nullable = false)
  private long time;
}
