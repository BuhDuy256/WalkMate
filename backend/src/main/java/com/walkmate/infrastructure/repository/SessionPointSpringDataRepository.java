package com.walkmate.infrastructure.repository;

import com.walkmate.infrastructure.repository.entity.SessionPointJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SessionPointSpringDataRepository extends JpaRepository<SessionPointJpaEntity, UUID> {
}
