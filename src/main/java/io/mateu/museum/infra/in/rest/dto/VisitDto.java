package io.mateu.museum.infra.in.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VisitDto(
    Long id,
    Long museumId,
    /** Read-only. */
    String museumName,
    LocalDate date,
    Integer visitors,
    Integer members,
    BigDecimal revenue,
    String url) {}
