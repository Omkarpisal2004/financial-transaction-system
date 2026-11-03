package com.financialtransaction.security.userdetails;

import com.financialtransaction.user.entity.User;
import com.financialtransaction.user.entity.UserRole;
import com.financialtransaction.user.entity.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;


import java.util.Collection;
import java.util.List;

public class UserPrincipal implements UserDetails {

    @Getter
    private final Long id;
    private final String email;
    private final String passwordHash;
    private final UserStatus status;
    private final UserRole userRole;
    @Getter
    private final Integer tokenVersion;

    public UserPrincipal(Long id, String email, String passwordHash, UserStatus status , UserRole userRole, Integer tokenVersion) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
        this.userRole=userRole;
        this.tokenVersion = tokenVersion;
    }

    public static UserPrincipal fromUser(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getStatus(),
                user.getRole(),
                user.getTokenVersion()
        );
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        return List.of(new SimpleGrantedAuthority("ROLE_"+ userRole.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email; // Spring Security "username" ko generic naam deta hai, hum email use kar rahe
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Humare system mein account "expire" hone ka concept nahi hai
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.BLOCKED; // Agar BLOCKED hai, Spring Security login allow nahi karega
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Password expiry policy nahi hai abhi
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
