package com.walkmate.infrastructure.email;

import com.walkmate.application.user.EmailProvider;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmtpEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailProvider(JavaMailSender mailSender,
                              @Value("${spring.mail.username}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendOtp(String toEmail, String otpCode) {
        log.info("Sending OTP email to {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("WalkMate — Your password reset code");
            helper.setText(buildHtml(otpCode), true);
            mailSender.send(message);
            log.info("OTP email sent successfully to {}", toEmail);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send OTP email to {}", toEmail, e);
        }
    }

    private String buildHtml(String otpCode) {
        return """
                <div style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px">
                  <h2 style="color:#FF6B2C">WalkMate</h2>
                  <p>You requested a password reset. Use the code below — it expires in <strong>5 minutes</strong>.</p>
                  <div style="font-size:36px;font-weight:bold;letter-spacing:12px;
                              text-align:center;padding:24px;background:#f5f5f5;
                              border-radius:8px;margin:24px 0">%s</div>
                  <p style="color:#888;font-size:13px">
                    If you did not request a password reset, ignore this email.<br>
                    Do not share this code with anyone.
                  </p>
                </div>
                """.formatted(otpCode);
    }
}
