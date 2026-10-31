package io.mateu.museum.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Someone whose work is in the collections. One row per artist across all three museums — Van Gogh
 * is one artist with works in two of them, which is what makes "by artist" a real join.
 */
@Entity
@Table(name = "artist")
@Getter
@Setter
public class Artist {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank private String name;
  private String nationality;
  private Integer birthYear;
  private Integer deathYear;

  @Column(length = 4000)
  private String bio;

  /** A work BY the artist (not a portrait — CC0 portraits are rare), linked from its museum. */
  @Column(length = 1000)
  private String image;

  private String imageCredit;
}
