package io.mateu.museum.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * A public-domain work held by one of the museums. The record comes from the museum's open-access
 * API (CC0); the images are the museum's own, linked rather than copied. The estimated value is
 * synthetic — museums do not publish one — so that money fields and ranges have something to show.
 */
@Entity
@Table(name = "artwork")
@Getter
@Setter
public class Artwork {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Column(length = 1000)
  private String title;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "artist_id")
  private Artist artist;

  /** How the museum attributes it: the artist's name, or a culture when the maker is unknown. */
  private String artistDisplay;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "museum_id")
  private Museum museum;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id")
  private Department department;

  private String classification;
  private String objectType;

  @Column(length = 1000)
  private String medium;

  private String objectDate;
  private Integer beginYear;
  private Integer endYear;

  @Column(length = 2000)
  private String dimensions;

  @Column(precision = 10, scale = 2)
  private BigDecimal heightCm;

  @Column(precision = 10, scale = 2)
  private BigDecimal widthCm;

  @Column(precision = 10, scale = 2)
  private BigDecimal depthCm;

  private String culture;
  private Boolean onView;
  private String gallery;
  private Integer acquisitionYear;
  private String accessionNumber;

  @Column(length = 2000)
  private String creditLine;

  @Column(precision = 15, scale = 2)
  private BigDecimal estimatedValue;

  /** Comma-joined, so it filters by containment — `?tags=portraits`. */
  @Column(length = 1000)
  private String tags;

  @Column(length = 8000)
  private String description;

  private Boolean highlight;

  @Column(length = 1000)
  private String imageSmall;

  @Column(length = 1000)
  private String imageLarge;

  private String imageCredit;

  @Column(length = 1000)
  private String sourceUrl;

  /** The exhibitions showing it — owned by Exhibition, read here to filter by. */
  @ManyToMany(mappedBy = "artworks")
  private java.util.Set<Exhibition> exhibitions = new java.util.LinkedHashSet<>();

  /** The museum's own id for the work, so a record can always be traced back to its source. */
  private String sourceId;
}
