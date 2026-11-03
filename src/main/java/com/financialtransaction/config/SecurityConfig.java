package com.financialtransaction.config;

import com.financialtransaction.security.filter.JwtAuthenticationFilter;
import com.financialtransaction.security.filter.RateLimitFilter;
import com.financialtransaction.security.userdetails.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.boot.web.servlet.FilterRegistrationBean;



@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, RateLimitFilter rateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .exceptionHandling(exception -> exception

                        // User authenticated nahi hai
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        "Unauthorized"
                                )
                        )

                        // User authenticated hai, but permission nahi hai
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(
                                        HttpServletResponse.SC_FORBIDDEN,
                                        "Forbidden"
                                )
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()

                        // User registration
                        .requestMatchers(HttpMethod.POST, "/users").permitAll()

                        // Swagger / OpenAPI endpoints
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()

                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        rateLimitFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(CustomUserDetailsService userDetailsService,
                                                               PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider daoProvider) {
        return new ProviderManager(daoProvider);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration() {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }
}