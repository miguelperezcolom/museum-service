package io.mateu.museum.infra.in.rest;

import io.mateu.museum.domain.Department;
import io.mateu.museum.infra.in.rest.dto.DepartmentDto;
import io.mateu.museum.infra.in.rest.query.FilterKind;
import io.mateu.museum.infra.in.rest.query.QuerySpec;
import io.mateu.museum.infra.out.ApiRepository;
import io.mateu.museum.infra.out.ArtworkRepository;
import io.mateu.museum.infra.out.DepartmentRepository;
import io.mateu.museum.infra.out.MuseumRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments")
@Tag(name = "Departments", description = "Curatorial departments, each belonging to one museum")
public class DepartmentsController extends CrudController<Department, DepartmentDto> {

  private final DepartmentRepository departments;
  private final MuseumRepository museums;
  private final ArtworkRepository artworks;

  public DepartmentsController(
      DepartmentRepository departments, MuseumRepository museums, ArtworkRepository artworks) {
    this.departments = departments;
    this.museums = museums;
    this.artworks = artworks;
  }

  @Override
  protected String collection() {
    return "departments";
  }

  @Override
  protected ApiRepository<Department> repository() {
    return departments;
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("name", FilterKind.TEXT)
        .filter("head", FilterKind.TEXT)
        .filter("museumId", "museum.id", FilterKind.REF)
        .searchable("name", "head")
        .defaultSort("name")
        .build();
  }

  @Override
  protected Map<String, String> sortAliases() {
    return Map.of("museumName", "museum.name");
  }

  @Override
  protected Department newEntity() {
    return new Department();
  }

  @Override
  protected Long idOf(DepartmentDto dto) {
    return dto.id();
  }

  @Override
  protected DepartmentDto toDto(Department d) {
    return new DepartmentDto(
        d.getId(),
        d.getName(),
        d.getHead(),
        d.getMuseum() == null ? null : d.getMuseum().getId(),
        d.getMuseum() == null ? null : d.getMuseum().getName(),
        artworks.countByDepartment_Id(d.getId()),
        urlOf(d.getId()));
  }

  @Override
  protected void apply(DepartmentDto dto, Department d) {
    d.setName(dto.name());
    d.setHead(dto.head());
    d.setMuseum(resolveOne(dto.museumId(), museums, "museum"));
  }
}
