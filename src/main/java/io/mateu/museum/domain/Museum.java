package io.mateu.museum.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/** One of the museums. Its departments, artworks, exhibitions and visits point at it. */
@Entity
@Table(name = "museum")
@Getter
@Setter
public class Museum {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** MET, SMK, CMA — short and stable, what a filter or a chart legend uses. */
  @NotBlank private String code;

  @NotBlank private String name;
  private String shortName;
  private String city;
  private String country;

  @Column(precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(precision = 9, scale = 6)
  private BigDecimal longitude;

  private Integer founded;
  private String website;

  @Column(length = 2000)
  private String description;

  @Column(length = 1000)
  private String image;

  private String imageCredit;

  @OneToMany(mappedBy = "museum")
  @BatchSize(size = 100)
  private Set<Department> departments = new LinkedHashSet<>();
}
