package com.example.converter.security;

import com.example.converter.security.jwt.JwtIssuerService;
import com.example.converter.security.jwt.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import com.example.converter.service.user.CustomUserDetailsService;
import com.example.converter.domain.service.AccessTokenService;

/**
 * JWT Authentication Filter to filter requests and authenticate JWT.
 * Uses JwtIssuerService to validate token and set authentication information for Spring Security Context.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtIssuerService jwtIssuerService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private AccessTokenService accessTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            logger.info("=== JWT Filter Processing: " + request.getMethod() + " " + request.getRequestURI());
            String jwt = getJwtFromRequest(request);
            logger.info("JWT Token present: " + (jwt != null ? "YES" : "NO"));
            
            if (jwt != null) {
                logger.info("JWT Token length: " + jwt.length());
                logger.info("JWT Token format (first 50 chars): " + jwt.substring(0, Math.min(jwt.length(), 50)));
                int dotCount = jwt.length() - jwt.replace(".", "").length();
                logger.info("JWT Token dot count: " + dotCount + " (should be 2)");
            }

            if (StringUtils.hasText(jwt) && jwtIssuerService.validateToken(jwt)) {
                Map<String, Object> claims = jwtIssuerService.getClaimsFromToken(jwt);
                String tokenType = (String) claims.get("token_type");

                if (!"access".equals(tokenType)) {
                    logger.info("JWT token_type is not 'access' (" + tokenType + "), skipping authentication");
                } else {
                    String jti = (String) claims.get("jti");

                    if (jti != null && tokenBlacklistService.isBlacklisted(jti)) {
                        logger.info("JWT token  is blacklisted, rejecting authentication");
                    } else {
                        String sub = (String) claims.get("sub");
                        Number iatNum = (Number) claims.get("iat");

                        if (sub != null) {
                            if (iatNum != null && accessTokenService.revokedByEpoch(sub, iatNum.longValue())) {
                                logger.info("ACCESS token is revoked by epoch, skipping authentication");
                            } else {
                                Long userId = Long.parseLong(sub);
                                logger.info("Valid ACCESS JWT found, user ID: " + userId);

                                UserDetails userDetails = userDetailsService.loadUserById(userId);
                                logger.info("Loaded user details: " + userDetails.getUsername());

                                UsernamePasswordAuthenticationToken authentication =
                                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                                SecurityContextHolder.getContext().setAuthentication(authentication);

                                logger.info("Authentication set in context for user: " + userDetails.getUsername());
                            }
                        } else {
                            logger.info("JWT valid but no subject (user ID) found");
                        }
                    }
                }
            } else {
                if (jwt != null) {
                    logger.info("JWT present but validation failed");
                } else {
                    logger.info("No JWT token found, proceeding as anonymous");
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        logger.info("JWT Filter completed, proceeding to next filter");
        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        logger.info("Authorization Header: " + (bearerToken != null ? bearerToken.substring(0, Math.min(bearerToken.length(), 100)) + "..." : "null"));

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String extractedToken = bearerToken.substring(7);
            logger.info("Extracted JWT length: " + extractedToken.length());
            return extractedToken;
        }
        logger.info("No Bearer token found in Authorization header");
        return null;
    }
}
