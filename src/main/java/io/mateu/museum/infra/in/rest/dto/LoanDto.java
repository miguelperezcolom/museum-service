package io.mateu.museum.infra.in.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanDto(
    Long id,
    Long artworkId,
    /** Read-only. */
    String artworkTitle,
    /** Read-only. */
    String artworkImage,
    /** Defaults to the artwork's own museum when not given. */
    Long fromMuseumId,
    /** Read-only. */
    String fromMuseumName,
    Long toMuseumId,
    /** Read-only. */
    String toMuseumName,
    Long exhibitionId,
    /** Read-only. */
    String exhibitionTitle,
    LocalDate startDate,
    LocalDate endDate,
    /** requested · approved · in_transit · on_display · returned */
    String status,
    BigDecimal insuranceValue,
    String courier,
    String notes,
    String url) {}
