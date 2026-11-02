package com.credlearn.repository;

import com.credlearn.entity.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
  Optional<UserAccount> findByEmail(String email);

  boolean existsByEmail(String email);
}
