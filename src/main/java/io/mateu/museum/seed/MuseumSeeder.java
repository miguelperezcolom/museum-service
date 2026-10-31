package io.mateu.museum.seed;

import io.mateu.museum.domain.*;
import io.mateu.museum.infra.out.*;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the bundled dataset (src/main/resources/seed, written by scripts/build-seed.py) into the
 * database at boot.
 *
 * <p>The database is H2 in memory, so this runs on every start and every restart is a clean slate —
 * a public, writable demo that anybody can scribble on had better be able to forget.
 *
 * <p>The seed files give every row an id and refer to other rows by those ids. They are NOT reused
 * as primary keys — the database assigns its own — but the files are loaded in dependency order and
 * each seed id is remembered against the row it became, so references resolve whatever the database
 * numbers them. (In a fresh database they come out equal anyway.)
 */
@Component
public class MuseumSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(MuseumSeeder.class);

  private final JsonMapper json = JsonMapper.builder().build();

  private final MuseumRepository museums;
  private final DepartmentRepository departments;
  private final ArtistRepository artists;
  private final ArtworkRepository artworks;
  private final ExhibitionRepository exhibitions;
  private final LoanRepository loans;
  private final ConservationTaskRepository tasks;
  private final VisitRepository visits;

  private final Map<Long, Museum> museumById = new HashMap<>();
  private final Map<Long, Department> departmentById = new HashMap<>();
  private final Map<Long, Artist> artistById = new HashMap<>();
  private final Map<Long, Artwork> artworkById = new HashMap<>();
  private final Map<Long, Exhibition> exhibitionById = new HashMap<>();

  public MuseumSeeder(
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

  @Override
  @Transactional
  public void run(ApplicationArguments args) throws IOException {
    if (museums.count() > 0) {
      log.info("Database already seeded; skipping");
      return;
    }
    long started = System.currentTimeMillis();

    forEach("museums", n -> museumById.put(id(n), museums.save(museum(n))));
    forEach("departments", n -> departmentById.put(id(n), departments.save(department(n))));
    forEach("artists", n -> artistById.put(id(n), artists.save(artist(n))));
    forEach("artworks", n -> artworkById.put(id(n), artworks.save(artwork(n))));
    forEach("exhibitions", n -> exhibitionById.put(id(n), exhibitions.save(exhibition(n))));
    forEach("loans", n -> loans.save(loan(n)));
    forEach("conservation-tasks", n -> tasks.save(task(n)));
    forEach("visits", n -> visits.save(visit(n)));

    log.info(
        "Seeded {} museums, {} departments, {} artists, {} artworks, {} exhibitions, {} loans,"
            + " {} conservation tasks, {} visit days in {} ms",
        museums.count(), departments.count(), artists.count(), artworks.count(),
        exhibitions.count(), loans.count(), tasks.count(), visits.count(),
        System.currentTimeMillis() - started);
  }

  private Museum museum(JsonNode n) {
    Museum m = new Museum();
    m.setCode(text(n, "code"));
    m.setName(text(n, "name"));
    m.setShortName(text(n, "shortName"));
    m.setCity(text(n, "city"));
    m.setCountry(text(n, "country"));
    m.setLatitude(decimal(n, "latitude"));
    m.setLongitude(decimal(n, "longitude"));
    m.setFounded(integer(n, "founded"));
    m.setWebsite(text(n, "website"));
    m.setDescription(text(n, "description"));
    m.setImage(text(n, "image"));
    m.setImageCredit(text(n, "imageCredit"));
    return m;
  }

  private Department department(JsonNode n) {
    Department d = new Department();
    d.setName(text(n, "name"));
    d.setHead(text(n, "head"));
    d.setMuseum(museumById.get(ref(n, "museumId")));
    return d;
  }

  private Artist artist(JsonNode n) {
    Artist a = new Artist();
    a.setName(text(n, "name"));
    a.setNationality(text(n, "nationality"));
    a.setBirthYear(integer(n, "birthYear"));
    a.setDeathYear(integer(n, "deathYear"));
    a.setBio(text(n, "bio"));
    a.setImage(text(n, "image"));
    a.setImageCredit(text(n, "imageCredit"));
    return a;
  }

  private Artwork artwork(JsonNode n) {
    Artwork a = new Artwork();
    a.setTitle(text(n, "title"));
    a.setArtist(artistById.get(ref(n, "artistId")));
    a.setArtistDisplay(text(n, "artistDisplay"));
    a.setMuseum(museumById.get(ref(n, "museumId")));
    a.setDepartment(departmentById.get(ref(n, "departmentId")));
    a.setClassification(text(n, "classification"));
    a.setObjectType(text(n, "objectType"));
    a.setMedium(text(n, "medium"));
    a.setObjectDate(text(n, "objectDate"));
    a.setBeginYear(integer(n, "beginYear"));
    a.setEndYear(integer(n, "endYear"));
    a.setDimensions(text(n, "dimensions"));
    a.setHeightCm(decimal(n, "heightCm"));
    a.setWidthCm(decimal(n, "widthCm"));
    a.setDepthCm(decimal(n, "depthCm"));
    a.setCulture(text(n, "culture"));
    a.setOnView(bool(n, "onView"));
    a.setGallery(text(n, "gallery"));
    a.setAcquisitionYear(integer(n, "acquisitionYear"));
    a.setAccessionNumber(text(n, "accessionNumber"));
    a.setCreditLine(text(n, "creditLine"));
    a.setEstimatedValue(decimal(n, "estimatedValue"));
    a.setTags(text(n, "tags"));
    a.setDescription(text(n, "description"));
    a.setHighlight(bool(n, "highlight"));
    a.setImageSmall(text(n, "imageSmall"));
    a.setImageLarge(text(n, "imageLarge"));
    a.setImageCredit(text(n, "imageCredit"));
    a.setSourceUrl(text(n, "sourceUrl"));
    a.setSourceId(text(n, "sourceId"));
    return a;
  }

  private Exhibition exhibition(JsonNode n) {
    Exhibition e = new Exhibition();
    e.setTitle(text(n, "title"));
    e.setMuseum(museumById.get(ref(n, "museumId")));
    e.setStartDate(date(n, "startDate"));
    e.setEndDate(date(n, "endDate"));
    e.setStatus(text(n, "status"));
    e.setCurator(text(n, "curator"));
    e.setDescription(text(n, "description"));
    e.setBudget(decimal(n, "budget"));
    e.setVisitors(integer(n, "visitors"));
    n.path("artworkIds").forEach(id -> {
      Artwork a = artworkById.get(id.asLong());
      if (a != null) e.getArtworks().add(a);
    });
    return e;
  }

  private Loan loan(JsonNode n) {
    Loan l = new Loan();
    l.setArtwork(artworkById.get(ref(n, "artworkId")));
    l.setFromMuseum(museumById.get(ref(n, "fromMuseumId")));
    l.setToMuseum(museumById.get(ref(n, "toMuseumId")));
    l.setExhibition(exhibitionById.get(ref(n, "exhibitionId")));
    l.setStartDate(date(n, "startDate"));
    l.setEndDate(date(n, "endDate"));
    l.setStatus(text(n, "status"));
    l.setInsuranceValue(decimal(n, "insuranceValue"));
    l.setCourier(text(n, "courier"));
    l.setNotes(text(n, "notes"));
    return l;
  }

  private ConservationTask task(JsonNode n) {
    ConservationTask t = new ConservationTask();
    t.setTitle(text(n, "title"));
    t.setArtwork(artworkById.get(ref(n, "artworkId")));
    t.setType(text(n, "type"));
    t.setPriority(text(n, "priority"));
    t.setStatus(text(n, "status"));
    t.setAssignee(text(n, "assignee"));
    t.setDueDate(date(n, "dueDate"));
    t.setEstimatedHours(integer(n, "estimatedHours"));
    t.setNotes(text(n, "notes"));
    return t;
  }

  private Visit visit(JsonNode n) {
    Visit v = new Visit();
    v.setMuseum(museumById.get(ref(n, "museumId")));
    v.setDate(date(n, "date"));
    v.setVisitors(integer(n, "visitors"));
    v.setMembers(integer(n, "members"));
    v.setRevenue(decimal(n, "revenue"));
    return v;
  }

  // ── reading ─────────────────────────────────────────────────────────────────

  private void forEach(String resource, Consumer<JsonNode> visitor) throws IOException {
    try (InputStream in = new ClassPathResource("seed/" + resource + ".json").getInputStream()) {
      json.readTree(in).forEach(visitor);
    }
  }

  private static Long id(JsonNode n) {
    return n.path("id").asLong();
  }

  private static Long ref(JsonNode n, String field) {
    JsonNode v = n.get(field);
    return v == null || v.isNull() ? null : v.asLong();
  }

  private static String text(JsonNode n, String field) {
    JsonNode v = n.get(field);
    if (v == null || v.isNull()) return null;
    String s = v.asString();
    return s == null || s.isBlank() ? null : s;
  }

  private static Integer integer(JsonNode n, String field) {
    JsonNode v = n.get(field);
    return v == null || v.isNull() || !v.isNumber() ? null : v.asInt();
  }

  private static BigDecimal decimal(JsonNode n, String field) {
    JsonNode v = n.get(field);
    return v == null || v.isNull() || !v.isNumber() ? null : v.decimalValue();
  }

  private static Boolean bool(JsonNode n, String field) {
    JsonNode v = n.get(field);
    return v == null || v.isNull() ? null : v.asBoolean();
  }

  private static LocalDate date(JsonNode n, String field) {
    String s = text(n, field);
    return s == null ? null : LocalDate.parse(s);
  }
}
