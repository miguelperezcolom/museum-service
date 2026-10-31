package io.mateu.museum.infra.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExhibitionDto(
    Long id,
    @NotBlank(message = "must not be blank") String title,
    Long museumId,
    /** Read-only. */
    String museumName,
    LocalDate startDate,
    LocalDate endDate,
    /** planned · open · closed */
    String status,
    String curator,
    String description,
    BigDecimal budget,
    Integer visitors,
    /** Owned here: a PUT re-hangs the show. */
    List<Long> artworkIds,
    /** Read-only. */
    Integer artworkCount,
    /** Read-only: the first work's image, as the exhibition's poster. */
    String image,
    /** Read-only. */
    String imageCredit,
    String url) {}
