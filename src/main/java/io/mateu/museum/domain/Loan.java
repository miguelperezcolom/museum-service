package io.mateu.museum.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * A work lent from one museum to another (usually for one of its exhibitions). Its status walks a
 * workflow: requested → approved → in_transit → on_display → returned.
 */
@Entity
@Table(name = "loan")
@Getter
@Setter
public class Loan {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "artwork_id")
  private Artwork artwork;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "from_museum_id")
  private Museum fromMuseum;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "to_museum_id")
  private Museum toMuseum;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "exhibition_id")
  private Exhibition exhibition;

  private LocalDate startDate;
  private LocalDate endDate;
  private String status;

  @Column(precision = 15, scale = 2)
  private BigDecimal insuranceValue;

  private String courier;

  @Column(length = 2000)
  private String notes;
}
