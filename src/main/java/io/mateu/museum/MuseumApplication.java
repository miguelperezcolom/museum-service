package io.mateu.museum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Several art museums behind one writable API.
 *
 * <p>The artworks are real: public-domain works of The Metropolitan Museum of Art, Statens Museum
 * for Kunst (Copenhagen) and the Cleveland Museum of Art, whose records and images those museums
 * publish free of rights (CC0, or the Public Domain Mark). They were fetched once by {@code scripts/build-seed.py} into
 * {@code src/main/resources/seed}; this service never calls a museum. Around them, synthetic but
 * plausible data — exhibitions, loans, conservation tasks, daily visits — gives a UI something to
 * plan, track and chart. Everything lives in an in-memory H2 seeded at boot.
 */
@SpringBootApplication
public class MuseumApplication {

  public static void main(String[] args) {
    SpringApplication.run(MuseumApplication.class, args);
  }
}
