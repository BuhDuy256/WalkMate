package com.walkmate.infrastructure.repository;

import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUserRepository extends JpaRepository<User, UUID>, UserRepository {
}