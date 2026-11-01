package io.mateu.museum.infra.in.rest;

import io.mateu.museum.domain.LoanDocument;
import io.mateu.museum.infra.in.rest.dto.LoanDocumentDto;
import io.mateu.museum.infra.in.rest.dto.NewLoanDocumentDto;
import io.mateu.museum.infra.out.LoanDocumentRepository;
import io.mateu.museum.infra.out.LoanRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The documents of a loan — its agreement, condition reports, receipts. Uploaded as a data URI in a
 * JSON body (what a browser's FileReader gives a form, with no multipart endpoint to set up), listed
 * with an absolute URL each, served back with their own type and name.
 *
 * <p>Only document types are kept — PDF, images, plain text and CSV, Office and OpenDocument files —
 * and nothing that a browser would run (HTML, SVG, scripts): the bytes are served from this API's
 * origin. At most {@value #MAX_BYTES} bytes each and {@code museum.max-documents-per-loan} per loan,
 * since the database is in the heap.
 */
@RestController
@RequestMapping("/api/loans/{loanId}/documents")
@Tag(name = "Loan documents", description = "Files attached to a loan: agreement, condition reports, receipts")
public class LoanDocumentsController {

  static final int MAX_BYTES = 5 * 1024 * 1024;

  static final Set<String> TYPES = Set.of(
      "application/pdf",
      "image/png", "image/jpeg", "image/gif", "image/webp",
      "text/plain", "text/csv",
      "application/msword",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "application/vnd.ms-excel",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "application/vnd.ms-powerpoint",
      "application/vnd.openxmlformats-officedocument.presentationml.presentation",
      "application/vnd.oasis.opendocument.text",
      "application/vnd.oasis.opendocument.spreadsheet",
      "application/vnd.oasis.opendocument.presentation");

  private static final Map<String, String> EXTENSIONS = Map.of(
      "application/pdf", "pdf", "image/png", "png", "image/jpeg", "jpg", "image/gif", "gif",
      "image/webp", "webp", "text/plain", "txt", "text/csv", "csv");

  private final LoanRepository loans;
  private final LoanDocumentRepository documents;
  private final long maxPerLoan;

  public LoanDocumentsController(
      LoanRepository loans,
      LoanDocumentRepository documents,
      @Value("${museum.max-documents-per-loan:20}") long maxPerLoan) {
    this.loans = loans;
    this.documents = documents;
    this.maxPerLoan = maxPerLoan;
  }

  @Operation(summary = "The loan's documents, newest first")
  @GetMapping
  @Transactional(readOnly = true)
  public Map<String, Object> list(@PathVariable Long loanId) {
    requireLoan(loanId);
    List<LoanDocumentDto> content =
        documents.findByLoanIdOrderByUploadedAtDescIdDesc(loanId).stream().map(this::toDto).toList();
    // the same envelope as every collection of this API (content + totalElements)
    return Map.of("content", content, "totalElements", content.size());
  }

  @Operation(
      summary = "Upload a document",
      description = "Body: {name?, content} where content is a base64 data URI. PDF, images, text/CSV, "
          + "Office or OpenDocument; at most 5 MB. Requires the write API key.")
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  public ResponseEntity<LoanDocumentDto> upload(
      @PathVariable Long loanId, @RequestBody NewLoanDocumentDto body) {
    var loan = requireLoan(loanId);
    if (body == null || body.content() == null || body.content().isBlank()) {
      throw new InvalidDocumentException("Choose a file to upload (content is empty).");
    }
    var uri = DataUri.parse(body.content());
    if (!TYPES.contains(uri.contentType())) {
      throw new InvalidDocumentException("Files of type " + uri.contentType() + " are not accepted: "
          + "upload a PDF, an image, a text/CSV file or an Office/OpenDocument file.");
    }
    if (uri.bytes().length == 0) throw new InvalidDocumentException("The file is empty.");
    if (uri.bytes().length > MAX_BYTES) {
      throw new InvalidDocumentException("The file is " + megabytes(uri.bytes().length)
          + " MB: documents can be at most 5 MB.");
    }
    if (documents.countByLoanId(loanId) >= maxPerLoan) {
      throw new InvalidDocumentException("This loan already has " + maxPerLoan
          + " documents: delete one before uploading another.");
    }
    var doc = new LoanDocument();
    doc.setLoan(loan);
    doc.setName(nameOf(body.name(), uri));
    doc.setContentType(uri.contentType());
    doc.setSize(uri.bytes().length);
    doc.setUploadedAt(Instant.now());
    doc.setContent(uri.bytes());
    var saved = documents.save(doc);
    var dto = toDto(saved);
    return ResponseEntity.created(URI.create(dto.url())).body(dto);
  }

  @Operation(summary = "The document's bytes, with its type and name")
  @GetMapping("/{docId}")
  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> download(@PathVariable Long loanId, @PathVariable Long docId) {
    var doc = requireDocument(loanId, docId);
    // a PDF or an image opens in the browser tab; anything else is a download
    boolean inline = doc.getContentType().equals("application/pdf") || doc.getContentType().startsWith("image/");
    var disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
        .filename(doc.getName(), StandardCharsets.UTF_8)
        .build();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(doc.getContentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox")
        .body(doc.getContent());
  }

  @Operation(summary = "Delete a document", description = "Requires the write API key.")
  @DeleteMapping("/{docId}")
  @Transactional
  public ResponseEntity<Void> delete(@PathVariable Long loanId, @PathVariable Long docId) {
    documents.delete(requireDocument(loanId, docId));
    return ResponseEntity.noContent().build();
  }

  private io.mateu.museum.domain.Loan requireLoan(Long loanId) {
    return loans.findById(loanId).orElseThrow(() -> new NotFoundException("loans", loanId));
  }

  private LoanDocument requireDocument(Long loanId, Long docId) {
    requireLoan(loanId);
    return documents.findByIdAndLoanId(docId, loanId)
        .orElseThrow(() -> new NotFoundException("loans/" + loanId + "/documents", docId));
  }

  private LoanDocumentDto toDto(LoanDocument d) {
    // ABSOLUTE: a UI opens it in a new tab, from whatever origin it is served from
    String url = ServletUriComponentsBuilder.fromCurrentContextPath()
        .path("/api/loans/{loanId}/documents/{id}")
        .buildAndExpand(d.getLoan().getId(), d.getId())
        .toUriString();
    return new LoanDocumentDto(d.getId(), d.getLoan().getId(), d.getName(), d.getContentType(),
        d.getSize(), d.getUploadedAt(), url);
  }

  /** The name given, else the data URI's, else one from the type — never a path, never empty. */
  static String nameOf(String given, DataUri uri) {
    String name = given != null && !given.isBlank() ? given : uri.name();
    if (name != null) {
      name = name.replace('\\', '/');
      name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
    }
    if (name == null || name.isBlank()) {
      name = "document." + EXTENSIONS.getOrDefault(uri.contentType(), "bin");
    }
    return name.length() > 200 ? name.substring(0, 200) : name;
  }

  private static String megabytes(int bytes) {
    return String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0));
  }
}
