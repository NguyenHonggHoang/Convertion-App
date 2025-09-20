package com.example.converter.config;

import com.example.converter.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import com.example.converter.service.user.CustomUserDetailsService;
import com.example.converter.security.JwtAuthenticationEntryPoint;

/**
 * Spring Security configuration for the application.
 * Sets up HttpSecurity to protect endpoints, allows public registration/login endpoints.
 * Configures AuthenticationManagerBuilder and PasswordEncoder.
 * Adds JwtAuthenticationFilter to the filter chain.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtAuthenticationEntryPoint unauthorizedHandler;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                // Public endpoints (relative to server.servlet.context-path=/api)
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/news", "/news/**", "/api/news", "/api/news/**").permitAll()
                // Swagger UI endpoints 
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/api-docs/**").permitAll() 
                .requestMatchers("/swagger-resources/**", "/swagger-config/**").permitAll() 
                .requestMatchers("/webjars/**", "/favicon.ico").permitAll()
                .requestMatchers("/actuator/health", "/api/actuator/health").permitAll()
                // JWKS endpoint for public key distribution
                .requestMatchers("/.well-known/jwks.json").permitAll()
                // Internal service endpoints 
                .requestMatchers("/internal/**", "/api/internal/**").permitAll()
                // Exchange history endpoint for internal services
                .requestMatchers("/api/exchange/history").permitAll()
                // Conversion endpoints - allow both authenticated and anonymous access
                // JWT filter will still process tokens if present
                .requestMatchers("/convert/unit", "/api/convert/unit", "/convert/unit/**", "/api/convert/unit/**").permitAll()
                .requestMatchers("/convert/currency", "/api/convert/currency").permitAll()
                // Protected endpoints
                .requestMatchers("/users/**").authenticated()
                .requestMatchers("/alerts/**").authenticated()
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authenticationProvider(authenticationProvider())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
