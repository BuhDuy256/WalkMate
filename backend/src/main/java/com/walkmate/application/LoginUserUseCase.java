package com.walkmate.application;

import com.walkmate.domain.session.RefreshToken;
import com.walkmate.domain.session.RefreshTokenRepository;
import com.walkmate.domain.user.InvalidCredentialsException;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginUserUseCase {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional
    public LoginResult execute(LoginUserCommand command) {
        String normalizedEmail = User.normalizeEmail(command.email());

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE));

        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        TokenPair tokenPair = tokenService.generateTokenPair(user);

        RefreshToken refreshToken = RefreshToken.issue(user.getUserId(), tokenPair.refreshToken());
        refreshTokenRepository.save(refreshToken);

        user.markLoggedIn();
        userRepository.save(user);

        return new LoginResult(tokenPair.accessToken(), tokenPair.accessTokenExpiresIn());
    }
}
