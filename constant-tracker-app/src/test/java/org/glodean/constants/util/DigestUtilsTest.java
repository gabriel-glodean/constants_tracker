package org.glodean.constants.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DigestUtils.
 */
class DigestUtilsTest {

  @Test
  void newSha256_returnsMessageDigest() {
    assertThat(DigestUtils.newSha256()).isNotNull();
    assertThat(DigestUtils.newSha256().getAlgorithm()).isEqualTo("SHA-256");
  }

  @Test
  void hexEncode_returnsLowercaseHex() {
    byte[] bytes = new byte[]{(byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef};
    assertThat(DigestUtils.hexEncode(bytes)).isEqualTo("deadbeef");
  }

  @Test
  void hexEncode_emptyArrayReturnsEmptyString() {
    assertThat(DigestUtils.hexEncode(new byte[0])).isEmpty();
  }

  @Test
  void sha256Hex_returnsSixtyFourCharLowercaseHex() throws IOException {
    byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
    String hex = DigestUtils.sha256Hex(new ByteArrayInputStream(input));
    assertThat(hex).hasSize(64);
    assertThat(hex).matches("[0-9a-f]{64}");
  }

  @Test
  void sha256Hex_isDeterministic() throws IOException {
    byte[] input = "deterministic-test".getBytes(StandardCharsets.UTF_8);
    String hash1 = DigestUtils.sha256Hex(new ByteArrayInputStream(input));
    String hash2 = DigestUtils.sha256Hex(new ByteArrayInputStream(input));
    assertThat(hash1).isEqualTo(hash2);
  }

  @Test
  void sha256Hex_differentInputsProduceDifferentHashes() throws IOException {
    String hash1 = DigestUtils.sha256Hex(
        new ByteArrayInputStream("input-a".getBytes(StandardCharsets.UTF_8)));
    String hash2 = DigestUtils.sha256Hex(
        new ByteArrayInputStream("input-b".getBytes(StandardCharsets.UTF_8)));
    assertThat(hash1).isNotEqualTo(hash2);
  }

  @Test
  void sha256Hex_emptyInputProducesKnownHash() throws IOException {
    // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
    String hex = DigestUtils.sha256Hex(new ByteArrayInputStream(new byte[0]));
    assertThat(hex).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  }
}
