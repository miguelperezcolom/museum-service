package io.mateu.museum.infra.in.rest;

import io.mateu.museum.domain.Museum;
import io.mateu.museum.infra.in.rest.dto.MuseumDto;
import io.mateu.museum.infra.in.rest.query.FilterKind;
import io.mateu.museum.infra.in.rest.query.QuerySpec;
import io.mateu.museum.infra.out.ApiRepository;
import io.mateu.museum.infra.out.ArtworkRepository;
import io.mateu.museum.infra.out.DepartmentRepository;
import io.mateu.museum.infra.out.MuseumRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/museums")
@Tag(name = "Museums", description = "The museums whose collections this API serves")
public class MuseumsController extends CrudController<Museum, MuseumDto> {

  private final MuseumRepository museums;
  private final DepartmentRepository departments;
  private final ArtworkRepository artworks;

  public MuseumsController(
      MuseumRepository museums, DepartmentRepository departments, ArtworkRepository artworks) {
    this.museums = museums;
    this.departments = departments;
    this.artworks = artworks;
  }

  @Override
  protected String collection() {
    return "museums";
  }

  @Override
  protected ApiRepository<Museum> repository() {
    return museums;
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("name", FilterKind.TEXT)
        .filter("code", FilterKind.EXACT)
        .filter("city", FilterKind.TEXT)
        .filter("country", FilterKind.EXACT)
        .filter("founded", FilterKind.NUMBER)
        .searchable("name", "shortName", "city", "country")
        .defaultSort("name")
        .build();
  }

  @Override
  protected Museum newEntity() {
    return new Museum();
  }

  @Override
  protected Long idOf(MuseumDto dto) {
    return dto.id();
  }

  @Override
  protected MuseumDto toDto(Museum m) {
    return new MuseumDto(
        m.getId(),
        m.getCode(),
        m.getName(),
        m.getShortName(),
        m.getCity(),
        m.getCountry(),
        m.getLatitude(),
        m.getLongitude(),
        m.getFounded(),
        m.getWebsite(),
        m.getDescription(),
        m.getImage(),
        m.getImageCredit(),
        departments.countByMuseum_Id(m.getId()),
        artworks.countByMuseum_Id(m.getId()),
        urlOf(m.getId()));
  }

  @Override
  protected void apply(MuseumDto dto, Museum m) {
    m.setCode(dto.code());
    m.setName(dto.name());
    m.setShortName(dto.shortName());
    m.setCity(dto.city());
    m.setCountry(dto.country());
    m.setLatitude(dto.latitude());
    m.setLongitude(dto.longitude());
    m.setFounded(dto.founded());
    m.setWebsite(dto.website());
    m.setDescription(dto.description());
    m.setImage(keep(dto.image(), m.getImage()));
    m.setImageCredit(keep(dto.imageCredit(), m.getImageCredit()));
  }
}
