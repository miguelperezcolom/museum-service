package io.mateu.museum.infra.in.rest.query;

/**
 * How one declared filter compares. Making this explicit per field, instead of inferring it from the
 * column type, is what keeps {@code ?status=open} from also matching "reopened" — an enum-like column
 * is a pick, not a prefix, and only the API author knows which is which.
 */
public enum FilterKind {

  /** Case-insensitive containment. For prose-ish columns: a name, a model, a terrain. */
  TEXT,

  /** Case-insensitive equality. For a closed set of values: status, priority, museum code. */
  EXACT,

  /** Equality, plus the inclusive {@code <field>_from} / {@code <field>_to} range. */
  NUMBER,

  /** Same as NUMBER over an ISO-8601 date. */
  DATE,

  /** Equality on a true/false column — {@code ?onView=true}. */
  BOOLEAN,

  /** Equality on the id of a referenced row — {@code ?museumId=1}. */
  REF
}
