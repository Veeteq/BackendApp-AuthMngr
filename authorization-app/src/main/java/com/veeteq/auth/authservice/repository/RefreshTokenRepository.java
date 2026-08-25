package com.veeteq.auth.authservice.repository;

import com.veeteq.auth.authservice.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    @Query(value = "SELECT authuser_seq.nextval from dual", nativeQuery = true)
    Long getId();

    // Revoke all active tokens for a user (used to enforce a single active token)
    @Modifying
    @Query("""
            UPDATE RefreshToken rt
               SET rt.revoked = true
             WHERE rt.authUser.id = :userId
               AND rt.revoked = false
               AND rt.expiresAt > CURRENT_TIMESTAMP
            """)
    void revokeAllTokensByUserId(Long userId);

    @Query("""
            SELECT rt
              FROM RefreshToken rt
              JOIN FETCH rt.authUser au
              JOIN FETCH au.roles
             WHERE rt.token = :token
            """)
    Optional<RefreshToken> findByTokenWithUserAndRoles(String token);
}
