package io.mateu.museum;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** A loan's documents: uploaded as a data URI, listed with an absolute URL, served, deleted. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "museum.write-key=testkey")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LoanDocumentsTest {

  private static final String KEY = "X-Api-Key";
  private static final byte[] PDF = "%PDF-1.4\n% a tiny agreement\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

  @Autowired private MockMvc mvc;

  private static String dataUri(String type, String nameParam, byte[] bytes) {
    return "data:" + type + (nameParam == null ? "" : ";name=" + nameParam) + ";base64,"
        + Base64.getEncoder().encodeToString(bytes);
  }

  private static String body(String name, String content) {
    return "{" + (name == null ? "" : "\"name\":\"" + name + "\",") + "\"content\":\"" + content + "\"}";
  }

  private String upload(long loanId, String json) throws Exception {
    return mvc.perform(post("/api/loans/" + loanId + "/documents").header(KEY, "testkey")
            .contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
  }

  @Test
  @DisplayName("upload a PDF, list it, download the same bytes with its name, delete it")
  void lifecycle() throws Exception {
    // the name travels inside the data URI, as Mateu's file field stamps it
    String created = upload(1, body(null, dataUri("application/pdf", "Loan%20agreement.pdf", PDF)));
    Number id = JsonPath.read(created, "$.id");
    String url = JsonPath.read(created, "$.url");
    org.assertj.core.api.Assertions.assertThat(url).startsWith("http").endsWith("/api/loans/1/documents/" + id);

    mvc.perform(get("/api/loans/1/documents"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Loan agreement.pdf"))
        .andExpect(jsonPath("$.content[0].contentType").value("application/pdf"))
        .andExpect(jsonPath("$.content[0].size").value(PDF.length))
        .andExpect(jsonPath("$.content[0].uploadedAt").isNotEmpty())
        .andExpect(jsonPath("$.content[0].url").value(url));

    mvc.perform(get("/api/loans/1/documents/" + id))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/pdf"))
        .andExpect(header().string("Content-Disposition", containsString("inline")))
        .andExpect(header().string("Content-Disposition", containsString("Loan")))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(content().bytes(PDF));

    // reads are open, writes need the key
    mvc.perform(delete("/api/loans/1/documents/" + id)).andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/loans/1/documents/" + id).header(KEY, "testkey")).andExpect(status().isNoContent());
    mvc.perform(get("/api/loans/1/documents")).andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/api/loans/1/documents/" + id)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("an explicit name wins; without any, the name comes from the type; never a path")
  void names() throws Exception {
    upload(2, body("contract.pdf", dataUri("application/pdf", "ignored.pdf", PDF)));
    upload(2, body(null, dataUri("text/plain", null, "notes".getBytes(StandardCharsets.UTF_8))));
    upload(2, body("../../etc/passwd.txt", dataUri("text/plain", null, "x".getBytes(StandardCharsets.UTF_8))));
    mvc.perform(get("/api/loans/2/documents"))
        .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("contract.pdf", "document.txt", "passwd.txt")));
  }

  @Test
  @DisplayName("what is refused is a 400 that says why; an unknown loan is a 404; no key is a 401")
  void refusals() throws Exception {
    String html = body("x.html", dataUri("text/html", null, "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)));
    mvc.perform(post("/api/loans/1/documents").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON).content(html))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("text/html are not accepted")));
    String svg = body("x.svg", dataUri("image/svg+xml", null, "<svg/>".getBytes(StandardCharsets.UTF_8)));
    mvc.perform(post("/api/loans/1/documents").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON).content(svg))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/loans/1/documents").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content(body("x.pdf", "https://example.com/x.pdf")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("data URI")));
    mvc.perform(post("/api/loans/1/documents").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"\"}"))
        .andExpect(status().isBadRequest());
    byte[] big = new byte[5 * 1024 * 1024 + 1];
    mvc.perform(post("/api/loans/1/documents").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content(body("big.pdf", dataUri("application/pdf", null, big))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("at most 5 MB")));
    mvc.perform(post("/api/loans/999999/documents").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content(body("a.pdf", dataUri("application/pdf", null, PDF))))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/loans/1/documents").contentType(MediaType.APPLICATION_JSON)
            .content(body("a.pdf", dataUri("application/pdf", null, PDF))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("a file of exactly 5 MB is accepted (a ~7 MB JSON body) and comes back intact")
  void fiveMegabytes() throws Exception {
    byte[] five = new byte[5 * 1024 * 1024];
    Arrays.fill(five, (byte) 7);
    String created = upload(3, body("scan.pdf", dataUri("application/pdf", null, five)));
    Number id = JsonPath.read(created, "$.id");
    mvc.perform(get("/api/loans/3/documents/" + id)).andExpect(content().bytes(five));
  }

  @Test
  @DisplayName("a document can be deleted by its id alone (a list deleting its selected rows)")
  void deleteById() throws Exception {
    Number id = JsonPath.read(upload(4, body("a.pdf", dataUri("application/pdf", null, PDF))), "$.id");
    mvc.perform(delete("/api/loan-documents/" + id)).andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/loan-documents/" + id).header(KEY, "testkey")).andExpect(status().isNoContent());
    mvc.perform(delete("/api/loan-documents/" + id).header(KEY, "testkey")).andExpect(status().isNotFound());
    mvc.perform(get("/api/loans/4/documents")).andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("deleting a loan deletes its documents")
  void cascade() throws Exception {
    String created = mvc.perform(post("/api/loans").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("{\"artworkId\":1,\"status\":\"requested\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    Number loanId = JsonPath.read(created, "$.id");
    upload(loanId.longValue(), body("a.pdf", dataUri("application/pdf", null, PDF)));
    mvc.perform(delete("/api/loans/" + loanId).header(KEY, "testkey")).andExpect(status().is2xxSuccessful());
    mvc.perform(get("/api/loans/" + loanId + "/documents")).andExpect(status().isNotFound());
  }
}
