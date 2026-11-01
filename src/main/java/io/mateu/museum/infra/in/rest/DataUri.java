package io.mateu.museum.infra.in.rest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * A base64 data URI as a browser's FileReader writes it — {@code data:<type>;base64,<bytes>} — with
 * the optional RFC 2397 {@code name=} parameter Mateu's file field stamps the file name into
 * ({@code data:application/pdf;name=agreement.pdf;base64,…}).
 */
record DataUri(String contentType, String name, byte[] bytes) {

  static DataUri parse(String value) {
    if (value == null || !value.startsWith("data:")) {
      throw new InvalidDocumentException("content must be a data URI (data:<type>;base64,…).");
    }
    int comma = value.indexOf(',');
    if (comma < 0) throw new InvalidDocumentException("content is not a complete data URI.");
    String[] meta = value.substring(5, comma).split(";");
    boolean base64 = false;
    String name = null;
    for (int i = 1; i < meta.length; i++) {
      if (meta[i].equals("base64")) base64 = true;
      else if (meta[i].startsWith("name=")) {
        name = URLDecoder.decode(meta[i].substring(5), StandardCharsets.UTF_8);
      }
    }
    if (!base64) throw new InvalidDocumentException("content must be base64-encoded.");
    byte[] bytes;
    try {
      bytes = Base64.getMimeDecoder().decode(value.substring(comma + 1));
    } catch (IllegalArgumentException e) {
      throw new InvalidDocumentException("content is not valid base64.");
    }
    String type = meta[0].isBlank() ? "application/octet-stream" : meta[0].trim().toLowerCase(Locale.ROOT);
    return new DataUri(type, name, bytes);
  }
}
