package io.mateu.museum.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * A file attached to a loan — its agreement, a condition report, a courier's receipt. The bytes live
 * in the database like everything else here, so they are forgotten on restart with the rest of the
 * dataset.
 */
@Entity
@Table(name = "loan_document")
@Getter
@Setter
public class LoanDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // deleting a loan takes its documents with it (no 409 for an attachment)
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "loan_id")
  @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
  private Loan loan;

  private String name;
  private String contentType;
  private long size;
  private Instant uploadedAt;

  @Lob
  @Basic(fetch = FetchType.LAZY)
  @Column(columnDefinition = "BLOB")
  private byte[] content;
}
