package com.walkmate.application;

import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserAlreadyExistsException;
import com.walkmate.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User execute(RegisterUserCommand command) {
        String normalizedEmail = User.normalizeEmail(command.email());

        userRepository.findByEmail(normalizedEmail)
                .ifPresent(existingUser -> {
                    throw new UserAlreadyExistsException(normalizedEmail);
                });

        User user = User.register(
                command.fullName(),
                normalizedEmail,
                passwordEncoder.encode(command.password())
        );

        return userRepository.save(user);
    }
}