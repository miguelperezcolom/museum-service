package io.mateu.museum.infra.in.rest;

import io.mateu.museum.domain.*;
import io.mateu.museum.infra.out.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The numbers a dashboard opens with, computed from the live tables (so a write shows up here on the
 * next read), and the collection as a tree — museum → department → gallery — for a tree view.
 *
 * <p>The top-level figures are FLAT on purpose: a page that loads this into its state can show
 * {@code ${state.visitorsYtd}} directly, with no path to walk. Each big number also comes
 * pre-formatted ({@code …Label}), because a template interpolates; it does not format.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Stats", description = "Aggregates for dashboards, and the collection as a tree")
public class StatsController {

  private final MuseumRepository museums;
  private final DepartmentRepository departments;
  private final ArtistRepository artists;
  private final ArtworkRepository artworks;
  private final ExhibitionRepository exhibitions;
  private final LoanRepository loans;
  private final ConservationTaskRepository tasks;
  private final VisitRepository visits;

  public StatsController(
      MuseumRepository museums,
      DepartmentRepository departments,
      ArtistRepository artists,
      ArtworkRepository artworks,
      ExhibitionRepository exhibitions,
      LoanRepository loans,
      ConservationTaskRepository tasks,
      VisitRepository visits) {
    this.museums = museums;
    this.departments = departments;
    this.artists = artists;
    this.artworks = artworks;
    this.exhibitions = exhibitions;
    this.loans = loans;
    this.tasks = tasks;
    this.visits = visits;
  }

  public record MuseumStats(
      Long museumId, String code, String name, long artworks, long onView, long visitors,
      BigDecimal revenue, long openExhibitions) {}

  public record MonthStats(String month, Map<String, Long> visitorsByMuseum, long visitors) {}

  public record Stats(
      long museums,
      long departments,
      long artists,
      long artworks,
      long onView,
      long highlights,
      long exhibitions,
      long openExhibitions,
      long plannedExhibitions,
      long loans,
      long activeLoans,
      long openTasks,
      long urgentTasks,
      long visitorsYtd,
      String visitorsYtdLabel,
      BigDecimal revenueYtd,
      String revenueYtdLabel,
      BigDecimal collectionValue,
      String collectionValueLabel,
      LocalDate lastVisitDate,
      List<MuseumStats> perMuseum,
      Map<String, Long> artworksByClassification,
      Map<String, Long> loansByStatus,
      Map<String, Long> tasksByStatus,
      List<MonthStats> visitsByMonth) {}

  @Operation(summary = "Dashboard figures, computed from the live tables")
  @Transactional(readOnly = true)
  @GetMapping("/stats")
  public Stats stats() {
    var allArtworks = artworks.findAll();
    var allVisits = visits.findAll(Sort.by("date"));
    var allExhibitions = exhibitions.findAll();
    var allLoans = loans.findAll();
    var allTasks = tasks.findAll();

    long visitorsYtd = allVisits.stream().mapToLong(v -> nz(v.getVisitors())).sum();
    BigDecimal revenueYtd = allVisits.stream().map(Visit::getRevenue).filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal value = allArtworks.stream().map(Artwork::getEstimatedValue).filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    List<MuseumStats> perMuseum = museums.findAll(Sort.by("id")).stream()
        .map(m -> new MuseumStats(
            m.getId(), m.getCode(), m.getName(),
            allArtworks.stream().filter(a -> is(a.getMuseum(), m)).count(),
            allArtworks.stream().filter(a -> is(a.getMuseum(), m) && Boolean.TRUE.equals(a.getOnView())).count(),
            allVisits.stream().filter(v -> is(v.getMuseum(), m)).mapToLong(v -> nz(v.getVisitors())).sum(),
            allVisits.stream().filter(v -> is(v.getMuseum(), m)).map(Visit::getRevenue)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add),
            allExhibitions.stream().filter(e -> is(e.getMuseum(), m) && "open".equals(e.getStatus())).count()))
        .toList();

    Map<String, Map<String, Long>> byMonth = new TreeMap<>();
    for (Visit v : allVisits) {
      if (v.getDate() == null || v.getMuseum() == null) continue;
      byMonth.computeIfAbsent(v.getDate().toString().substring(0, 7), k -> new TreeMap<>())
          .merge(v.getMuseum().getCode(), (long) nz(v.getVisitors()), Long::sum);
    }

    return new Stats(
        museums.count(),
        departments.count(),
        artists.count(),
        allArtworks.size(),
        allArtworks.stream().filter(a -> Boolean.TRUE.equals(a.getOnView())).count(),
        allArtworks.stream().filter(a -> Boolean.TRUE.equals(a.getHighlight())).count(),
        allExhibitions.size(),
        allExhibitions.stream().filter(e -> "open".equals(e.getStatus())).count(),
        allExhibitions.stream().filter(e -> "planned".equals(e.getStatus())).count(),
        allLoans.size(),
        allLoans.stream().filter(l -> Set.of("approved", "in_transit", "on_display").contains(l.getStatus())).count(),
        allTasks.stream().filter(t -> !"done".equals(t.getStatus())).count(),
        allTasks.stream().filter(t -> "urgent".equals(t.getPriority()) && !"done".equals(t.getStatus())).count(),
        visitorsYtd,
        compact(BigDecimal.valueOf(visitorsYtd), ""),
        revenueYtd,
        compact(revenueYtd, "$"),
        value,
        compact(value, "$"),
        allVisits.isEmpty() ? null : allVisits.get(allVisits.size() - 1).getDate(),
        perMuseum,
        counts(allArtworks.stream().map(Artwork::getClassification)),
        counts(allLoans.stream().map(Loan::getStatus)),
        counts(allTasks.stream().map(ConservationTask::getStatus)),
        byMonth.entrySet().stream()
            .map(e -> new MonthStats(e.getKey(), e.getValue(),
                e.getValue().values().stream().mapToLong(Long::longValue).sum()))
            .toList());
  }

  /** One node of the collection tree. A listing in tree layout reads {@code children}. */
  public record Node(String id, String name, String kind, String museum, long artworks,
      long onView, List<Node> children) {}

  @Operation(
      summary = "The collection as a tree: museum → department → gallery",
      description = "Wrapped like every listing ({content, totalElements}); the leaves are galleries, "
          + "with the works not on view grouped under \"In storage\".")
  @Transactional(readOnly = true)
  @GetMapping("/collection-tree")
  public Map<String, Object> tree() {
    var allArtworks = artworks.findAll();
    var roots = new ArrayList<Node>();
    for (Museum m : museums.findAll(Sort.by("id"))) {
      var depts = new ArrayList<Node>();
      for (Department d : departments.findAll(Sort.by("name"))) {
        if (!is(d.getMuseum(), m)) continue;
        var works = allArtworks.stream().filter(a -> a.getDepartment() != null
            && a.getDepartment().getId().equals(d.getId())).toList();
        Map<String, List<Artwork>> byGallery = works.stream().collect(Collectors.groupingBy(
            a -> Boolean.TRUE.equals(a.getOnView()) && a.getGallery() != null ? a.getGallery() : "In storage",
            TreeMap::new, Collectors.toList()));
        var galleries = byGallery.entrySet().stream()
            .map(g -> new Node("g" + d.getId() + "-" + g.getKey().replaceAll("\\W+", ""), g.getKey(),
                "Gallery", m.getShortName(), g.getValue().size(),
                g.getValue().stream().filter(a -> Boolean.TRUE.equals(a.getOnView())).count(), List.of()))
            .toList();
        depts.add(new Node("d" + d.getId(), d.getName(), "Department", m.getShortName(), works.size(),
            works.stream().filter(a -> Boolean.TRUE.equals(a.getOnView())).count(), galleries));
      }
      long count = depts.stream().mapToLong(Node::artworks).sum();
      long onView = depts.stream().mapToLong(Node::onView).sum();
      roots.add(new Node("m" + m.getId(), m.getName(), "Museum", m.getShortName(), count, onView, depts));
    }
    return Map.of("content", roots, "totalElements", roots.size());
  }

  private static boolean is(Museum a, Museum b) {
    return a != null && b != null && a.getId().equals(b.getId());
  }

  private static int nz(Integer v) {
    return v == null ? 0 : v;
  }

  private static Map<String, Long> counts(java.util.stream.Stream<String> values) {
    return values.filter(Objects::nonNull)
        .collect(Collectors.groupingBy(v -> v, TreeMap::new, Collectors.counting()));
  }

  /** 3954321 → "3.95 M"; 1250000000 → "$1.25 B". */
  static String compact(BigDecimal value, String prefix) {
    double v = value.doubleValue();
    if (v >= 1e9) return prefix + String.format(Locale.ROOT, "%.2f B", v / 1e9);
    if (v >= 1e6) return prefix + String.format(Locale.ROOT, "%.2f M", v / 1e6);
    if (v >= 1e3) return prefix + String.format(Locale.ROOT, "%.1f K", v / 1e3);
    return prefix + String.format(Locale.ROOT, "%.0f", v);
  }
}
