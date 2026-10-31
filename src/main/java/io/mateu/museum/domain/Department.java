package io.mateu.museum.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** A curatorial department of one museum — European Paintings, Arms and Armor, Arts of Asia… */
@Entity
@Table(name = "department")
@Getter
@Setter
public class Department {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank private String name;

  /** The department's head — a synthetic name, like every person in this dataset. */
  private String head;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "museum_id")
  private Museum museum;
}
