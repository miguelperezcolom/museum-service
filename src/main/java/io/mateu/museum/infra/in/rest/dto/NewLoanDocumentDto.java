package io.mateu.museum.infra.in.rest.dto;

/**
 * An upload: {@code content} is a data URI ({@code data:<type>[;name=<file>];base64,<bytes>} — what a
 * browser's FileReader, and so a Mateu {@code fileUpload} field, produces). {@code name} is optional:
 * without it the data URI's {@code name=} parameter is used, else a name from the type.
 */
public record NewLoanDocumentDto(String name, String content) {}
