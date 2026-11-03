package com.veeteq.auth.authservice.rest.api;

import com.veeteq.auth.authservice.mapper.AuthUserMapper;
import com.veeteq.auth.authservice.rest.dto.*;
import com.veeteq.auth.authservice.service.AccessTokenService;
import com.veeteq.auth.authservice.service.AuthUserService;
import com.veeteq.auth.authservice.service.CookieService;
import com.veeteq.auth.authservice.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.Arrays;

import static java.time.ZoneOffset.UTC;

@RestController
@RequestMapping("${app.api.base-path}/auth")
public class AuthController implements AuthenticationApi {
    private final AccessTokenService accessTokenService;
    private final AuthenticationManager authManager;
    private final AuthUserService authUserService;
    private final AuthUserMapper authUserMapper;
    private final RefreshTokenService refreshTokenService;
    private final CookieService cookieService;

    public AuthController(AccessTokenService accessTokenService, AuthenticationManager authManager, AuthUserService authUserService, AuthUserMapper authUserMapper, RefreshTokenService refreshTokenService, CookieService cookieService) {
        this.accessTokenService = accessTokenService;
        this.authManager = authManager;
        this.authUserService = authUserService;
        this.authUserMapper = authUserMapper;
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
        headers.add(HttpHeaders.SET_COOKIE, cookie.toString());

        var accessToken = accessTokenService.issueToken(authentication);

        var roles = accessToken.roles().stream().map(s -> UserRoleDto.valueOf(s)).toList();
        var response = new LoginResponseDto()
                .type("Bearer")
                .token(accessToken.token())
                .expiresAt(ZonedDateTime.ofInstant(accessToken.expiresAt(), UTC))
                .roles(roles)
                .user(authUserMapper.toDto(authUser));

        return ResponseEntity.ok()
                .headers(headers)
                .body(response);
    }

    /**
     * REFRESH: read refresh cookie, validate, rotate, return new access token
     */
    @Override
    @PostMapping(path = "/refresh")
    public ResponseEntity<AuthTokenResponseDto> refreshToken(String cookie) {
        var cookieName = cookieService.getName();
        var cookieToken = extractCookie(cookie, cookieName);

        if (cookieToken == null || cookieToken.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        var authUser = refreshTokenService.validateTokenAndGetAuthuser(cookieToken);

        // rotate: revoke old, issue new
        var rotated = refreshTokenService.rotateTokenForUser(authUser);

        var responseCookie = cookieService.createCookie(rotated.getToken());
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, responseCookie.toString());

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
    @PostMapping(path = "/register")
    public ResponseEntity<Void> registerUser(UserRegistrationDto userRegistrationDto) {
        var saved = authUserService.register(userRegistrationDto.getUsername(), userRegistrationDto.getPassword(), userRegistrationDto.getEmail(), userRegistrationDto.getFirstName(), userRegistrationDto.getLastName());
        return ResponseEntity.status(HttpStatus.CREATED).build();
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
        headers.add(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.noContent()
                .headers(headers)
                .build();
    }

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
