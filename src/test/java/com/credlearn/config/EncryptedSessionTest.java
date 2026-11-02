package com.credlearn.config;

import static org.assertj.core.api.Assertions.*;

import com.credlearn.service.GoogleConnection;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class EncryptedSessionTest {
  private SharedSessionConfiguration.EncryptedAttributes codec(int value) {
    byte[] key = new byte[32];
    Arrays.fill(key, (byte) value);
    return new SharedSessionConfiguration.EncryptedAttributes(
        Base64.getEncoder().encodeToString(key));
  }

  @Test
  void encryptsGoogleTokensAndUsesDifferentNonces() {
    var tokens =
        new GoogleConnection.Tokens(1L, "subject", "test-access", "test-refresh", Instant.now());
    var first = codec(1).encode(tokens);
    var second = codec(1).encode(tokens);
    assertThat(first).isNotEqualTo(second);
    assertThat(new String(first, java.nio.charset.StandardCharsets.ISO_8859_1))
        .doesNotContain("test-access", "test-refresh");
    assertThat(codec(1).decode(first)).isEqualTo(tokens);
  }

  @Test
  void rejectsTamperingAndWrongKeysBeforeDeserialization() {
    byte[] data = codec(1).encode("private");
    assertThatThrownBy(() -> codec(2).decode(data)).isInstanceOf(IllegalStateException.class);
    data[data.length - 1] ^= 1;
    assertThatThrownBy(() -> codec(1).decode(data)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> codec(1).decode(new byte[10]))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsShortKeys() {
    assertThatThrownBy(() -> new SharedSessionConfiguration.EncryptedAttributes("YQ=="))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
