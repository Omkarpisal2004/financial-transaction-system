package com.financialtransaction.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationMs;
    private final String issuer;


    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMs,
            @Value("${jwt.issuer}") String issuer
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT secret must not be empty"
            );
        }

        if (expirationMs <= 0) {
            throw new IllegalArgumentException(
                    "JWT expiration must be greater than zero"
            );
        }

        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT issuer must not be empty"
            );
        }

        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        this.expirationMs = expirationMs;
        this.issuer = issuer;
    }



    public String generateToken(Long userId, String email ,Integer tokenVersion) {

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("tokenVersion", tokenVersion)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();

    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        return extractClaim(
                token,
                claims -> claims.get("userId", Long.class)
        );
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);

            return !isTokenExpired(claims)
                    && issuer.equals(claims.getIssuer());

        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration == null || expiration.before(new Date());
    }

    private <T> T extractClaim(
            String token,
            Function<Claims, T> claimsResolver
    ) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Integer extractTokenVersion(String token) {
        return extractClaim(
                token,
                claims -> claims.get("tokenVersion", Integer.class)
        );
    }
}