package com.financialtransaction.security.filter;

import com.financialtransaction.security.jwt.JwtUtil;
import com.financialtransaction.security.userdetails.CustomUserDetailsService;
import com.financialtransaction.security.userdetails.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            CustomUserDetailsService userDetailsService
    ) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No token: let Spring Security authorization rules decide.
        if (authHeader == null || authHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // A supplied Authorization header must use Bearer authentication.
        if (!authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response);
            return;
        }

        String token = authHeader.substring(7).trim();

        if (token.isEmpty()) {
            writeUnauthorized(response);
            return;
        }

        try {
            if (!jwtUtil.isTokenValid(token)) {
                SecurityContextHolder.clearContext();
                writeUnauthorized(response);
                return;
            }

            String email = jwtUtil.extractEmail(token);

            UserDetails userDetails =
                    userDetailsService.loadUserByUsername(email);

            if (!(userDetails instanceof UserPrincipal userPrincipal)) {
                SecurityContextHolder.clearContext();
                writeUnauthorized(response);
                return;
            }

            Integer tokenVersion = jwtUtil.extractTokenVersion(token);

            if (tokenVersion == null ||
                    !tokenVersion.equals(userPrincipal.getTokenVersion())) {
                SecurityContextHolder.clearContext();
                writeUnauthorized(response);
                return;
            }


            if (!userDetails.isEnabled()
                    || !userDetails.isAccountNonLocked()) {
                SecurityContextHolder.clearContext();
                writeUnauthorized(response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

            filterChain.doFilter(request, response);

        } catch (UsernameNotFoundException ex) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(response);
        } catch (io.jsonwebtoken.JwtException
                 | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(response);
        }
    }

    private void writeUnauthorized(HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\","
                        + "\"message\":\"Invalid or expired authentication token\"}"
        );
    }

//    @Override
//    protected boolean shouldNotFilter(HttpServletRequest request) {
//
//        String path = request.getServletPath();
//
//        return path.startsWith("/auth/")
//                || (request.getMethod().equals("POST")
//                && path.equals("/users"))
//                || path.equals("/error");
//    }
}

