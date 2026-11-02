package com.credlearn.config;

import com.credlearn.security.OwnerAdminPolicy;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AdminOwnerProperties.class)
public class AppConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, OwnerAdminPolicy ownerAdmin) throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(
                        jakarta.servlet.DispatcherType.ASYNC, jakarta.servlet.DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        "/",
                        "/register",
                        "/login",
                        "/admin/login",
                        "/css/**",
                        "/brand/**",
                        "/js/**",
                        "/vendor/**",
                        "/error")
                    .permitAll()
                    .requestMatchers("/admin", "/admin/**")
                    .access(
                        (authentication, context) ->
                            new AuthorizationDecision(ownerAdmin.allowed(authentication.get())))
                    .anyRequest()
                    .hasRole("STUDENT"))
        .formLogin(
            f ->
                f.loginPage("/login")
                    .usernameParameter("email")
                    .successHandler(
                        (request, response, authentication) -> {
                          boolean admin =
                              authentication.getAuthorities().stream()
                                  .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                          response.sendRedirect(admin ? "/admin" : "/dashboard");
                        })
                    .failureHandler(
                        (request, response, exception) ->
                            response.sendRedirect(
                                "admin".equals(request.getParameter("loginArea"))
                                    ? "/admin/login?error"
                                    : "/login?error"))
                    .permitAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (request, response, exception) -> {
                      var path = request.getServletPath();
                      if (path.isEmpty()) path = request.getRequestURI();
                      if (path.startsWith("/api/")) response.sendError(401);
                      else
                        response.sendRedirect(
                            path.startsWith("/admin") ? "/admin/login" : "/login");
                    }))
        .logout(
            l ->
                l.logoutSuccessUrl("/login?logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID"))
        .sessionManagement(s -> s.sessionFixation(f -> f.migrateSession()))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; style-src 'self'; script-src 'self'; img-src"
                                + " 'self' data:; font-src 'self'; object-src 'none'; base-uri"
                                + " 'self'; frame-ancestors 'none'; form-action 'self'"
                                + " https://accounts.google.com")));
    return http.build();
  }
}
