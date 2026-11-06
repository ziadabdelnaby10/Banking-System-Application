package org.bank.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bank.common.dto.request.LoginRequest;
import org.bank.common.dto.request.MfaVerifyRequest;
import org.bank.common.dto.request.RefreshTokenRequest;
import org.bank.common.dto.response.LoginResponse;
import org.bank.common.dto.response.TokenResponse;
import org.bank.common.error.ApiErrorDetail;
import org.bank.identity.service.AuthService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints (see bank.security.public-paths). Responses carry credentials, so they are never cached. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Public endpoints: login, MFA, token refresh and logout. No bearer token needed.")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Log in with username and password",
            description = "Returns tokens (status AUTHENTICATED) or, for MFA users, a challenge (status MFA_REQUIRED) "
                    + "to complete with POST /api/v1/auth/mfa/verify. In the dev profile the MFA code is printed in the Identity log.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "teller (no MFA)", value = "{\"username\": \"teller1\", \"password\": \"Dev@Passw0rd!2026\"}")))
    @ApiResponse(responseCode = "401", description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ApiErrorDetail.class)))
    @ApiResponse(responseCode = "423", description = "Account locked after too many failed attempts",
            headers = @Header(name = "Retry-After", description = "Seconds until the account unlocks"),
            content = @Content(schema = @Schema(implementation = ApiErrorDetail.class)))
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return noStore(authService.login(request, ClientContexts.from(http)));
    }

    @PostMapping("/mfa/verify")
    @Operation(summary = "Complete an MFA login",
            description = "Exchanges the challengeToken from the login response and the one-time code for tokens.")
    @ApiResponse(responseCode = "401", description = "Wrong or expired code or challenge",
            content = @Content(schema = @Schema(implementation = ApiErrorDetail.class)))
    public ResponseEntity<TokenResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest request, HttpServletRequest http) {
        return noStore(authService.verifyMfa(request, ClientContexts.from(http)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh token",
            description = "Returns a new access token and a new refresh token. Re-using an old refresh token revokes the session.")
    @ApiResponse(responseCode = "401", description = "Refresh token unknown, expired or already used",
            content = @Content(schema = @Schema(implementation = ApiErrorDetail.class)))
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        return noStore(authService.refresh(request, ClientContexts.from(http)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out", description = "Revokes the session that owns the given refresh token.")
    @ApiResponse(responseCode = "204", description = "Session revoked")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
