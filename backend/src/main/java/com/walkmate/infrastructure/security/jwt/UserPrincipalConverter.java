package com.walkmate.infrastructure.security.jwt;

import com.walkmate.application.user.UserPrincipal;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts a validated JWT into a UsernamePasswordAuthenticationToken whose
 * principal is a UserPrincipal record (userId + email).
 *
 * The "sub" claim holds the userId (UUID string), set during token generation
 * in JwtTokenProvider.
 */
@Component
public class UserPrincipalConverter implements Converter<Jwt, UsernamePasswordAuthenticationToken> {

    @Override
    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        String roleClaim = jwt.getClaimAsString("role");
        String role = roleClaim != null ? roleClaim : "USER";
        
        UserPrincipal principal = new UserPrincipal(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                role
        );
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
}
