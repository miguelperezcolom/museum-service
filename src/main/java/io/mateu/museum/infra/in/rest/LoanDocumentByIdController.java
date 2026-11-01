package io.mateu.museum.infra.in.rest;

import io.mateu.museum.infra.out.LoanDocumentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * A document addressed by its own id alone: a list of documents that deletes its selected rows has
 * each row's id at hand, not the loan it belongs to.
 */
@RestController
@Tag(name = "Loan documents")
public class LoanDocumentByIdController {

  private final LoanDocumentRepository documents;

  public LoanDocumentByIdController(LoanDocumentRepository documents) {
    this.documents = documents;
  }

  @Operation(summary = "Delete a document by its id", description = "Requires the write API key.")
  @DeleteMapping("/api/loan-documents/{docId}")
  @Transactional
  public ResponseEntity<Void> delete(@PathVariable Long docId) {
    var doc = documents.findById(docId).orElseThrow(() -> new NotFoundException("loan-documents", docId));
    documents.delete(doc);
    return ResponseEntity.noContent().build();
  }
}
