package com.veeteq.auth.authservice.rest.api;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import com.veeteq.auth.authservice.service.AccessTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import com.veeteq.auth.authservice.rest.dto.AuthTokenResponseDto;
import com.veeteq.auth.authservice.rest.dto.LoginRequestDto;
import com.veeteq.auth.authservice.rest.dto.LoginResponseDto;
import com.veeteq.auth.authservice.rest.dto.UserRegistrationDto;
import com.veeteq.auth.authservice.service.AuthUserService;
import com.veeteq.auth.authservice.service.CookieService;
import com.veeteq.auth.authservice.service.RefreshTokenService;

import static java.time.ZoneOffset.UTC;

@RestController
@RequestMapping("${app.api.base-path}/auth")
public class AuthController implements AuthenticationApi {
    private final AccessTokenService accessTokenService;
    private final AuthenticationManager authManager;
    private final AuthUserService authUserService;
    private final RefreshTokenService refreshTokenService;
    private final CookieService cookieService;

    public AuthController(AccessTokenService accessTokenService, AuthenticationManager authManager, AuthUserService authUserService, RefreshTokenService refreshTokenService, CookieService cookieService) {
        this.accessTokenService = accessTokenService;
        this.authManager = authManager;
        this.authUserService = authUserService;
        this.refreshTokenService = refreshTokenService;
        this.cookieService = cookieService;
    }

    // Token lifetime configurable via properties (default 3600s)
    @Value("${app.jwt.access-token-seconds:3600}")
    private long accessTokenSeconds;

    /**
     * LOGIN: issue access token + set refresh cookie
     */
    @Override
    @PostMapping(path = "/login")
    public ResponseEntity<LoginResponseDto> loginUser(LoginRequestDto loginRequest) {
        var authToken = new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword());
        var authentication = authManager.authenticate(authToken);

        var authUser = authUserService.findByUsername(authentication.getName()).orElseThrow();
        var refreshToken = refreshTokenService.issueToken(authUser);
        var cookie = cookieService.createCookie(refreshToken.getToken());
        var headers = new HttpHeaders();
        headers.add("Set-Cookie", cookie.toString());

        var accessToken = accessTokenService.issueToken(authentication);

        var response = new LoginResponseDto()
                .type("Bearer")
                .token(accessToken.token())
                .expiresAt(ZonedDateTime.ofInstant(accessToken.expiresAt(), UTC))
                .roles(accessToken.roles())
                .user(null);

        return ResponseEntity.ok()
                .headers(headers)
                .body(response);
    }

    /**
     * REFRESH: read refresh cookie, validate, rotate, return new access token
     */
    @Override
    @PostMapping(path = "/refresh")
    public ResponseEntity<AuthTokenResponseDto> refreshToken(String setCookie) {
        var cookieName = cookieService.getName();
        var cookieToken = extractCookie(setCookie, cookieName);

        if (cookieToken == null || cookieToken.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        var authUser = refreshTokenService.validateTokenAndGetAuthuser(cookieToken);

        // rotate: revoke old, issue new
        var rotated = refreshTokenService.rotateTokenForUser(authUser);

        var cookie = cookieService.createCookie(rotated.getToken());
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie.toString());

        var accessToken = accessTokenService.issueToken(authUser);

        var response = new AuthTokenResponseDto()
                .type("Bearer")
                .token(accessToken.token())
                .expiresAt(ZonedDateTime.ofInstant(accessToken.expiresAt(), UTC));

        return ResponseEntity.ok()
                .headers(headers)
                .body(response);
    }

    @Override
    public ResponseEntity<Void> registerUser(UserRegistrationDto userRegistrationDto) {
        return null;
    }

    /**
     * LOGOUT: revoke refresh token and clear cookie
     */
    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logoutUser(String setCookie) {
        String cookieName = cookieService.getName();
        String cookieToken = extractCookie(setCookie, cookieName);
        if (cookieToken != null && !cookieToken.isBlank()) {
            refreshTokenService.revoke(cookieToken);
        }

        ResponseCookie cookie = cookieService.clearCookie();
        var headers = new HttpHeaders();
        headers.add("Set-Cookie", cookie.toString());

        return ResponseEntity.noContent()
                .headers(headers)
                .build();
    }

    /*
        @GetMapping("/validate")
        public String validate(@RequestParam String token) {
            jwtDecoder.decode(token);
            return "VALID";
        }
    */
    private String extractCookie(String cookieHeader, String cookieName) {
        if (cookieHeader == null) return null;
        var cookieSearch = cookieName + "=";
        var result = Arrays.stream(cookieHeader.split(";"))
                .map(String::trim)
                .filter(s -> s.startsWith(cookieSearch))
                .map(s -> s.substring(cookieSearch.length()))
                .findFirst()
                .orElse(null);
        return result;
    }

}
