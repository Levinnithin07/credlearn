package com.credlearn.dto;

import com.credlearn.entity.Category;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

public final class Forms {
  @Data
  public static class Registration {
    @NotBlank
    @Size(max = 100)
    private String fullName = "";

    @NotBlank
    @Email
    @Size(max = 254)
    private String email = "";

    @NotBlank
    @Size(min = 10, max = 72)
    private String password = "";

    @NotBlank private String confirmPassword = "";

    @NotBlank
    @Size(max = 100)
    private String department = "";

    @Min(1)
    @Max(6)
    private int yearOfStudy = 1;

    @Size(max = 1000)
    private String bio = "";
  }

  @Data
  public static class Profile {
    @NotBlank
    @Size(max = 100)
    private String fullName;

    @NotBlank
    @Size(max = 100)
    private String department;

    @Min(1)
    @Max(6)
    private int yearOfStudy;

    @Size(max = 1000)
    private String bio;
  }

  @Data
  public static class SkillForm {
    @NotBlank
    @Size(max = 100)
    private String title = "";

    @NotBlank
    @Size(min = 20, max = 2000)
    private String description = "";

    @NotNull private Category category = Category.PROGRAMMING;
  }

  @Data
  public static class InterestForm {
    @NotBlank
    @Size(max = 100)
    private String skillName = "";

    @NotNull private Category category = Category.OTHER;
  }

  @Data
  public static class SessionForm {
    @NotNull
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startsAt;

    @Min(30)
    @Max(240)
    private int durationMinutes = 60;

    @Size(max = 1000)
    private String message = "";
  }

  @Data
  public static class PasswordForm {
    @NotBlank private String currentPassword = "";

    @NotBlank
    @Size(min = 10, max = 72)
    private String newPassword = "";

    @NotBlank private String confirmPassword = "";
  }
}
