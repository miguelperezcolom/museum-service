package io.mateu.museum;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The API's behaviour, pinned against the real seeded dataset: what the numbers are, what the
 * filters mean, what a write does to a reference, where every image comes from, and which failures
 * are the caller's fault rather than the server's.
 *
 * <p>The counts are the committed seed's. Re-running scripts/build-seed.py against the live museums
 * may pick other works; if it does, these numbers move with the seed, on purpose.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "museum.write-key=testkey")
class MuseumApiTest {

  private static final String KEY = "X-Api-Key";

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("the whole dataset is seeded at boot, references included")
  void seeded() throws Exception {
    mvc.perform(get("/api/museums")).andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(get("/api/departments")).andExpect(jsonPath("$.totalElements").value(41));
    mvc.perform(get("/api/artists")).andExpect(jsonPath("$.totalElements").value(211));
    mvc.perform(get("/api/artworks")).andExpect(jsonPath("$.totalElements").value(401));
    mvc.perform(get("/api/exhibitions")).andExpect(jsonPath("$.totalElements").value(18));
    mvc.perform(get("/api/loans")).andExpect(jsonPath("$.totalElements").value(53));
    mvc.perform(get("/api/conservation-tasks")).andExpect(jsonPath("$.totalElements").value(72));
    mvc.perform(get("/api/visits")).andExpect(jsonPath("$.totalElements").value(740));

    // a museum knows how much of the dataset is its own
    mvc.perform(get("/api/museums").param("code", "MET"))
        .andExpect(jsonPath("$.content[0].name").value("The Metropolitan Museum of Art"))
        .andExpect(jsonPath("$.content[0].artworkCount").value(132))
        .andExpect(jsonPath("$.content[0].latitude").value(40.7794));
  }

  @Test
  @DisplayName("every artwork links the museum's own images, its credit and its page")
  void everyImageIsCredited() throws Exception {
    mvc.perform(get("/api/artworks").param("size", "200"))
        .andExpect(jsonPath("$.content[*].imageSmall").value(everyItem(startsWith("https://"))))
        .andExpect(jsonPath("$.content[*].imageLarge").value(everyItem(startsWith("https://"))))
        .andExpect(jsonPath("$.content[*].imageCredit").value(everyItem(endsWith("· CC0"))))
        .andExpect(jsonPath("$.content[*].sourceUrl").value(everyItem(startsWith("https://"))));
    mvc.perform(get("/api/artworks").param("museumCode", "AIC").param("size", "1"))
        .andExpect(jsonPath("$.content[0].imageSmall").value(startsWith("https://www.artic.edu/iiif/2/")))
        .andExpect(jsonPath("$.content[0].imageCredit").value("Art Institute of Chicago · CC0"));
    mvc.perform(get("/api/credits"))
        .andExpect(jsonPath("$.length()").value(401))
        .andExpect(jsonPath("$[*].license").value(everyItem(is("CC0 1.0"))));
  }

  @Test
  @DisplayName("a page carries the total, so a client can render a pager without fetching everything")
  void pagedEnvelope() throws Exception {
    mvc.perform(get("/api/artworks").param("size", "25").param("page", "2"))
        .andExpect(jsonPath("$.content.length()").value(25))
        .andExpect(jsonPath("$.totalElements").value(401))
        .andExpect(jsonPath("$.totalPages").value(17))
        .andExpect(jsonPath("$.page").value(2));
  }

  @Test
  @DisplayName("a single-valued reference travels with its display name, so a table needs no second call")
  void referenceCarriesItsName() throws Exception {
    mvc.perform(get("/api/artworks").param("search", "The Bedroom"))
        .andExpect(jsonPath("$.content[0].artistName").value("Vincent van Gogh"))
        .andExpect(jsonPath("$.content[0].museumName").value("Art Institute of Chicago"))
        .andExpect(jsonPath("$.content[0].departmentName").value("Painting and Sculpture of Europe"));
  }

  @Test
  @DisplayName("one artist across museums: Van Gogh has works in Chicago and Cleveland")
  void artistsAreSharedAcrossMuseums() throws Exception {
    String body = mvc.perform(get("/api/artists").param("name", "van Gogh"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].artworkCount").value(3))
        .andExpect(jsonPath("$.content[0].lifespan").value("1853–1890"))
        .andReturn().getResponse().getContentAsString();
    String id = body.replaceAll("^.*?\"id\":(\\d+).*$", "$1");
    mvc.perform(get("/api/artworks").param("artistId", id).param("museumCode", "AIC"))
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  @DisplayName("search scans the collection's text fields")
  void search() throws Exception {
    mvc.perform(get("/api/artworks").param("search", "monet"))
        .andExpect(jsonPath("$.totalElements").value(6));
  }

  @Test
  @DisplayName("an EXACT filter is a pick; repeating it, or comma-joining it, is an OR")
  void exactAndMultiSelect() throws Exception {
    mvc.perform(get("/api/artworks").param("classification", "Painting"))
        .andExpect(jsonPath("$.totalElements").value(145));
    mvc.perform(get("/api/artworks").param("classification", "Painting").param("classification", "Photograph"))
        .andExpect(jsonPath("$.totalElements").value(171));
    mvc.perform(get("/api/artworks").param("classification", "Painting,Photograph"))
        .andExpect(jsonPath("$.totalElements").value(171));
    mvc.perform(get("/api/loans").param("status", "on_display,in_transit"))
        .andExpect(jsonPath("$.totalElements").value(20));
  }

  @Test
  @DisplayName("a boolean filter takes true or false, and nothing else")
  void booleanFilter() throws Exception {
    mvc.perform(get("/api/artworks").param("onView", "true"))
        .andExpect(jsonPath("$.totalElements").value(269));
    mvc.perform(get("/api/artworks").param("onView", "false"))
        .andExpect(jsonPath("$.totalElements").value(132));
    mvc.perform(get("/api/artworks").param("onView", "maybe")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("ranges are inclusive and compare as numbers or dates, not as text")
  void ranges() throws Exception {
    mvc.perform(get("/api/artworks").param("estimatedValue_from", "10000000"))
        .andExpect(jsonPath("$.totalElements").value(102));
    mvc.perform(get("/api/artworks").param("estimatedValue_from", "1000000").param("estimatedValue_to", "5000000"))
        .andExpect(jsonPath("$.totalElements").value(103));
    mvc.perform(get("/api/exhibitions").param("startDate_from", "2026-01-01"))
        .andExpect(jsonPath("$.totalElements").value(16));
  }

  @Test
  @DisplayName("a reference is filterable by id, also through another reference")
  void filterByReference() throws Exception {
    mvc.perform(get("/api/artworks").param("museumId", "1"))
        .andExpect(jsonPath("$.totalElements").value(132));
    mvc.perform(get("/api/visits").param("museumId", "1"))
        .andExpect(jsonPath("$.totalElements").value(261));
    // a task belongs to a museum through its artwork
    mvc.perform(get("/api/conservation-tasks").param("museumId", "1"))
        .andExpect(jsonPath("$.totalElements").value(24));
  }

  @Test
  @DisplayName("a table sorts by the name it shows, not only by entity properties")
  void sortByDenormalisedName() throws Exception {
    mvc.perform(get("/api/artworks").param("sort", "museumName,desc").param("size", "1"))
        .andExpect(jsonPath("$.content[0].museumName").value("The Metropolitan Museum of Art"));
    mvc.perform(get("/api/artworks").param("sort", "nonsense")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("an undeclared filter is a 400, never a silently unfiltered page")
  void unknownFilterIsRejected() throws Exception {
    mvc.perform(get("/api/artworks").param("museum", "Met"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("Unknown filter")));
  }

  @Test
  @DisplayName("an unparseable filter value is the caller's fault, not a server error")
  void unparseableFilterValue() throws Exception {
    mvc.perform(get("/api/artworks").param("beginYear_from", "old")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("writing without the key is refused; reading never needs it")
  void writeNeedsTheKey() throws Exception {
    mvc.perform(post("/api/artists").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/artists").header(KEY, "wrong").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"X\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/artworks")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("a loan request fills in where the work leaves from and where it goes")
  // writes: the next test gets a freshly seeded database
  @DirtiesContext
  void loanDefaults() throws Exception {
    // what a form sends: every value a string, the unset ones blank
    mvc.perform(post("/api/loans").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"artworkId":"1","exhibitionId":"9","startDate":"2027-03-01","endDate":"2027-06-30",
                 "status":"","insuranceValue":"12000000","courier":"","notes":"",
                 "fromMuseumId":"","toMuseumId":""}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("requested"))
        .andExpect(jsonPath("$.fromMuseumName").value("The Metropolitan Museum of Art"))
        .andExpect(jsonPath("$.toMuseumId").value(2))
        .andExpect(jsonPath("$.exhibitionTitle").isNotEmpty());
    mvc.perform(get("/api/loans").param("status", "requested"))
        .andExpect(jsonPath("$.totalElements").value(13));
  }

  @Test
  @DisplayName("a full write cycle: create, read back, move a card on the board, delete")
  // writes: the next test gets a freshly seeded database
  @DirtiesContext
  void writeCycle() throws Exception {
    String created = mvc.perform(post("/api/conservation-tasks").header(KEY, "testkey")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Condition report","artworkId":1,"type":"Condition report",
                 "priority":"high","assignee":"Ana López","dueDate":"2026-12-01","estimatedHours":4}
                """))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.status").value("backlog"))
        .andExpect(jsonPath("$.museumName").value("The Metropolitan Museum of Art"))
        .andReturn().getResponse().getContentAsString();
    String id = created.replaceAll("^\\{\"id\":(\\d+).*$", "$1");

    mvc.perform(put("/api/conservation-tasks/" + id).header(KEY, "testkey")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Condition report","artworkId":1,"type":"Condition report",
                 "priority":"high","status":"in_progress","assignee":"Ana López","dueDate":"2026-12-01"}
                """))
        .andExpect(jsonPath("$.status").value("in_progress"));

    mvc.perform(delete("/api/conservation-tasks/" + id).header(KEY, "testkey"))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/conservation-tasks/" + id)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("editing an artwork never blanks the museum's image, credit or link")
  // writes: the next test gets a freshly seeded database
  @DirtiesContext
  void provenanceSurvivesAnEdit() throws Exception {
    mvc.perform(put("/api/artworks/1").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Saint John on Patmos (retitled)","museumId":1,"onView":"false",
                 "estimatedValue":"9900000","imageSmall":"","imageCredit":""}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Saint John on Patmos (retitled)"))
        .andExpect(jsonPath("$.onView").value(false))
        .andExpect(jsonPath("$.imageSmall").value(startsWith("https://images.metmuseum.org/")))
        .andExpect(jsonPath("$.imageCredit").value("The Metropolitan Museum of Art · CC0"));
  }

  @Test
  @DisplayName("a write pointing at a row that does not exist is the caller's 400")
  void badReference() throws Exception {
    mvc.perform(post("/api/loans").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("{\"artworkId\":99999}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("deleting a work still on a loan is a 409, and nothing is lost")
  void deletingAReferencedRowIsAConflict() throws Exception {
    mvc.perform(delete("/api/artworks/156").header(KEY, "testkey")).andExpect(status().isConflict());
    mvc.perform(get("/api/artworks/156")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("the dashboard figures and the collection tree come from the live tables")
  void statsAndTree() throws Exception {
    mvc.perform(get("/api/stats"))
        .andExpect(jsonPath("$.artworks").value(401))
        .andExpect(jsonPath("$.openExhibitions").value(6))
        .andExpect(jsonPath("$.perMuseum.length()").value(3))
        .andExpect(jsonPath("$.visitorsYtdLabel").value(endsWith(" M")))
        .andExpect(jsonPath("$.visitsByMonth.length()").value(10));
    mvc.perform(get("/api/collection-tree"))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[0].kind").value("Museum"))
        .andExpect(jsonPath("$.content[0].artworks").value(132))
        .andExpect(jsonPath("$.content[0].children[0].kind").value("Department"))
        .andExpect(jsonPath("$.content[0].children[0].children[0].kind").value("Gallery"));
  }
}
