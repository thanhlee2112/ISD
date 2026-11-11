package com.example.CMS.Services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends the application's emails. Sending runs on a background thread so the request returns at
 * the same speed whether or not an account exists (otherwise response time would reveal it), and
 * so a slow or failing SMTP server cannot hang the API. Failures are logged, never thrown.
 */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String smtpHost;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> mailSender,
                       @Value("${spring.mail.host:}") String smtpHost,
                       @Value("${app.mail.from:}") String configuredFrom,
                       @Value("${spring.mail.username:}") String smtpUsername) {
        this.mailSender = mailSender;
        this.smtpHost = smtpHost;
        this.from = configuredFrom.isBlank() ? smtpUsername : configuredFrom;
    }

    @Async
    public void sendPasswordResetOtp(String to, String name, String otp, long validMinutes) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || smtpHost.isBlank()) {
            log.warn("Mail is not configured (set MAIL_HOST etc.): password reset code was NOT sent");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (!from.isBlank()) {
                message.setFrom(from);
            }
            message.setTo(to);
            message.setSubject("Mã OTP đặt lại mật khẩu");
            message.setText("Xin chào " + name + ",\n\n"
                    + "Mã OTP để đặt lại mật khẩu của bạn là: " + otp + "\n\n"
                    + "Mã có hiệu lực trong " + validMinutes + " phút. Không chia sẻ mã này với bất kỳ ai.\n"
                    + "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.");
            sender.send(message);
        } catch (Exception e) {
            log.error("Could not send password reset email", e);
        }
    }
}
