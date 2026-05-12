package com.coachsim.auth;

import com.coachsim.user.User;
import com.coachsim.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(min = 2, max = 120) String displayName
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record AuthResponse(String token, long expiresInMs, UserInfo user) {}

    public record UserInfo(Long id, String email, String displayName, String role) {
        static UserInfo from(User u) { return new UserInfo(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole()); }
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw new ResponseStatusException(CONFLICT, "Email already registered");
        }
        User saved = users.save(User.builder()
                .email(req.email())
                .passwordHash(encoder.encode(req.password()))
                .displayName(req.displayName())
                .role("ROLE_USER")
                .build());
        return ResponseEntity.status(CREATED).body(tokenFor(saved));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        User u = users.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid credentials"));
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials");
        }
        return ResponseEntity.ok(tokenFor(u));
    }

    private AuthResponse tokenFor(User u) {
        String token = jwt.issue(u.getId(), u.getEmail(), u.getRole());
        return new AuthResponse(token, jwt.expirationMs(), UserInfo.from(u));
    }
}
