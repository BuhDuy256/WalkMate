package com.walkmate.infrastructure.config;

import com.walkmate.infrastructure.security.jwt.UserPrincipalConverter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserPrincipalConverter userPrincipalConverter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints are public
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/google").permitAll()
                        // Phase 2: hotspot catalogue is public for map browsing
                        .requestMatchers(HttpMethod.GET, "/api/v1/hotspots").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/hotspots/**").permitAll()
                        // Swagger / OpenAPI UI (development)
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        // Intent, proposal, and session endpoints require a valid JWT
                        .requestMatchers("/api/v1/intents/**").authenticated()
                        .requestMatchers("/api/v1/proposals/**").authenticated()
                        .requestMatchers("/api/v1/sessions/**").authenticated()
                        .requestMatchers("/api/v1/tracking/**").authenticated()
                        // Review endpoints — POST requires auth; GET /reviews is public
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*/reviews").permitAll()
                        .requestMatchers("/api/v1/sessions/*/review").authenticated()
                        // Gamification endpoints — leaderboard/badges/stats are public
                        .requestMatchers(HttpMethod.GET, "/api/v1/leaderboard").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*/badges").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*/stats").permitAll()
                        // Phase 7: public user profile view + avatar file serving
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/files/**").permitAll()
                        // Phase 7: own-profile read/write requires JWT
                        .requestMatchers("/api/v1/profile/**").authenticated()
                        // FCM token registration requires JWT (device-to-user binding)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me/fcm-token").authenticated()
                        // Phase 8: follower / following lists are public; follow & block require JWT
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*/followers").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*/following").permitAll()
                        // Phase 11: notification feed requires a valid JWT
                        .requestMatchers("/api/v1/notifications/**").authenticated()
                        // Everything else requires authentication by default
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(userPrincipalConverter))
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(@Value("${app.jwt.secret}") String jwtSecret) {
        SecretKey secretKey = buildSecretKey(jwtSecret);
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String jwtSecret) {
        SecretKey secretKey = buildSecretKey(jwtSecret);
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    private SecretKey buildSecretKey(String jwtSecret) {
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("app.jwt.secret must be at least 32 characters for HS256");
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }
}
