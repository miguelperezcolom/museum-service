package io.mateu.museum.infra.in.rest;

import io.mateu.museum.infra.out.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One search box over every collection — artworks, artists, exhibitions, museums — for a "search
 * everything" page or a command palette. Each hit says what it is ({@code category}), how to show it
 * ({@code label}, {@code description}, {@code image}) and where it lives in a UI ({@code route}:
 * {@code artworks/12}, {@code artists/3}…), so a client can list mixed results and open any of them
 * without knowing the collections.
 */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Search", description = "Search across every collection at once")
public class SearchController {

  public record Hit(
      String id, String category, String label, String description, String image, String route) {}

  private final ArtworkRepository artworks;
  private final ArtistRepository artists;
  private final ExhibitionRepository exhibitions;
  private final MuseumRepository museums;

  public SearchController(
      ArtworkRepository artworks,
      ArtistRepository artists,
      ExhibitionRepository exhibitions,
      MuseumRepository museums) {
    this.artworks = artworks;
    this.artists = artists;
    this.exhibitions = exhibitions;
    this.museums = museums;
  }

  @Operation(
      summary = "Search everything",
      description = "Case-insensitive containment over the main text fields of each collection. "
          + "Museums first, then artists, exhibitions and artworks. Paged like every listing.")
  @Transactional(readOnly = true)
  @GetMapping
  public Map<String, Object> search(
      @Parameter(description = "What to look for; blank returns the highlights")
          @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    List<Hit> hits = new ArrayList<>();
    if (!q.isEmpty()) {
      museums.findAll(Sort.by("name")).stream()
          .filter(m -> has(q, m.getName(), m.getShortName(), m.getCity(), m.getCountry()))
          .forEach(m -> hits.add(new Hit("museum-" + m.getId(), "Museum", m.getName(),
              m.getCity() + ", " + m.getCountry(), m.getImage(), "museums/" + m.getId())));
      artists.findAll(Sort.by("name")).stream()
          .filter(a -> has(q, a.getName(), a.getNationality()))
          .forEach(a -> hits.add(new Hit("artist-" + a.getId(), "Artist", a.getName(),
              join(a.getNationality(), ArtistsController.lifespan(a.getBirthYear(), a.getDeathYear())),
              a.getImage(), "artists/" + a.getId())));
      exhibitions.findAll(Sort.by("startDate")).stream()
          .filter(e -> has(q, e.getTitle(), e.getCurator(), e.getMuseum() == null ? null : e.getMuseum().getName()))
          .forEach(e -> hits.add(new Hit("exhibition-" + e.getId(), "Exhibition", e.getTitle(),
              join(e.getMuseum() == null ? null : e.getMuseum().getShortName(), e.getStatus()),
              e.getArtworks().stream().findFirst().map(x -> x.getImageSmall()).orElse(null),
              "exhibitions/" + e.getId())));
    }
    artworks.findAll(Sort.by("title")).stream()
        .filter(a -> q.isEmpty()
            ? Boolean.TRUE.equals(a.getHighlight())
            : has(q, a.getTitle(), a.getArtistDisplay(), a.getCulture(), a.getClassification(), a.getTags()))
        .forEach(a -> hits.add(new Hit("artwork-" + a.getId(), "Artwork", a.getTitle(),
            join(a.getArtistDisplay(), a.getObjectDate(), a.getMuseum() == null ? null : a.getMuseum().getShortName()),
            a.getImageSmall(), "artworks/" + a.getId())));

    int pageSize = Math.min(Math.max(size, 1), 200);
    int from = Math.min(Math.max(page, 0) * pageSize, hits.size());
    int to = Math.min(from + pageSize, hits.size());
    return Map.of(
        "content", hits.subList(from, to),
        "totalElements", hits.size(),
        "totalPages", (hits.size() + pageSize - 1) / pageSize,
        "page", Math.max(page, 0),
        "size", pageSize);
  }

  private static boolean has(String q, String... values) {
    for (String v : values) if (v != null && v.toLowerCase(Locale.ROOT).contains(q)) return true;
    return false;
  }

  private static String join(String... parts) {
    var out = new ArrayList<String>();
    for (String p : parts) if (p != null && !p.isBlank()) out.add(p);
    return String.join(" · ", out);
  }
}
