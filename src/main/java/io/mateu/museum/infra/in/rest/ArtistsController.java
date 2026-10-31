package io.mateu.museum.infra.in.rest;

import io.mateu.museum.domain.Artist;
import io.mateu.museum.infra.in.rest.dto.ArtistDto;
import io.mateu.museum.infra.in.rest.query.FilterKind;
import io.mateu.museum.infra.in.rest.query.QuerySpec;
import io.mateu.museum.infra.out.ApiRepository;
import io.mateu.museum.infra.out.ArtistRepository;
import io.mateu.museum.infra.out.ArtworkRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artists")
@Tag(name = "Artists", description = "The artists whose works are in the collections — one row across all museums")
public class ArtistsController extends CrudController<Artist, ArtistDto> {

  private final ArtistRepository artists;
  private final ArtworkRepository artworks;

  public ArtistsController(ArtistRepository artists, ArtworkRepository artworks) {
    this.artists = artists;
    this.artworks = artworks;
  }

  @Override
  protected String collection() {
    return "artists";
  }

  @Override
  protected ApiRepository<Artist> repository() {
    return artists;
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("name", FilterKind.TEXT)
        // nationality is a closed-ish set ("French", "Dutch"…): a pick, so "American" is not "Native American"
        .filter("nationality", FilterKind.EXACT)
        .filter("birthYear", FilterKind.NUMBER)
        .filter("deathYear", FilterKind.NUMBER)
        .searchable("name", "nationality", "bio")
        .defaultSort("name")
        .build();
  }

  @Override
  protected Artist newEntity() {
    return new Artist();
  }

  @Override
  protected Long idOf(ArtistDto dto) {
    return dto.id();
  }

  @Override
  protected ArtistDto toDto(Artist a) {
    return new ArtistDto(
        a.getId(),
        a.getName(),
        a.getNationality(),
        a.getBirthYear(),
        a.getDeathYear(),
        lifespan(a.getBirthYear(), a.getDeathYear()),
        a.getBio(),
        a.getImage(),
        a.getImageCredit(),
        artworks.countByArtist_Id(a.getId()),
        urlOf(a.getId()));
  }

  static String lifespan(Integer born, Integer died) {
    if (born == null) return died == null ? null : "died " + died;
    return died == null ? "born " + born : born + "–" + died;
  }

  @Override
  protected void apply(ArtistDto dto, Artist a) {
    a.setName(dto.name());
    a.setNationality(dto.nationality());
    a.setBirthYear(dto.birthYear());
    a.setDeathYear(dto.deathYear());
    a.setBio(dto.bio());
    a.setImage(keep(dto.image(), a.getImage()));
    a.setImageCredit(keep(dto.imageCredit(), a.getImageCredit()));
  }
}
