package com.walkmate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable()) // Disable CORS for development
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").permitAll() // Cho phép truy cập API không cần login
                .anyRequest().permitAll()
            );
        return http.build();
    }
}