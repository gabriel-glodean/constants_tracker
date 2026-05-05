package org.glodean.constants.auth;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
/**
 * Unit tests for {@link SecurityConfiguration}.
 *
 * <p>The full reactive filter chains require a running WebFlux context and are
 * tested in {@code LoginControllerTest}.  Here we only cover the pieces that
 * can be exercised without a Spring context.
 */
class SecurityConfigurationTest {
  private final SecurityConfiguration.SecuredConfig config = new SecurityConfiguration.SecuredConfig();
  @Test
  void passwordEncoder_isBCrypt() {
    PasswordEncoder encoder = config.passwordEncoder();
    assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
  }
  @Test
  void passwordEncoder_encodesAndVerifies() {
    PasswordEncoder encoder = config.passwordEncoder();
    String raw = "my-secret-password";
    String encoded = encoder.encode(raw);
    assertThat(encoder.matches(raw, encoded)).isTrue();
    assertThat(encoder.matches("wrong", encoded)).isFalse();
  }
  @Test
  void passwordEncoder_differentEncodingsOfSamePassword_bothValid() {
    PasswordEncoder encoder = config.passwordEncoder();
    String raw = "shared-password";
    // BCrypt produces different hashes each time (random salt)
    String hash1 = encoder.encode(raw);
    String hash2 = encoder.encode(raw);
    assertThat(hash1).isNotEqualTo(hash2);
    assertThat(encoder.matches(raw, hash1)).isTrue();
    assertThat(encoder.matches(raw, hash2)).isTrue();
  }
}
