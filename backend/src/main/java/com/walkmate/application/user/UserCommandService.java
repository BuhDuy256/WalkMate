package com.walkmate.application.user;

import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.RefreshToken;
import com.walkmate.domain.user.RefreshTokenRepository;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserErrorCode;
import com.walkmate.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCommandService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;

    @Transactional
    public LoginResult loginUser(LoginUserCommand command) {
        String normalizedEmail = User.normalizeEmail(command.email());

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_INVALID_CREDENTIALS));

        user.authenticate(command.password(), passwordEncoder::matches);

        TokenPair tokenPair = tokenProvider.generateTokenPair(user);

        RefreshToken refreshToken = RefreshToken.issue(user.getUserId(), tokenPair.refreshToken());
        refreshTokenRepository.save(refreshToken);

        userRepository.save(user);

        return new LoginResult(tokenPair.accessToken(), tokenPair.accessTokenExpiresIn());
    }

    @Transactional
    public void updateFcmToken(UpdateFcmTokenCommand command) {
        userRepository.updateFcmToken(command.userId(), command.token());
    }

    @Transactional
    public User registerUser(RegisterUserCommand command) {
        String normalizedEmail = User.normalizeEmail(command.email());

        userRepository.findByEmail(normalizedEmail)
                .ifPresent(existingUser -> {
                    throw new DomainException(UserErrorCode.USER_ALREADY_EXISTS);
                });

        User user = User.register(
                command.fullName(),
                normalizedEmail,
                passwordEncoder.encode(command.password())
        );

        return userRepository.save(user);
    }
}
