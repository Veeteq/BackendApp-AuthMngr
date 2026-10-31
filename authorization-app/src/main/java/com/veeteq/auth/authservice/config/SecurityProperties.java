package com.veeteq.auth.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String issuer,
        Duration accessTokenSeconds,
        Duration refreshTokenDays) {}