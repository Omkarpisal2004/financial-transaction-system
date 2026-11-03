package com.financialtransaction.auth.service;

import com.financialtransaction.auth.dto.LoginRequest;
import com.financialtransaction.auth.dto.LoginResponse;
import com.financialtransaction.security.jwt.JwtUtil;
import com.financialtransaction.security.userdetails.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtUtil jwtUtil
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(LoginRequest request) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.email(),
                                request.password()
                        )
                );

        UserPrincipal userPrincipal =
                (UserPrincipal) authentication.getPrincipal();

        String token = jwtUtil.generateToken(
                userPrincipal.getId(),
                userPrincipal.getUsername(),
                userPrincipal.getTokenVersion()
        );

        return new LoginResponse(
                token,
                userPrincipal.getId(),
                userPrincipal.getUsername()
        );
    }
}

