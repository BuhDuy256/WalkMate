package com.walkmate.infrastructure.security.oauth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.walkmate.application.user.GoogleIdentity;
import com.walkmate.application.user.GoogleTokenVerifier;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.UserErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirebaseTokenVerifier implements GoogleTokenVerifier {

    private final FirebaseAuth firebaseAuth;

    @Override
    public GoogleIdentity verify(String firebaseIdToken) {
        try {
            // checkRevoked=false: skips the extra Firebase revocation-status network call.
            // That call requires the service account to have firebaseauth.users.get permission
            // and adds latency; revoked tokens are already rejected by token expiry.
            FirebaseToken decoded = firebaseAuth.verifyIdToken(firebaseIdToken, false);
            return new GoogleIdentity(
                    decoded.getUid(),
                    decoded.getEmail(),
                    decoded.getName(),
                    decoded.getPicture()
            );
        } catch (FirebaseAuthException e) {
            log.warn("Firebase ID token verification failed: {} — {}", e.getAuthErrorCode(), e.getMessage());
            throw new DomainException(UserErrorCode.USER_INVALID_CREDENTIALS);
        }
    }
}
