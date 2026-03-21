package com.walkmate.infrastructure.repository;

import com.walkmate.domain.session.RefreshToken;
import com.walkmate.domain.session.RefreshTokenRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRefreshTokenRepository extends JpaRepository<RefreshToken, UUID>, RefreshTokenRepository {
}
