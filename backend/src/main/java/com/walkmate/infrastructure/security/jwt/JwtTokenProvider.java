package com.walkmate.infrastructure.security.jwt;

import com.walkmate.application.user.TokenPair;
import com.walkmate.application.user.TokenProvider;
import com.walkmate.domain.user.User;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider implements TokenProvider {

    private final JwtEncoder jwtEncoder;

    @Value("${app.jwt.access-token-ttl-seconds:900}")
    private long accessTokenTtlSeconds;

    @Value("${app.jwt.refresh-token-ttl-seconds:2592000}")
    private long refreshTokenTtlSeconds;

    @Override
    public TokenPair generateTokenPair(User user) {
        String accessToken = generateToken(user, accessTokenTtlSeconds, "access");
        String refreshToken = generateToken(user, refreshTokenTtlSeconds, "refresh");
        return new TokenPair(accessToken, accessTokenTtlSeconds, refreshToken, refreshTokenTtlSeconds);
    }

    private String generateToken(User user, long ttlSeconds, String tokenType) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("walkmate-backend")
                .subject(user.getUserId().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .claim("email", user.getEmail())
                .claim("token_type", tokenType)
                .claim("jti", UUID.randomUUID().toString())
                .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}
