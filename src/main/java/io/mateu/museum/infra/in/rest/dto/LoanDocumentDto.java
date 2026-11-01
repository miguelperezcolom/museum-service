package io.mateu.museum.infra.in.rest.dto;

import java.time.Instant;

/** A loan's document as listed: what it is, and the absolute URL its bytes are served at. */
public record LoanDocumentDto(
    Long id, Long loanId, String name, String contentType, long size, Instant uploadedAt, String url) {}
