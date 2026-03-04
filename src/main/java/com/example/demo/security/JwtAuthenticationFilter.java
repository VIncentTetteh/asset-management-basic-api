package com.example.demo.security;

import com.example.demo.models.User;
import com.example.demo.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtil.parseToken(token);
                String username = claims.getSubject();
                // JWT is written with key 'role' (singular), e.g. "ROLE_ADMIN"
                Object roleClaim = claims.get("role");
                List<SimpleGrantedAuthority> authorities = List.of();
                if (roleClaim != null) {
                    String roleStr = roleClaim.toString().trim();
                    if (!roleStr.isEmpty()) {
                        // Support both "ADMIN" (legacy) and "ROLE_ADMIN" (prefixed)
                        String authority = roleStr.startsWith("ROLE_") ? roleStr : "ROLE_" + roleStr;
                        authorities = List.of(new SimpleGrantedAuthority(authority));
                    }
                }

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(username, null,
                        authorities);
                // attach claims as details
                auth.setDetails(claims);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ex) {
                // ignore invalid token and continue as unauthenticated
            }
        }
        filterChain.doFilter(request, response);
    }
}
