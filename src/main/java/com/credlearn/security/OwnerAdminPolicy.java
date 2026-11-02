package com.credlearn.security;

import com.credlearn.config.AdminOwnerProperties;
import com.credlearn.entity.AccountRole;
import com.credlearn.entity.UserAccount;
import com.credlearn.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Only the privately configured account can exercise administrator privileges. */
@Component("ownerAdminPolicy")
@RequiredArgsConstructor
public class OwnerAdminPolicy {
  private final AdminOwnerProperties configuration;
  private final UserRepository users;

  public boolean isOwnerAccount(UserAccount account) {
    Long ownerId = configuration.ownerUserId();
    return ownerId != null
        && ownerId > 0
        && ownerId.equals(account.getId())
        && account.getRole() == AccountRole.ADMIN;
  }

  public boolean allowed(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication.getAuthorities().stream()
            .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) return false;
    // Re-read persisted role and owner ID for each request: old sessions cannot bypass revocation.
    return users
        .findByEmail(authentication.getName().trim().toLowerCase(Locale.ROOT))
        .map(this::isOwnerAccount)
        .orElse(false);
  }
}
