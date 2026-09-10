package com.senico.diagnostic.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senico.diagnostic.security.JwtAuthenticationFilter;
import com.senico.diagnostic.security.PasswordChangeGuardFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PasswordChangeGuardFilter passwordChangeGuardFilter;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Non authentifie"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Acces refuse"))
                )
                .authorizeHttpRequests(auth -> auth
                        // Changer son mot de passe suppose de savoir qui le demande : cette
                        // route precede le permitAll de /auth, qui ne vaut que pour la connexion.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/change-password").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        // Lire la liste des directions : indispensable au DG pour naviguer
                        // (selecteurs de groupe, ecrans de documents). L'ecriture sur /groups —
                        // creation, modification, mot de passe — reste a l'admin, juste en dessous.
                        .requestMatchers(HttpMethod.GET, "/api/v1/groups", "/api/v1/groups/*")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_DIRECTEUR_GENERAL")
                        // Administration technique : reste a l'admin, y compris vis-a-vis du DG.
                        // Ces regles precedent celle de /admin/** : la premiere qui correspond gagne.
                        .requestMatchers("/api/v1/groups/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/v1/admin/users/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/groups/*/cycles/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/groups/*/sections/*/content").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/groups/*/sections/**").hasAuthority("ROLE_ADMIN")
                        // Second niveau de validation : le DG seul approuve ce qui entre dans les
                        // documents consolides, l'admin ne pouvant pas se l'accorder a lui-meme.
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/groups/*/sections/*/dg-approval")
                        .hasAuthority("ROLE_DIRECTEUR_GENERAL")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/groups/*/sections/dg-approve-all")
                        .hasAuthority("ROLE_DIRECTEUR_GENERAL")
                        // Meme arbitrage, mais a l'echelle de la campagne : selection cochee dans
                        // la liste des soumissions, ou tout ce qui attend encore le DG.
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/dg-approvals/**")
                        .hasAuthority("ROLE_DIRECTEUR_GENERAL")
                        // Consultation, revision et validation : admin et direction generale.
                        .requestMatchers("/api/v1/admin/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_DIRECTEUR_GENERAL")
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Apres l'authentification : le garde a besoin du principal pour savoir si le
                // compte doit d'abord changer son mot de passe.
                .addFilterAfter(passwordChangeGuardFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    private void writeJsonError(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "status", status,
                "message", message
        )));
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
