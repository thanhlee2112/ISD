package com.example.CMS.Controller.Admin;

import com.example.CMS.Common.ResponseUtils;
import com.example.CMS.DTO.EditAccountRequest;
import com.example.CMS.Model.User;
import com.example.CMS.Services.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Map;

@RestController
@RequestMapping("/admin/user")
public class UserController {
    private static final String PASSWORD_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/list")
    public ResponseEntity<?> index() {
        return ResponseUtils.success(userService.index());
    }

    @PostMapping("/detail/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        return ResponseUtils.success(userService.detail(id));
    }

    @PutMapping("/edit-account")
    public ResponseEntity<?> editAccount(@Valid @RequestBody EditAccountRequest request) {
        userService.update(request);
        return ResponseUtils.success("Update account successfully!");
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseUtils.success("Delete successfully!");
    }

    @PostMapping("/store")
    public ResponseEntity<?> store(@Valid @RequestBody User user) {
        String rawPassword = generateRandomPassword(12);
        user.setPassword(passwordEncoder.encode(rawPassword));
        userService.store(user);

        // The generated password is only ever shown here, once.
        return ResponseUtils.success(Map.of(
                "message", "Create user successfully!",
                "generatedPassword", rawPassword));
    }

    private String generateRandomPassword(int length) {
        StringBuilder password = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            password.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }
}
