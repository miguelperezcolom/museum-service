package io.mateu.museum.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** A piece of conservation work on one artwork — a card on the conservation board. */
@Entity
@Table(name = "conservation_task")
@Getter
@Setter
public class ConservationTask {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank private String title;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "artwork_id")
  private Artwork artwork;

  private String type;

  /** low · medium · high · urgent */
  private String priority;

  /** backlog · in_progress · review · done — the board's columns */
  private String status;

  private String assignee;
  private LocalDate dueDate;
  private Integer estimatedHours;

  @Column(length = 4000)
  private String notes;
}
