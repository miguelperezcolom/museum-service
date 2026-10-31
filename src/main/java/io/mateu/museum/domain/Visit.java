package io.mateu.museum.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** How many people came to one museum on one (open) day — synthetic, for dashboards and charts. */
@Entity
@Table(name = "visit")
@Getter
@Setter
public class Visit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "museum_id")
  private Museum museum;

  private LocalDate date;
  private Integer visitors;
  private Integer members;

  @Column(precision = 12, scale = 2)
  private BigDecimal revenue;
}
