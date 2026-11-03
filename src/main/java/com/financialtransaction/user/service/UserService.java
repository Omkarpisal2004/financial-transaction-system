package com.financialtransaction.user.service;

import com.financialtransaction.user.dto.UserCreateRequest;
import com.financialtransaction.user.dto.UserResponse;
import com.financialtransaction.user.dto.UserStatusUpdateRequest;
import com.financialtransaction.user.entity.User;
import com.financialtransaction.user.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.financialtransaction.security.userdetails.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;


@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse createUser(UserCreateRequest request) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);
        return UserResponse.from(saved);

    }


    public UserResponse getUserById(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found: " + id
                        )
                );

        return UserResponse.from(user);
    }

    public UserResponse updateUserStatus(
            Long userId,
            UserStatusUpdateRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found: " + userId)
                );

        user.setStatus(request.status());

        User updatedUser = userRepository.save(user);

        return UserResponse.from(updatedUser);
    }
}