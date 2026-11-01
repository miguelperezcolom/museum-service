package io.mateu.museum.infra.in.rest;

/** An upload the caller got wrong: not a data URI, a type we do not keep, too big. A 400. */
public class InvalidDocumentException extends RuntimeException {
  public InvalidDocumentException(String message) {
    super(message);
  }
}
