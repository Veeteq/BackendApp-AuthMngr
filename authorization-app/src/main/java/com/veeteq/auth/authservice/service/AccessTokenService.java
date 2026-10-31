package com.veeteq.auth.authservice.service;

import com.veeteq.auth.authservice.config.SecurityProperties;
import com.veeteq.auth.authservice.entity.AuthUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AccessTokenService {
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties securityProperties;

    public AccessTokenService(JwtEncoder jwtEncoder, SecurityProperties securityProperties) {
        this.jwtEncoder = jwtEncoder;
        this.securityProperties = securityProperties;
    }

    public AccessTokenResult issueToken(Authentication authentication) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(securityProperties.accessTokenSeconds());

        var roles = extractRoles(authentication);
        var claims = JwtClaimsSet.builder()
                .issuer(securityProperties.issuer())
                .subject(authentication.getName())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .build();

        var token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new AccessTokenResult(token, expiresAt, roles);
    }

    public AccessTokenResult issueToken(AuthUser authUser) {
        var authorities = authUser.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .toList();

        var authentication = new UsernamePasswordAuthenticationToken(
                authUser.getUsername(),
                null,
                authorities
        );

        return issueToken(authentication);
    }

    private List<String> extractRoles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

}
