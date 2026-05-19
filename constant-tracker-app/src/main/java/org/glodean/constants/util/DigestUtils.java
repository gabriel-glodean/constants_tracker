package org.glodean.constants.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Utility methods for SHA-256 hashing. */
public final class DigestUtils {

  private DigestUtils() {}

  /**
   * Returns a fresh SHA-256 {@link MessageDigest}.
   *
   * @throws IllegalStateException if SHA-256 is not available (should never happen on standard JVMs)
   */
  public static MessageDigest newSha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /**
   * Reads {@code in} to EOF and returns its SHA-256 hex digest. The stream is not closed.
   *
   * @param in the input stream to hash
   * @return lowercase hex-encoded SHA-256 digest
   * @throws IOException if reading from the stream fails
   */
  public static String sha256Hex(InputStream in) throws IOException {
    MessageDigest digest = newSha256();
    try (DigestInputStream dis = new DigestInputStream(in, digest)) {
      dis.transferTo(OutputStream.nullOutputStream());
    }
    return hexEncode(digest.digest());
  }

  /**
   * Encodes a digest byte array as a lowercase hex string.
   *
   * @param bytes raw digest bytes
   * @return lowercase hex string
   */
  public static String hexEncode(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }
}
