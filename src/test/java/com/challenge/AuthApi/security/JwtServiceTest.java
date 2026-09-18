package com.challenge.AuthApi.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private String secret;
    private long expiration;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        secret = "MinhaChaveSuperSecretaComMaisDe32CaracteresSeguros123!";
        expiration = 3600000; // 1 hour
        ReflectionTestUtils.setField(jwtService, "secret", secret);
        ReflectionTestUtils.setField(jwtService, "expiration", expiration);
    }

    @Test
    void generateToken_shouldReturnNonNullToken() {
        String token = jwtService.generateToken("test@email.com", "USER");

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void generateToken_shouldHaveCorrectSubject() {
        String token = jwtService.generateToken("test@email.com", "USER");

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals("test@email.com", claims.getSubject());
    }

    @Test
    void generateToken_shouldContainRoleClaim() {
        String token = jwtService.generateToken("test@email.com", "ADMIN");

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals("ADMIN", claims.get("role", String.class));
    }

    @Test
    void generateToken_shouldHaveExpiration() {
        String token = jwtService.generateToken("test@email.com", "USER");

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(new Date()));
    }

    @Test
    void generateToken_shouldHaveIssuedAt() {
        String token = jwtService.generateToken("test@email.com", "USER");

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertNotNull(claims.getIssuedAt());
        assertTrue(claims.getIssuedAt().before(new Date()) || claims.getIssuedAt().equals(new Date()));
    }

    @Test
    void isValid_shouldReturnTrueForValidToken() {
        String token = jwtService.generateToken("test@email.com", "USER");

        assertTrue(jwtService.isValid(token));
    }

    @Test
    void isValid_shouldReturnFalseForTamperedToken() {
        String token = jwtService.generateToken("test@email.com", "USER");
        String tamperedToken = token.substring(0, token.length() - 5) + "xxxxx";

        assertFalse(jwtService.isValid(tamperedToken));
    }

    @Test
    void isValid_shouldReturnFalseForExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "expiration", -10000L);
        String expiredToken = jwtService.generateToken("test@email.com", "USER");

        assertFalse(jwtService.isValid(expiredToken));
    }

    @Test
    void extractEmail_shouldReturnCorrectEmail() {
        String token = jwtService.generateToken("test@email.com", "USER");

        String email = jwtService.extractEmail(token);

        assertEquals("test@email.com", email);
    }

    @Test
    void extractRole_shouldReturnCorrectRole() {
        String token = jwtService.generateToken("test@email.com", "ADMIN");

        String role = jwtService.extractRole(token);

        assertEquals("ADMIN", role);
    }

    @Test
    void isExpired_shouldReturnFalseForValidToken() {
        String token = jwtService.generateToken("test@email.com", "USER");

        assertFalse(jwtService.isExpired(token));
    }

    @Test
    void isExpired_shouldReturnTrueForExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "expiration", -10000L);
        String expiredToken = jwtService.generateToken("test@email.com", "USER");

        assertTrue(jwtService.isExpired(expiredToken));
    }
}