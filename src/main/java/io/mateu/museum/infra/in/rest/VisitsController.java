package io.mateu.museum.infra.in.rest;

import io.mateu.museum.domain.Visit;
import io.mateu.museum.infra.in.rest.dto.VisitDto;
import io.mateu.museum.infra.in.rest.query.FilterKind;
import io.mateu.museum.infra.in.rest.query.QuerySpec;
import io.mateu.museum.infra.out.ApiRepository;
import io.mateu.museum.infra.out.MuseumRepository;
import io.mateu.museum.infra.out.VisitRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visits")
@Tag(name = "Visits", description = "Visitors per museum per open day (synthetic)")
public class VisitsController extends CrudController<Visit, VisitDto> {

  private final VisitRepository visits;
  private final MuseumRepository museums;

  public VisitsController(VisitRepository visits, MuseumRepository museums) {
    this.visits = visits;
    this.museums = museums;
  }

  @Override
  protected String collection() {
    return "visits";
  }

  @Override
  protected ApiRepository<Visit> repository() {
    return visits;
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("museumId", "museum.id", FilterKind.REF)
        .filter("date", FilterKind.DATE)
        .filter("visitors", FilterKind.NUMBER)
        .filter("members", FilterKind.NUMBER)
        .filter("revenue", FilterKind.NUMBER)
        .defaultSort("date")
        .build();
  }

  @Override
  protected Map<String, String> sortAliases() {
    return Map.of("museumName", "museum.name");
  }

  @Override
  protected Visit newEntity() {
    return new Visit();
  }

  @Override
  protected Long idOf(VisitDto dto) {
    return dto.id();
  }

  @Override
  protected VisitDto toDto(Visit v) {
    return new VisitDto(
        v.getId(),
        v.getMuseum() == null ? null : v.getMuseum().getId(),
        v.getMuseum() == null ? null : v.getMuseum().getName(),
        v.getDate(),
        v.getVisitors(),
        v.getMembers(),
        v.getRevenue(),
        urlOf(v.getId()));
  }

  @Override
  protected void apply(VisitDto dto, Visit v) {
    v.setMuseum(resolveOne(dto.museumId(), museums, "museum"));
    v.setDate(dto.date());
    v.setVisitors(dto.visitors());
    v.setMembers(dto.members());
    v.setRevenue(dto.revenue());
  }
}
