package com.example.CMS.Services;

import com.example.CMS.Exception.InvalidOtpException;
import com.example.CMS.Model.PasswordResetOtp;
import com.example.CMS.Model.User;
import com.example.CMS.Repository.PasswordResetOtpRepository;
import com.example.CMS.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** "Forgot password" flow: email a one-time code, then accept it together with a new password. */
@Service
public class PasswordResetService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final Duration otpTtl;
    private final Duration resendCooldown;
    private final int maxAttempts;
    /** Lets the unknown-account path spend the same BCrypt time as the wrong-code path. */
    private final String dummyHash;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetOtpRepository otpRepository,
                                PasswordEncoder passwordEncoder,
                                MailService mailService,
                                @Value("${app.password-reset.otp-ttl-minutes:10}") long otpTtlMinutes,
                                @Value("${app.password-reset.resend-cooldown-seconds:60}") long resendCooldownSeconds,
                                @Value("${app.password-reset.max-attempts:5}") int maxAttempts) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.otpTtl = Duration.ofMinutes(otpTtlMinutes);
        this.resendCooldown = Duration.ofSeconds(resendCooldownSeconds);
        this.maxAttempts = maxAttempts;
        this.dummyHash = passwordEncoder.encode("dummy");
    }

    /**
     * Emails a new code to the account with this address. Silently does nothing when there is no
     * such account, or when a code was sent less than the cooldown ago, so the caller learns nothing.
     */
    @Transactional
    public void requestOtp(String email) {
        Optional<User> found = userRepository.findByEmail(email.trim());
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        Instant now = Instant.now();

        PasswordResetOtp otp = otpRepository.findByUserId(user.getUserId()).orElseGet(PasswordResetOtp::new);
        if (otp.getSentAt() != null && now.isBefore(otp.getSentAt().plus(resendCooldown))) {
            return;
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        otp.setUserId(user.getUserId());
        otp.setOtpHash(passwordEncoder.encode(code));
        otp.setExpiresAt(now.plus(otpTtl));
        otp.setAttempts(0);
        otp.setSentAt(now);
        otpRepository.save(otp);

        mailService.sendPasswordResetOtp(user.getEmail(), user.getName(), code, otpTtl.toMinutes());
    }

    /**
     * Sets a new password if {@code code} matches the one sent to this address. A failed guess is
     * counted (hence {@code noRollbackFor}); after too many the code is discarded. A code works once.
     */
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public void resetPassword(String email, String code, String newPassword) {
        Optional<User> found = userRepository.findByEmail(email.trim());
        Optional<PasswordResetOtp> pending = found.flatMap(u -> otpRepository.findByUserId(u.getUserId()));
        if (pending.isEmpty()) {
            passwordEncoder.matches(code, dummyHash);
            throw new InvalidOtpException();
        }

        PasswordResetOtp otp = pending.get();
        if (Instant.now().isAfter(otp.getExpiresAt()) || otp.getAttempts() >= maxAttempts) {
            otpRepository.delete(otp);
            throw new InvalidOtpException();
        }
        if (!passwordEncoder.matches(code, otp.getOtpHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            if (otp.getAttempts() >= maxAttempts) {
                otpRepository.delete(otp);
            } else {
                otpRepository.save(otp);
            }
            throw new InvalidOtpException();
        }

        User user = found.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        otpRepository.delete(otp);
    }
}
