package io.mateu.museum.infra.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record ConservationTaskDto(
    Long id,
    @NotBlank(message = "must not be blank") String title,
    Long artworkId,
    /** Read-only. */
    String artworkTitle,
    /** Read-only. */
    String artworkImage,
    /** Read-only: the artwork's museum. */
    Long museumId,
    /** Read-only. */
    String museumName,
    String type,
    /** low · medium · high · urgent */
    String priority,
    /** backlog · in_progress · review · done */
    String status,
    String assignee,
    LocalDate dueDate,
    Integer estimatedHours,
    String notes,
    /** The card's place in its board column (ascending, decimal). Absent on a write = unchanged. */
    Double boardOrder,
    String url) {}
