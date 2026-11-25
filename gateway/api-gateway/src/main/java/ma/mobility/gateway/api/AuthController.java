package ma.mobility.gateway.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import ma.mobility.gateway.auth.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    @PostMapping
    public ResponseEntity<?> authenticate(@Valid @RequestBody AuthenticationRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthenticationError("Invalid username or password"));
        }

        return ResponseEntity.ok(new AuthenticationResponse(
                tokenService.issueToken(request.username()),
                "Bearer",
                tokenService.tokenLifetimeSeconds()));
    }

    public record AuthenticationRequest(@NotBlank String username, @NotBlank String password) {}

    public record AuthenticationResponse(String accessToken, String tokenType, long expiresIn) {}

    public record AuthenticationError(String error) {}
}
