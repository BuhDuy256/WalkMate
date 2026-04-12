package com.walkmate.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link AuthTokenFactory} against the real HTTP + DB stack.
 *
 * These are the P0-3 acceptance tests. Each test covers one factory method.
 */
class AuthTokenFactoryTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createAndLoginUser_returnsValidBearerToken() throws Exception {
        String token = authFactory.createAndLoginUser("alice@example.com", "Password1!");

        assertThat(token).startsWith("Bearer ");
        assertThat(token.length()).isGreaterThan(20);

        // UC-01 side-effect: user_account row must exist
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.user_account WHERE email = 'alice@example.com'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void login_afterManualRegister_returnsToken() throws Exception {
        // Register via factory first step, then call login() separately
        authFactory.createAndLoginUser("bob@example.com", "Password1!");
        String token = authFactory.login("bob@example.com", "Password1!");

        assertThat(token).startsWith("Bearer ");
    }

    @Test
    void createAndLoginUserWithProfile_seedsProfileData() throws Exception {
        String token = authFactory.createAndLoginUserWithProfile(
                "carol@example.com",
                "Password1!",
                "Carol Smith",
                "I love morning walks",
                List.of("hiking", "nature"));

        assertThat(token).startsWith("Bearer ");

        // user_profile row must exist with the correct full_name
        String name = jdbcTemplate.queryForObject(
                """
                SELECT up.full_name
                FROM public.user_profile up
                JOIN public.user_account ua ON ua.user_id = up.user_id
                WHERE ua.email = 'carol@example.com'
                """, String.class);
        assertThat(name).isEqualTo("Carol Smith");

        // Tags must be seeded
        Integer tagCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.profile_tag pt
                JOIN public.user_account ua ON ua.user_id = pt.user_id
                WHERE ua.email = 'carol@example.com'
                """, Integer.class);
        assertThat(tagCount).isEqualTo(2);
    }

    @Test
    void twoUsers_inSameTest_haveIndependentAccounts() throws Exception {
        String tokenA = authFactory.createAndLoginUser("userA@example.com", "Password1!");
        String tokenB = authFactory.createAndLoginUser("userB@example.com", "Password1!");

        assertThat(tokenA).isNotEqualTo(tokenB);

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.user_account", Integer.class);
        assertThat(total).isEqualTo(2);
    }
}
