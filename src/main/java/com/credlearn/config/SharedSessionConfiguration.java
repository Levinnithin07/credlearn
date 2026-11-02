package com.credlearn.config;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.*;
import org.springframework.util.SerializationUtils;

/** Shared sessions are opt-in; all attributes (including Google tokens) use AES-GCM. */
@Configuration
@Profile("vercel")
@EnableJdbcHttpSession(tableName = "credlearn_http_sessions", maxInactiveIntervalInSeconds = 1800)
public class SharedSessionConfiguration {
  @Bean("springSessionConversionService")
  GenericConversionService conversionService(@Value("${credlearn.session-key}") String key) {
    var codec = new EncryptedAttributes(key);
    var service = new GenericConversionService();
    service.addConverter(Object.class, byte[].class, (Converter<Object, byte[]>) codec::encode);
    service.addConverter(byte[].class, Object.class, (Converter<byte[], Object>) codec::decode);
    return service;
  }

  @Bean
  CookieSerializer cookieSerializer(
      @Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
    var cookie = new DefaultCookieSerializer();
    cookie.setCookieName("JSESSIONID");
    cookie.setCookiePath("/");
    cookie.setUseSecureCookie(secure);
    cookie.setUseHttpOnlyCookie(true);
    cookie.setSameSite("Lax");
    return cookie;
  }

  static final class EncryptedAttributes {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    EncryptedAttributes(String encoded) {
      byte[] bytes = Base64.getDecoder().decode(encoded);
      if (bytes.length != 32)
        throw new IllegalArgumentException("Session key must contain 32 random bytes.");
      key = new SecretKeySpec(bytes, "AES");
    }

    byte[] encode(Object value) {
      try {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(
            "CredLearn session v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        byte[] encrypted = cipher.doFinal(SerializationUtils.serialize(value));
        byte[] result = Arrays.copyOf(nonce, nonce.length + encrypted.length);
        System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
        return result;
      } catch (Exception e) {
        throw new IllegalStateException("Session attribute could not be encrypted.", e);
      }
    }

    Object decode(byte[] bytes) {
      try {
        if (bytes.length < 28) throw new IllegalArgumentException();
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(bytes, 12)));
        cipher.updateAAD(
            "CredLearn session v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        // Only authenticated ciphertext is deserialized; untrusted bytes fail before this step.
        return SerializationUtils.deserialize(cipher.doFinal(bytes, 12, bytes.length - 12));
      } catch (Exception e) {
        throw new IllegalStateException("Session attribute could not be authenticated.", e);
      }
    }
  }
}
