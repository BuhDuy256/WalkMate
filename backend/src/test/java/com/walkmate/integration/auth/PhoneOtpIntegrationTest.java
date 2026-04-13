package com.walkmate.integration.auth;

import com.walkmate.application.user.SmsGateway;
import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for phone-based authentication (OTP flow).
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-08 Phone Sign-In — Send OTP</b> — T08-1 (happy path), T08-2 (invalid format)</li>
 *   <li><b>UC-09 Phone Sign-In — Verify OTP</b> — T09-1 (happy path), T09-2 (wrong code)</li>
 * </ul>
 *
 * <h3>SmsGateway mock strategy</h3>
 * <p>{@code @MockitoBean SmsGateway} is declared here rather than in
 * {@link AbstractIntegrationTest} because only phone OTP tests need to intercept
 * SMS delivery. Declaring it here keeps other test classes free of an unnecessary
 * mock and makes the dependency explicit at the class level.
 *
 * <p>Spring replaces the {@code NoOpSmsGateway} bean with this mock for the
 * context used by this test class. An {@link ArgumentCaptor} then extracts the
 * raw OTP code from the SMS message string — the only way to obtain the code
 * without modifying production code.
 *
 * <h3>T09-1 OTP extraction flow</h3>
 * <ol>
 *   <li>Call {@code POST /api/v1/auth/phone/send-otp} — the service generates a
 *       random 6-digit code, BCrypt-hashes it, saves to DB, and calls
 *       {@code smsGateway.send(phone, "Your WalkMate OTP is: XXXXXX")}.</li>
 *   <li>Use {@code ArgumentCaptor} to capture the message from the mock.</li>
 *   <li>Strip non-digits to get the raw code.</li>
 *   <li>Call {@code POST /api/v1/auth/phone/verify} with that code → 200 + token pair.</li>
 * </ol>
 */
class PhoneOtpIntegrationTest extends AbstractIntegrationTest {

    /** Replaces NoOpSmsGateway — allows ArgumentCaptor to intercept OTP messages. */
    @MockitoBean
    private SmsGateway smsGateway;

    private static final String SEND_OTP_URL  = "/api/v1/auth/phone/send-otp";
    private static final String VERIFY_OTP_URL = "/api/v1/auth/phone/verify";

    /** Valid E.164 phone number (+[country][8-14 digits]). */
    private static final String VALID_PHONE  = "+84702341568";
    /** Raw local format — missing country code prefix, fails Phone value-object validation. */
    private static final String INVALID_PHONE = "0702341568";

    private static final String DEVICE_ID = "test-device-phone";

    // ── T08-1: Send OTP — Happy Path ──────────────────────────────────────────

    @Test
    void t08_1_sendOtp_validE164Phone_returns200_andOtpRecordCreatedInDb() throws Exception {
        mockMvc.perform(post(SEND_OTP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendOtpBody(VALID_PHONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());   // data is null per UC-08

        // Verify OTP record persisted in DB for this phone
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.otp_record WHERE phone = ?",
                Integer.class,
                VALID_PHONE
        );
        assertThat(count).isEqualTo(1);
    }

    // ── T08-2: Send OTP — Invalid Phone Format ────────────────────────────────

    @Test
    void t08_2_sendOtp_invalidPhoneFormat_returns400_INVALID_USER_DATA() throws Exception {
        // "0702341568" is local Vietnamese format — not E.164 — fails Phone value-object validation
        mockMvc.perform(post(SEND_OTP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendOtpBody(INVALID_PHONE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_USER_DATA"));
    }

    // ── T09-1: Verify OTP — Happy Path ───────────────────────────────────────

    @Test
    void t09_1_verifyOtp_correctCode_returns200WithTokenPair() throws Exception {
        // Step 1 — trigger OTP creation; the service calls smsGateway.send(phone, message)
        mockMvc.perform(post(SEND_OTP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendOtpBody(VALID_PHONE)))
                .andExpect(status().isOk());

        // Step 2 — capture the SMS message to extract the raw OTP code
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(smsGateway).send(anyString(), messageCaptor.capture());
        // Message format: "Your WalkMate OTP is: 123456" — strip non-digits to get the code
        String rawCode = messageCaptor.getValue().replaceAll("[^0-9]", "");
        assertThat(rawCode).hasSize(6);

        // Step 3 — verify OTP with the extracted code
        mockMvc.perform(post(VERIFY_OTP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyOtpBody(VALID_PHONE, rawCode, DEVICE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    // ── T09-2: Verify OTP — Wrong Code ────────────────────────────────────────

    @Test
    void t09_2_verifyOtp_wrongCode_returns400_USER_OTP_INVALID() throws Exception {
        // First create a valid pending OTP in DB
        mockMvc.perform(post(SEND_OTP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendOtpBody(VALID_PHONE)))
                .andExpect(status().isOk());

        // Submit a deliberately wrong code
        mockMvc.perform(post(VERIFY_OTP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyOtpBody(VALID_PHONE, "000000", DEVICE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_OTP_INVALID"));
    }

    // ── Payload builders ───────────────────────────────────────────────────────

    private String sendOtpBody(String phone) throws Exception {
        return objectMapper.writeValueAsString(Map.of("phone", phone));
    }

    private String verifyOtpBody(String phone, String code, String deviceId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "phone",    phone,
                "code",     code,
                "deviceId", deviceId
        ));
    }
}
