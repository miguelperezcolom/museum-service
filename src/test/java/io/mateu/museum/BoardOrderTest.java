package io.mateu.museum;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The conservation board keeps the order its cards were dragged into (Mateu Kanban orderField). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "museum.write-key=testkey")
class BoardOrderTest {

  private static final String KEY = "X-Api-Key";
  private final ObjectMapper json = new ObjectMapper();

  @Autowired private MockMvc mvc;

  private JsonNode column(String status) throws Exception {
    String body = mvc.perform(get("/api/conservation-tasks").param("status", status)
            .param("sort", "boardOrder").param("size", "500"))
        .andReturn().getResponse().getContentAsString();
    return json.readTree(body).get("content");
  }

  @Test
  @DisplayName("every seeded column is numbered 1000, 2000, … by due date, and sorts by boardOrder")
  void seededColumnsAreNumbered() throws Exception {
    for (String status : List.of("backlog", "in_progress", "review", "done")) {
      JsonNode cards = column(status);
      assertThat(cards.size()).isPositive();
      List<Double> orders = new ArrayList<>();
      for (int i = 0; i < cards.size(); i++) {
        orders.add(cards.get(i).get("boardOrder").asDouble());
        org.assertj.core.api.Assertions.assertThat(cards.get(i).get("boardOrder").asDouble())
            .isEqualTo((i + 1) * 1000.0);
      }
      String previous = "";
      for (JsonNode c : cards) {
        String due = c.get("dueDate").asText();
        org.assertj.core.api.Assertions.assertThat(due.compareTo(previous)).isGreaterThanOrEqualTo(0);
        previous = due;
      }
    }
  }

  @Test
  @DisplayName("a card moved between two neighbours keeps its decimal place; an edit without an order keeps it")
  @DirtiesContext
  void aMovedCardKeepsItsPlace() throws Exception {
    JsonNode backlog = column("backlog");
    JsonNode card = backlog.get(0);
    long id = card.get("id").asLong();
    // the board's move: the whole record, with the new column and the midpoint of the new neighbours
    var moved = (com.fasterxml.jackson.databind.node.ObjectNode) card.deepCopy();
    moved.put("status", "review");
    moved.put("boardOrder", 1500.5);
    mvc.perform(put("/api/conservation-tasks/" + id).header(KEY, "testkey")
            .contentType(MediaType.APPLICATION_JSON).content(moved.toString()))
        .andExpect(jsonPath("$.status").value("review"))
        .andExpect(jsonPath("$.boardOrder").value(1500.5));

    JsonNode review = column("review");
    org.assertj.core.api.Assertions.assertThat(review.get(1).get("id").asLong()).isEqualTo(id);

    // an edit form that sends no boardOrder leaves the card where it is
    var edited = (com.fasterxml.jackson.databind.node.ObjectNode) moved.deepCopy();
    edited.remove("boardOrder");
    edited.put("notes", "checked");
    mvc.perform(put("/api/conservation-tasks/" + id).header(KEY, "testkey")
            .contentType(MediaType.APPLICATION_JSON).content(edited.toString()))
        .andExpect(jsonPath("$.boardOrder").value(1500.5));
  }

  @Test
  @DisplayName("a new card goes to the end of its column")
  @DirtiesContext
  void aNewCardGoesLast() throws Exception {
    JsonNode backlog = column("backlog");
    double last = backlog.get(backlog.size() - 1).get("boardOrder").asDouble();
    mvc.perform(post("/api/conservation-tasks").header(KEY, "testkey")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Condition report\",\"artworkId\":1,\"priority\":\"low\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.boardOrder").value(last + 1000));
  }

  private static org.assertj.core.api.AbstractIntegerAssert<?> assertThat(int v) {
    return org.assertj.core.api.Assertions.assertThat(v);
  }
}
