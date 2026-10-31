package io.mateu.museum.infra.in.rest;

import io.mateu.museum.domain.ConservationTask;
import io.mateu.museum.infra.in.rest.dto.ConservationTaskDto;
import io.mateu.museum.infra.in.rest.query.FilterKind;
import io.mateu.museum.infra.in.rest.query.QuerySpec;
import io.mateu.museum.infra.out.ApiRepository;
import io.mateu.museum.infra.out.ArtworkRepository;
import io.mateu.museum.infra.out.ConservationTaskRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conservation-tasks")
@Tag(name = "Conservation tasks", description = "Conservation work on the artworks — the columns of a kanban board")
public class ConservationTasksController extends CrudController<ConservationTask, ConservationTaskDto> {

  private final ConservationTaskRepository tasks;
  private final ArtworkRepository artworks;

  public ConservationTasksController(ConservationTaskRepository tasks, ArtworkRepository artworks) {
    this.tasks = tasks;
    this.artworks = artworks;
  }

  @Override
  protected String collection() {
    return "conservation-tasks";
  }

  @Override
  protected ApiRepository<ConservationTask> repository() {
    return tasks;
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("title", FilterKind.TEXT)
        .filter("type", FilterKind.EXACT)
        .filter("priority", FilterKind.EXACT)
        .filter("status", FilterKind.EXACT)
        .filter("assignee", FilterKind.TEXT)
        .filter("artworkId", "artwork.id", FilterKind.REF)
        .filter("museumId", "artwork.museum.id", FilterKind.REF)
        .filter("dueDate", FilterKind.DATE)
        .filter("estimatedHours", FilterKind.NUMBER)
        .searchable("title", "assignee", "type")
        .defaultSort("dueDate")
        .build();
  }

  @Override
  protected Map<String, String> sortAliases() {
    return Map.of("artworkTitle", "artwork.title", "museumName", "artwork.museum.name");
  }

  @Override
  protected ConservationTask newEntity() {
    return new ConservationTask();
  }

  @Override
  protected Long idOf(ConservationTaskDto dto) {
    return dto.id();
  }

  @Override
  protected ConservationTaskDto toDto(ConservationTask t) {
    var a = t.getArtwork();
    var m = a == null ? null : a.getMuseum();
    return new ConservationTaskDto(
        t.getId(),
        t.getTitle(),
        a == null ? null : a.getId(),
        a == null ? null : a.getTitle(),
        a == null ? null : a.getImageSmall(),
        m == null ? null : m.getId(),
        m == null ? null : m.getName(),
        t.getType(),
        t.getPriority(),
        t.getStatus(),
        t.getAssignee(),
        t.getDueDate(),
        t.getEstimatedHours(),
        t.getNotes(),
        urlOf(t.getId()));
  }

  @Override
  protected void apply(ConservationTaskDto dto, ConservationTask t) {
    t.setTitle(dto.title());
    t.setArtwork(resolveOne(dto.artworkId(), artworks, "artwork"));
    t.setType(dto.type());
    t.setPriority(dto.priority());
    t.setStatus(dto.status() == null || dto.status().isBlank() ? "backlog" : dto.status());
    t.setAssignee(dto.assignee());
    t.setDueDate(dto.dueDate());
    t.setEstimatedHours(dto.estimatedHours());
    t.setNotes(dto.notes());
  }
}
