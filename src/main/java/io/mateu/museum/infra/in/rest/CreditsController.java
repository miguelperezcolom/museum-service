package io.mateu.museum.infra.in.rest;

import io.mateu.museum.domain.Artwork;
import io.mateu.museum.infra.out.ArtworkRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where every image comes from and under which licence. All of them are CC0 — nothing is legally
 * owed — but the museums ask for a courtesy credit, and a demo that shows other people's pictures
 * had better say whose they are. The same list is in CREDITS.md.
 */
@RestController
@RequestMapping("/api/credits")
@Tag(name = "Credits", description = "The source and licence of every image")
public class CreditsController {

  static final String CC0 = "CC0 1.0";
  static final String CC0_URL = "https://creativecommons.org/publicdomain/zero/1.0/";

  /** One image and what to say about it. */
  public record Credit(
      Long artworkId,
      String title,
      String museum,
      String credit,
      String license,
      String licenseUrl,
      String imageSmall,
      String imageLarge,
      String sourceUrl) {}

  private final ArtworkRepository artworks;

  public CreditsController(ArtworkRepository artworks) {
    this.artworks = artworks;
  }

  @Operation(summary = "Every image's museum, licence and source page")
  @Transactional(readOnly = true)
  @GetMapping
  public List<Credit> credits() {
    return artworks.findAll(Sort.by("id")).stream().map(CreditsController::credit).toList();
  }

  private static Credit credit(Artwork a) {
    return new Credit(
        a.getId(),
        a.getTitle(),
        a.getMuseum() == null ? null : a.getMuseum().getName(),
        a.getImageCredit(),
        CC0,
        CC0_URL,
        a.getImageSmall(),
        a.getImageLarge(),
        a.getSourceUrl());
  }
}
