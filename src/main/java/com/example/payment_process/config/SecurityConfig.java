package com.example.payment_process.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public UserDetailsService userDetailsService(){
        UserDetails admin = User.withUsername("admin")
                .password("{noop}admin")
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for APIs (if you use cookies/session auth, consider enabling and using CSRF token)
                .csrf(csrf -> csrf.disable())

                // Session stateless for REST
                .sessionManagement(sm -> sm.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS))

                // Authorize requests
                .authorizeHttpRequests(auth -> auth
                        // 1) Allow Stripe webhook publicly (no auth)
                        .requestMatchers("/api/payment/webhook/stripe").permitAll()
                        .requestMatchers("/api/payment/status/**").permitAll()

                        // 2) All other /api/payment/** still require auth
                        .requestMatchers("/api/payment/**").authenticated()

                        // 3) Any other request also requires auth
                        .anyRequest().authenticated()
                )


                // For demo only: use default form/login disabled
                .httpBasic(Customizer.withDefaults());

        // Allow H2 console frames
        http.headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}
