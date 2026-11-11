package com.example.CMS.Controller;

import com.example.CMS.Common.ResponseUtils;
import com.example.CMS.DTO.ForgotPasswordRequest;
import com.example.CMS.DTO.LoginRequest;
import com.example.CMS.DTO.OtpResetPasswordRequest;
import com.example.CMS.DTO.ResetPasswordRequest;
import com.example.CMS.Model.CustomUserDetails;
import com.example.CMS.Model.User;
import com.example.CMS.Services.JwtService;
import com.example.CMS.Services.PasswordResetService;
import com.example.CMS.Services.UserService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetService passwordResetService;

    public AuthController(UserService userService, PasswordEncoder passwordEncoder, JwtService jwtService,
                          PasswordResetService passwordResetService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        Optional<User> user = userService.findByUsername(loginRequest.getUsername());
        if (user.isEmpty() || !passwordEncoder.matches(loginRequest.getPassword(), user.get().getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Sai tài khoản hoặc mật khẩu!");
        }

        String token = jwtService.generateToken(user.get().getUsername(), user.get().getRole().name());
        return ResponseEntity.ok(Map.of("token", token, "name", user.get().getName()));
    }

    /**
     * Changes a password. Requires a valid token: users may change their own password and admins
     * may change anyone's. (This endpoint used to be public, i.e. anyone could take over any account.)
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                           @AuthenticationPrincipal CustomUserDetails caller) {
        // The frontend sends only the new password: default to the caller's own account.
        String target = request.getUsername() == null || request.getUsername().isBlank()
                ? caller.getUsername() : request.getUsername();
        boolean isAdmin = caller.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"));
        if (!isAdmin && !caller.getUsername().equals(target)) {
            return ResponseUtils.error(HttpStatus.FORBIDDEN, "You can only change your own password");
        }
        Optional<User> user = userService.findByUsername(target);
        if (user.isEmpty()) {
            return ResponseUtils.error(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản!");
        }
        user.get().setPassword(passwordEncoder.encode(request.getPassword()));
        userService.resetPassword(user.get());
        return ResponseUtils.buildResponse(HttpStatus.OK, "Change password successfully", null);
    }

    /**
     * Public. Emails a one-time code for resetting the password. The answer is always the same,
     * whether or not the address belongs to an account, so this cannot be used to find accounts.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            passwordResetService.requestOtp(request.getEmail());
        } catch (DataIntegrityViolationException e) {
            // Two simultaneous requests for the same account raced on the unique OTP row; the other one wins.
        }
        return ResponseUtils.buildResponse(HttpStatus.OK,
                "Nếu email tồn tại trong hệ thống, mã OTP đã được gửi đến email đó.", null);
    }

    /** Public. Sets a new password for the account behind {@code email} when {@code otp} is the code sent to it. */
    @PostMapping("/forgot-password/reset")
    public ResponseEntity<?> resetPasswordWithOtp(@Valid @RequestBody OtpResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());
        return ResponseUtils.buildResponse(HttpStatus.OK, "Đặt lại mật khẩu thành công", null);
    }
}
