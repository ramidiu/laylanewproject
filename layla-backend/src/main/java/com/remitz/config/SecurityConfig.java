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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
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
        // Auth is Bearer-token only (no cookies), so credentialed CORS is unnecessary. Keeping
        // this false also avoids the unsafe wildcard-headers-with-credentials combination.
        config.setAllowCredentials(false);
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
                        // Identity-sensitive auth endpoints resolve the caller from the X-User-UUID
                        // header, which is only authoritative (JWT-derived) when a valid token is
                        // present. Require authentication so an unauthenticated request cannot spoof it.
                        .requestMatchers(
                                "/api/auth/change-password",
                                "/api/auth/mfa/setup",
                                "/api/auth/mfa/enable",
                                "/api/auth/mfa/disable").authenticated()
                        // Auth endpoints
                        .requestMatchers("/api/auth/**").permitAll()
                        // PayIn endpoints
                        .requestMatchers("/api/payin/**").authenticated()
                        // Internal service-to-service endpoints — MUST NOT be public.
                        // SECURITY: previously permitAll, which exposed KYC status lookup and
                        // compliance (CTR/alerts/screen) creation to the internet. In this
                        // deployment compliance/KYC run in-process (no external caller), so these
                        // require authentication. A future internal service must send a valid token.
                        .requestMatchers("/internal/**").authenticated()
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
                        .requestMatchers("/api/transactions/config/payin-methods").permitAll()
                        .requestMatchers("/api/transactions/config/payout-types").permitAll()
                        .requestMatchers("/api/transactions/banks/config/**").permitAll()
                        .requestMatchers("/api/transactions/banks/list/**").permitAll()
                        .requestMatchers("/api/transactions/banks/search/**").permitAll()
                        .requestMatchers("/api/transactions/lookup/mobile-services").permitAll()
                        .requestMatchers("/api/transactions/lookup/cash-points").permitAll()
                        // User public endpoints
                        // SECURITY (SEC-03): /api/users/* removed from permitAll — GET/PUT /api/users/{uuid}
                        // now require auth + owner-or-staff (@PreAuthorize in UserController). The KYC
                        // reference subtree stays public.
                        // SECURITY: the document-file endpoint is NO LONGER public — it served identity
                        // documents (passports/IDs) to unauthenticated callers (IDOR). It now requires
                        // auth + owner-or-staff via @PreAuthorize on KycController.getDocumentFile.
                        .requestMatchers("/api/users/kyc/**").permitAll()
                        .requestMatchers("/api/referrals/validate/**").permitAll()
                        .requestMatchers("/api/referrals/on-completed").permitAll()
                        // SECURITY (SEC-01): /api/wallet/credit and /api/wallet/debit removed from
                        // permitAll — they are now authenticated + @PreAuthorize('settlement:manage')
                        // in WalletController. Internal callers use WalletService directly.
                        // Notification public endpoints
                        .requestMatchers("/api/support/attachments/*/file").permitAll()
                        // Swagger / actuator
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/api-docs/**").permitAll()
                        .requestMatchers("/swagger-config").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // SECURITY: DEV/POC Kuber smoke-test endpoint is no longer public
                        // (it was flagged "remove before production"). Now requires authentication.
                        .requestMatchers("/api/dev/kuber/**").authenticated()
                        // Volume Pay webhook — called by Volume servers, not authenticated users
                        .requestMatchers("/api/webhooks/volume").permitAll()
                        // SECURITY (M-B): /jwt mints a server-signed payment token and is an
                        // enumeration oracle for transaction references — it must be authenticated
                        // (the Send Money page calls it with a bearer token). The server-to-server
                        // callback/confirm paths remain public below.
                        .requestMatchers("/api/trust-payment/jwt").authenticated()
                        // Trust Payments callback — called by frontend after card payment redirect
                        .requestMatchers("/api/trust-payment/**").permitAll()
                        // Smarty address lookup — public, used during registration
                        .requestMatchers("/api/address/**").permitAll()
                        .anyRequest().authenticated()
                )
                // Security response headers (API responses). The SPA's static assets get the
                // full header set (incl. CSP) from nginx; these harden direct API responses.
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
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
