package io.mateu.museum.infra.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record DepartmentDto(
    Long id,
    @NotBlank(message = "must not be blank") String name,
    String head,
    Long museumId,
    /** Read-only: the museum's name, so a table renders without a second request. */
    String museumName,
    /** Read-only, computed. */
    Long artworkCount,
    String url) {}
