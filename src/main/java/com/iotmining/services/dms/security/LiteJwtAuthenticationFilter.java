package com.iotmining.services.dms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class LiteJwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.secret}")
    private String secretKeyBase64;

    // A shared secret used ONLY for internal microservice communication
    @Value("${internal.api.key}")
    private String internalApiKey;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKeyBase64));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // --- 1. INTERNAL SERVICE BYPASS ---
        // If the request comes from TNS or TSS and has the correct internal API key,
        // we grant it "System" level access immediately.
        final String internalHeader = request.getHeader("X-Internal-API-Key");
        if (internalApiKey.equals(internalHeader)) {
            log.debug("Internal API Key accepted for URI: {}", request.getRequestURI());

            // Create a "System" principal
            UsernamePasswordAuthenticationToken systemAuth = new UsernamePasswordAuthenticationToken(
                    "SYSTEM_SERVICE",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_SYSTEM"))
            );
            SecurityContextHolder.getContext().setAuthentication(systemAuth);
            filterChain.doFilter(request, response);
            return;
        }

        // --- 2. STANDARD JWT VALIDATION (For UI/User calls) ---
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            List<String> roles = claims.get("role", List.class);
            List<SimpleGrantedAuthority> authorities = roles == null ? List.of() :
                    roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());

            // Principal is the Claims map
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    claims,
                    null,
                    authorities
            );

            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (Exception e) {
            log.warn("DMS JWT Validation failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}