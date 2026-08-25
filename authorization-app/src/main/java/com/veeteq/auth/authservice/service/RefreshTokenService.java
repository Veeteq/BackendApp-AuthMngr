package com.veeteq.auth.authservice.service;

import com.veeteq.auth.authservice.entity.AuthUser;
import com.veeteq.auth.authservice.entity.RefreshToken;
import com.veeteq.auth.authservice.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository tokenRepository;
    private final SecureRandom random = new SecureRandom();
    private final int refreshDays;

    public RefreshTokenService(RefreshTokenRepository tokenRepository, @Value("${app.jwt.refresh-token-days:14}") int refreshDays) {
        this.tokenRepository = tokenRepository;
        this.refreshDays = refreshDays;
    }

    /** Create a new opaque token and persist it for the user. */
    @Transactional
    public RefreshToken issueToken(AuthUser authUser) {
        String token = generateOpaqueToken();
        var rt = new RefreshToken()
                .setId(tokenRepository.getId())
                .setToken(token)
                .setAuthUser(authUser)
                .setExpiresAt(Instant.now().plus(refreshDays, ChronoUnit.DAYS))
                .setRevoked(false);
        return tokenRepository.save(rt);
    }


    /** Validate token (exists, not revoked, not expired). */
    @Transactional//(readOnly = true)
    public RefreshToken validateToken(String token) {
        var savedToken = tokenRepository.findByToken(token).orElseThrow(() -> new IllegalArgumentException("Invalid token provided"));
        if (savedToken.isRevoked()) throw new IllegalArgumentException("Token already revoked");
        if (savedToken.isExpired()) throw new IllegalArgumentException("Token already expired");
        return savedToken;
    }

    /** Validate token (exists, not revoked, not expired). */
    @Transactional//(readOnly = true)
    public AuthUser validateTokenAndGetAuthuser(String token) {
        var savedToken = tokenRepository.findByTokenWithUserAndRoles(token).orElseThrow(() -> new IllegalArgumentException("Invalid token provided"));
        if (savedToken.isRevoked()) throw new IllegalArgumentException("Token already revoked");
        if (savedToken.isExpired()) throw new IllegalArgumentException("Token already expired");
        var authUser = savedToken.getAuthUser();
        return authUser;
    }

    /** Rotate: revoke old token and issue new one for the same user. */
    @Transactional
    public RefreshToken rotateToken(RefreshToken oldToken) {
        oldToken.setRevoked(true);
        tokenRepository.save(oldToken);
        return issueToken(oldToken.getAuthUser());
    }

    /** Rotate: revoke all old tokens for this user and issue new one for the same user. */
    @Transactional
    public RefreshToken rotateTokenForUser(AuthUser authUser) {
        tokenRepository.revokeAllTokensByUserId(authUser.getId());
        return issueToken(authUser);
    }

    /** Revoke (on logout). */
    public void revoke(String token) {
        tokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            tokenRepository.save(rt);
        });
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[64]; // 512 bits
        random.nextBytes(bytes);
        // URL-safe Base64; shorter tokens OK, but keep enough entropy
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
