package io.mateu.museum.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/** A (synthetic) exhibition at one museum. It OWNS its list of works, so one PUT re-hangs the show. */
@Entity
@Table(name = "exhibition")
@Getter
@Setter
public class Exhibition {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank private String title;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "museum_id")
  private Museum museum;

  private LocalDate startDate;
  private LocalDate endDate;

  /** planned · open · closed */
  private String status;

  private String curator;

  @Column(length = 4000)
  private String description;

  @Column(precision = 15, scale = 2)
  private BigDecimal budget;

  private Integer visitors;

  @ManyToMany
  @JoinTable(
      name = "exhibition_artworks",
      joinColumns = @JoinColumn(name = "exhibition_id"),
      inverseJoinColumns = @JoinColumn(name = "artwork_id"))
  @BatchSize(size = 100)
  private Set<Artwork> artworks = new LinkedHashSet<>();
}
