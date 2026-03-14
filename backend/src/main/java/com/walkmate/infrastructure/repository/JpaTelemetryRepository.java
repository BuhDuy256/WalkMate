package com.walkmate.infrastructure.repository;

import com.walkmate.domain.session.entity.WalkSessionTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JpaTelemetryRepository extends JpaRepository<WalkSessionTelemetry, UUID> {
}
