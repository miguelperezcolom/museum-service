package io.mateu.museum.infra.in.rest;

import io.mateu.museum.domain.Artwork;
import io.mateu.museum.domain.Exhibition;
import io.mateu.museum.infra.in.rest.dto.ExhibitionDto;
import io.mateu.museum.infra.in.rest.query.FilterKind;
import io.mateu.museum.infra.in.rest.query.QuerySpec;
import io.mateu.museum.infra.out.ApiRepository;
import io.mateu.museum.infra.out.ArtworkRepository;
import io.mateu.museum.infra.out.ExhibitionRepository;
import io.mateu.museum.infra.out.MuseumRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashSet;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exhibitions")
@Tag(name = "Exhibitions", description = "Exhibitions (synthetic) — dated, with a status and the works they show")
public class ExhibitionsController extends CrudController<Exhibition, ExhibitionDto> {

  private final ExhibitionRepository exhibitions;
  private final MuseumRepository museums;
  private final ArtworkRepository artworks;

  public ExhibitionsController(
      ExhibitionRepository exhibitions, MuseumRepository museums, ArtworkRepository artworks) {
    this.exhibitions = exhibitions;
    this.museums = museums;
    this.artworks = artworks;
  }

  @Override
  protected String collection() {
    return "exhibitions";
  }

  @Override
  protected ApiRepository<Exhibition> repository() {
    return exhibitions;
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("title", FilterKind.TEXT)
        .filter("curator", FilterKind.TEXT)
        .filter("status", FilterKind.EXACT)
        .filter("museumId", "museum.id", FilterKind.REF)
        .filter("startDate", FilterKind.DATE)
        .filter("endDate", FilterKind.DATE)
        .filter("budget", FilterKind.NUMBER)
        .filter("visitors", FilterKind.NUMBER)
        .searchable("title", "curator", "description")
        .defaultSort("startDate")
        .build();
  }

  @Override
  protected Map<String, String> sortAliases() {
    return Map.of("museumName", "museum.name");
  }

  @Override
  protected Exhibition newEntity() {
    return new Exhibition();
  }

  @Override
  protected Long idOf(ExhibitionDto dto) {
    return dto.id();
  }

  @Override
  protected ExhibitionDto toDto(Exhibition e) {
    Artwork poster = e.getArtworks().stream().findFirst().orElse(null);
    return new ExhibitionDto(
        e.getId(),
        e.getTitle(),
        e.getMuseum() == null ? null : e.getMuseum().getId(),
        e.getMuseum() == null ? null : e.getMuseum().getName(),
        e.getStartDate(),
        e.getEndDate(),
        e.getStatus(),
        e.getCurator(),
        e.getDescription(),
        e.getBudget(),
        e.getVisitors(),
        e.getArtworks().stream().map(Artwork::getId).toList(),
        e.getArtworks().size(),
        poster == null ? null : poster.getImageLarge(),
        poster == null ? null : poster.getImageCredit(),
        urlOf(e.getId()));
  }

  @Override
  protected void apply(ExhibitionDto dto, Exhibition e) {
    e.setTitle(dto.title());
    e.setMuseum(resolveOne(dto.museumId(), museums, "museum"));
    e.setStartDate(dto.startDate());
    e.setEndDate(dto.endDate());
    e.setStatus(dto.status());
    e.setCurator(dto.curator());
    e.setDescription(dto.description());
    e.setBudget(dto.budget());
    e.setVisitors(dto.visitors());
    // a body without the list leaves the hang as it is; an empty list clears it
    if (dto.artworkIds() != null) {
      var resolved = new LinkedHashSet<>(resolve(dto.artworkIds(), artworks, "artwork"));
      e.getArtworks().clear();
      e.getArtworks().addAll(resolved);
    }
  }
}
