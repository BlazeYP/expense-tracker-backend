package com.example.finance.auth;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.example.finance.auth.model.AppUser;
import com.example.finance.auth.repository.AppUserRepository;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AppUserRepository users;
    private final JwtEncoder encoder;
    private final GoogleIdTokenVerifier verifier;

    public AuthController(AppUserRepository users, JwtEncoder encoder,
            @Value("${app.auth.google-client-id}") String clientId) throws GeneralSecurityException, IOException {
        this.users = users;
        this.encoder = encoder;
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId)).build();
    }

    @PostMapping("/google")
    public SignInResponse google(@RequestBody SignInRequest request) throws IOException, GeneralSecurityException {
        if (request == null || request.credential() == null || request.credential().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google credential is required");
        }
        GoogleIdToken token = verifier.verify(request.credential());
        if (token == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google credential is invalid or expired");
        GoogleIdToken.Payload payload = token.getPayload();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email is not verified");
        String subject = payload.getSubject();
        AppUser user = users.findByGoogleSubject(subject).orElseGet(AppUser::new);
        user.googleSubject = subject;
        user.email = payload.getEmail().toLowerCase(Locale.ROOT);
        user.name = (String) payload.get("name");
        user.picture = (String) payload.get("picture");
        user.updatedAt = Instant.now();
        user = users.save(user);
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("expense-tracker").issuedAt(now)
                .expiresAt(now.plusSeconds(12 * 60 * 60)).subject(user.id).claim("email", user.email)
                .claim("name", user.name).claim("picture", user.picture).build();
        String signed = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new SignInResponse(signed, profile(user));
    }

    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal Jwt jwt) {
        return new UserProfile(jwt.getSubject(), jwt.getClaimAsString("name"),
                jwt.getClaimAsString("email"), jwt.getClaimAsString("picture"));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() { }

    private static UserProfile profile(AppUser user) {
        return new UserProfile(user.id, user.name, user.email, user.picture);
    }
    public record SignInRequest(String credential) { }
    public record SignInResponse(String token, UserProfile user) { }
    public record UserProfile(String id, String name, String email, String picture) { }
}
