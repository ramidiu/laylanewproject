package com.remitz.config;

import com.remitz.security.JwtAuthenticationFilter;
import com.remitz.security.RemitzPermissionEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RemitzPermissionEvaluator permissionEvaluator;

    @Value("${app.frontend-url:https://layla.money}")
    private String frontendUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            frontendUrl,
            "https://global.remitz.co.uk",
            "http://localhost:8100",
            "http://localhost:4200",
            "http://localhost",
            "https://localhost",       // Capacitor Android (androidScheme=https)
            "capacitor://localhost",   // Capacitor iOS
            "ionic://localhost"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints
                        .requestMatchers("/api/auth/**").permitAll()
                        // PayIn endpoints
                        .requestMatchers("/api/payin/**").authenticated()
                        // Internal service-to-service endpoints
                        .requestMatchers("/internal/**").permitAll()
                        // FX public endpoints
                        .requestMatchers("/api/fx/rates").permitAll()
                        .requestMatchers("/api/fx/rates/**").permitAll()
                        .requestMatchers("/api/fx/quote").permitAll()
                        .requestMatchers("/api/fx/corridors/auto-create").permitAll()
                        .requestMatchers("/api/corridors").permitAll()
                        .requestMatchers("/api/corridors/*/delivery-methods").permitAll()
                        .requestMatchers("/api/corridors/*/limits").permitAll()
                        // Transaction public config endpoints
                        .requestMatchers("/api/transactions/config/active-countries").permitAll()
                        .requestMatchers("/api/transactions/config/active-receive-countries").permitAll()
                        .requestMatchers("/api/transactions/config/payment-methods").permitAll()
                        .requestMatchers("/api/transactions/config/payout-types").permitAll()
                        .requestMatchers("/api/transactions/banks/config/**").permitAll()
                        .requestMatchers("/api/transactions/banks/list/**").permitAll()
                        .requestMatchers("/api/transactions/banks/search/**").permitAll()
                        .requestMatchers("/api/transactions/lookup/mobile-services").permitAll()
                        .requestMatchers("/api/transactions/lookup/cash-points").permitAll()
                        // User public endpoints
                        .requestMatchers("/api/users/*").permitAll()
                        .requestMatchers("/api/users/kyc/**").permitAll()
                        .requestMatchers("/api/users/*/kyc/documents/*/file").permitAll()
                        .requestMatchers("/api/referrals/validate/**").permitAll()
                        .requestMatchers("/api/referrals/on-completed").permitAll()
                        .requestMatchers("/api/wallet/credit").permitAll()
                        .requestMatchers("/api/wallet/debit").permitAll()
                        // Notification public endpoints
                        .requestMatchers("/api/support/attachments/*/file").permitAll()
                        // Swagger / actuator
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/api-docs/**").permitAll()
                        .requestMatchers("/swagger-config").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Code added by Naresh: Phase 2 DEV/POC Kuber smoke-test endpoint (remove before production).
                        .requestMatchers("/api/dev/kuber/**").permitAll()
                        // Volume Pay webhook — called by Volume servers, not authenticated users
                        .requestMatchers("/api/webhooks/volume").permitAll()
                        // Trust Payments callback — called by frontend after card payment redirect
                        .requestMatchers("/api/trust-payment/**").permitAll()
                        // Smarty address lookup — public, used during registration
                        .requestMatchers("/api/address/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
}
