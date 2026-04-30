package com.walkmate.infrastructure.security.jwt;

import com.walkmate.application.session.SessionQrTokenProvider;
import com.walkmate.domain.session.SessionErrorCode;
import com.walkmate.domain.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtQrTokenProvider implements SessionQrTokenProvider {

    static final String PURPOSE_CLAIM   = "purpose";
    static final String PURPOSE_VALUE   = "SESSION_QR";
    static final String SESSION_CLAIM   = "session_id";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    @Value("${app.qr.token-ttl-seconds:300}")
    private long qrTokenTtlSeconds;

    @Override
    public String generateQrToken(String userId, String sessionId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("walkmate-backend")
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(qrTokenTtlSeconds))
                .claim(PURPOSE_CLAIM, PURPOSE_VALUE)
                .claim(SESSION_CLAIM, sessionId)
                .claim("jti", UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Override
    public String validateQrToken(String token, String expectedSessionId) {
        try {
            var jwt = jwtDecoder.decode(token);

            // Guard: must be a QR token, not an auth token
            if (!PURPOSE_VALUE.equals(jwt.getClaimAsString(PURPOSE_CLAIM))) {
                throw new DomainException(SessionErrorCode.SESSION_QR_TOKEN_INVALID);
            }
            // Guard: must belong to the requested session
            if (!expectedSessionId.equals(jwt.getClaimAsString(SESSION_CLAIM))) {
                throw new DomainException(SessionErrorCode.SESSION_QR_TOKEN_INVALID);
            }
            return jwt.getSubject();

        } catch (JwtException ex) {
            // JwtException covers both expired and signature failures from Spring Security
            String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
            if (msg.contains("expired") || msg.contains("exp")) {
                throw new DomainException(SessionErrorCode.SESSION_QR_TOKEN_EXPIRED);
            }
            throw new DomainException(SessionErrorCode.SESSION_QR_TOKEN_INVALID);
        }
    }
}
