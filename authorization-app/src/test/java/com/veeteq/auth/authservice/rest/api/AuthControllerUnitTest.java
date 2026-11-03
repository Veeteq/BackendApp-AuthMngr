package com.veeteq.auth.authservice.rest.api;

import com.veeteq.auth.authservice.entity.AuthUser;
import com.veeteq.auth.authservice.entity.RefreshToken;
import com.veeteq.auth.authservice.mapper.AuthUserMapper;
import com.veeteq.auth.authservice.rest.dto.LoginRequestDto;
import com.veeteq.auth.authservice.rest.dto.UserResponseDto;
import com.veeteq.auth.authservice.rest.dto.UserRoleDto;
import com.veeteq.auth.authservice.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class AuthControllerUnitTest {

    @Mock
    private AuthUserService authUserService;

    @Mock
    private AccessTokenService accessTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;
    
    @Mock
    private CookieService cookieService;
    
    @Mock
    private AuthenticationManager authManager;

    @InjectMocks
    private AuthController authController;

    @Mock
    private AuthUserMapper authUserMapper;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testAuthenticateUser_Success() {
        var loginRequest = new LoginRequestDto();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password");

        when(authUserService.findByUsername(anyString())).thenReturn(Optional.of(new AuthUser().setUsername("Witek")));

        var mockToken = new RefreshToken().setToken("dummy-refresh-token-123");
        when(refreshTokenService.issueToken(any(AuthUser.class))).thenReturn(mockToken);
        
        when(cookieService.createCookie(anyString())).thenReturn(ResponseCookie.from("dummy-cookie-name", mockToken.getToken()).build());
        
        var authentication = new TestingAuthenticationToken("testuser", "password", "ROLE_USER");
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);

        var userResponseDto = new UserResponseDto()
                .id(1L)
                .username("Witek");
        when(authUserMapper.toDto(any(AuthUser.class))).thenReturn(userResponseDto);

        //when(accessTokenService.extractRoles(authentication)).thenReturn(List.of("USER_ROLE", "ACCOUNT_ADMIN", "DOCUMENT_ADMIN", "ITEM_ADMIN"));
        var accessTokenResult = new AccessTokenResult("test-access-token", Instant.parse("2026-08-15T18:00:00Z"), List.of("USER_ROLE", "ACCOUNT_ADMIN", "DOCUMENT_ADMIN", "ITEM_ADMIN"));
        when(accessTokenService.issueToken(any(Authentication.class))).thenReturn(accessTokenResult);

        // Act
        var response = authController.loginUser(loginRequest);

        // Assert
        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        var responseBody = response.getBody();
        assert responseBody != null; // Ensure response body is not null
        assertEquals("Bearer", responseBody.getType());
        assertEquals("test-access-token", responseBody.getToken());
        //assertEquals(expiresAt.toString(), responseBody.getExpiresAt());
        assertEquals(List.of(UserRoleDto.USER_ROLE, UserRoleDto.ACCOUNT_ADMIN, UserRoleDto.DOCUMENT_ADMIN, UserRoleDto.ITEM_ADMIN), responseBody.getRoles());
    }
    
    @Test
    void testAuthenticateUser_Failure() {
        var loginRequest = new LoginRequestDto();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("wrongpassword");

        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenThrow(new RuntimeException("Authentication failed"));

        var exc = assertThrows(RuntimeException.class, () -> authController.loginUser(loginRequest));
        assertNotNull(exc);
        assertEquals("Authentication failed", exc.getMessage());
    }

}
