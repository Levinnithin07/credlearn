package com.credlearn.security;

import com.credlearn.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentDetailsService implements UserDetailsService {
  private final UserRepository users;

  public UserDetails loadUserByUsername(String email) {
    var u =
        users
            .findByEmail(email.trim().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    String[] roles =
        u.getRole() == com.credlearn.entity.AccountRole.ADMIN
            ? new String[] {"STUDENT", "ADMIN"}
            : new String[] {"STUDENT"};
    return User.withUsername(u.getEmail()).password(u.getPasswordHash()).roles(roles).build();
  }
}
