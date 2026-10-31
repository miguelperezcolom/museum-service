package io.mateu.museum.infra.in.rest.query;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.MultiValueMap;

/**
 * Turns the request's query parameters into a JPA {@link Specification}, against what the collection
 * declared in its {@link QuerySpec}.
 *
 * <p>Every condition is evaluated by the DATABASE. That is the whole point of a real backend rather
 * than a static file: a client can ask for the paintings on view at the Met worth over a million and
 * get those rows back instead of the whole collection to sift through.
 *
 * <p>Conditions combine with AND; a parameter repeated (<code>?status=open&amp;status=planned</code>)
 * is an OR between its values, which is what a multi-select means.
 */
public final class SpecificationBuilder {

  /** Reserved parameters that steer the page rather than filter it. */
  public static final Set<String> RESERVED = Set.of("search", "page", "size", "sort");

  /** More than any multi-select sends; see {@link TooManyValuesException}. */
  private static final int MAX_VALUES = 50;

  private static final String FROM = "_from";
  private static final String TO = "_to";

  private SpecificationBuilder() {}

  public static <T> Specification<T> build(MultiValueMap<String, String> params, QuerySpec spec) {
    rejectUnknown(params, spec);
    rejectTooMany(params, spec);
    return (root, query, cb) -> {
      List<Predicate> and = new ArrayList<>();

      String search = first(params, "search");
      if (search != null && !search.isBlank() && !spec.searchPaths().isEmpty()) {
        String needle = "%" + search.trim().toLowerCase() + "%";
        List<Predicate> or = spec.searchPaths().stream()
            .map(path -> cb.like(cb.lower(SpecificationBuilder.<String>path(root, path)), needle))
            .map(Predicate.class::cast)
            .toList();
        and.add(cb.or(or.toArray(Predicate[]::new)));
      }

      for (Map.Entry<String, QuerySpec.Filter> declared : spec.filters().entrySet()) {
        String name = declared.getKey();
        QuerySpec.Filter filter = declared.getValue();

        List<String> values = values(params, name, filter.kind());
        if (!values.isEmpty()) {
          List<Predicate> or = values.stream()
              .map(value -> equality(root, cb, filter, value))
              .toList();
          and.add(or.size() == 1 ? or.get(0) : cb.or(or.toArray(Predicate[]::new)));
        }

        if (filter.kind() == FilterKind.NUMBER || filter.kind() == FilterKind.DATE) {
          Predicate range = range(root, cb, filter, first(params, name + FROM), first(params, name + TO));
          if (range != null) and.add(range);
        }
      }

      // a filter through a to-many join may meet the same row twice
      if (!root.getJoins().isEmpty() && query != null) query.distinct(true);
      return and.isEmpty() ? cb.conjunction() : cb.and(and.toArray(Predicate[]::new));
    };
  }

  /**
   * A filter the collection does not declare is an error, not a no-op. Answering 200 with an
   * unfiltered page to <code>?museum=Met</code> (for a field actually called {@code museumId})
   * is the kind of bug a caller finds in production, so it is a 400 here instead.
   */
  private static void rejectUnknown(MultiValueMap<String, String> params, QuerySpec spec) {
    Set<String> unknown = new LinkedHashSet<>();
    for (String name : params.keySet()) {
      if (RESERVED.contains(name)) continue;
      String bare = name.endsWith(FROM) || name.endsWith(TO)
          ? name.substring(0, name.lastIndexOf('_'))
          : name;
      if (!spec.knows(bare)) unknown.add(name);
    }
    if (!unknown.isEmpty()) {
      throw new UnknownFilterException(unknown, spec.filters().keySet());
    }
  }

  /** Checked up front, not while building predicates, so the 400 never reaches the database. */
  private static void rejectTooMany(MultiValueMap<String, String> params, QuerySpec spec) {
    spec.filters().forEach((name, filter) -> {
      int given = values(params, name, filter.kind()).size();
      if (given > MAX_VALUES) throw new TooManyValuesException(name, given, MAX_VALUES);
    });
  }

  private static <T> Predicate equality(
      jakarta.persistence.criteria.Root<T> root,
      jakarta.persistence.criteria.CriteriaBuilder cb,
      QuerySpec.Filter filter,
      String value) {
    return switch (filter.kind()) {
      case TEXT -> cb.like(cb.lower(path(root, filter.path())), "%" + value.toLowerCase() + "%");
      case EXACT -> cb.equal(cb.lower(path(root, filter.path())), value.toLowerCase());
      case REF -> cb.equal(path(root, filter.path()), Long.valueOf(value));
      case NUMBER -> cb.equal(path(root, filter.path()), number(root, filter.path(), value));
      case DATE -> cb.equal(path(root, filter.path()), date(value));
      case BOOLEAN -> cb.equal(path(root, filter.path()), bool(value));
    };
  }

  private static <T> Predicate range(
      jakarta.persistence.criteria.Root<T> root,
      jakarta.persistence.criteria.CriteriaBuilder cb,
      QuerySpec.Filter filter,
      String from,
      String to) {
    if (blank(from) && blank(to)) return null;
    if (filter.kind() == FilterKind.DATE) {
      Path<LocalDate> path = path(root, filter.path());
      if (blank(to)) return cb.greaterThanOrEqualTo(path, date(from));
      if (blank(from)) return cb.lessThanOrEqualTo(path, date(to));
      return cb.between(path, date(from), date(to));
    }
    Path<BigDecimal> path = path(root, filter.path());
    if (blank(to)) return cb.ge(path, new BigDecimal(from));
    if (blank(from)) return cb.le(path, new BigDecimal(to));
    return cb.and(cb.ge(path, new BigDecimal(from)), cb.le(path, new BigDecimal(to)));
  }

  /**
   * The value has to arrive as the column's own type: comparing a {@code Long} column against a
   * {@code BigDecimal} is a type error in the criteria API, not a widening.
   */
  private static Object number(Path<?> root, String property, String value) {
    Class<?> type = path(root, property).getJavaType();
    if (type == Integer.class || type == int.class) return Integer.valueOf(value);
    if (type == Long.class || type == long.class) return Long.valueOf(value);
    return new BigDecimal(value);
  }

  /** Only the two words a checkbox can mean; anything else is the caller's mistake, not a false. */
  private static Boolean bool(String value) {
    if (value.equalsIgnoreCase("true")) return Boolean.TRUE;
    if (value.equalsIgnoreCase("false")) return Boolean.FALSE;
    throw new IllegalArgumentException("Not a boolean: " + value);
  }

  private static LocalDate date(String value) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Not an ISO-8601 date: " + value, e);
    }
  }

  /**
   * Walks a dotted property path, joining as it goes, so {@code museum.id} is filterable — and so is
   * {@code exhibitions.id}: a segment that names a COLLECTION (a many-to-many) is joined rather than
   * navigated, which is what lets "the works in this exhibition" be a plain filter.
   */
  @SuppressWarnings("unchecked")
  private static <Y> Path<Y> path(Path<?> root, String property) {
    Path<?> path = root;
    for (String segment : property.split("\\.")) {
      Path<?> next = path.get(segment);
      if (path instanceof From<?, ?> from && Collection.class.isAssignableFrom(next.getJavaType())) {
        next = from.join(segment, JoinType.INNER);
      }
      path = next;
    }
    return (Path<Y>) path;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String first(MultiValueMap<String, String> params, String name) {
    List<String> values = params.get(name);
    return values == null || values.isEmpty() ? null : values.get(0);
  }

  /**
   * The values one filter was given. A parameter may be repeated, and — for a filter whose values
   * cannot themselves contain a comma — a single comma-joined value counts as several. That is what
   * a client sending one multi-select as `?status=open,planned` means, and a browser is a client that
   * naturally joins a list that way.
   *
   * <p>A TEXT filter is deliberately NOT split: `tags` really does hold "portraits, women", and
   * splitting it would turn one honest condition into two loose ones.
   */
  private static List<String> values(
      MultiValueMap<String, String> params, String name, FilterKind kind) {
    List<String> values = params.get(name);
    if (values == null) return List.of();
    return values.stream()
        .filter(v -> v != null && !v.isBlank())
        .flatMap(v -> kind == FilterKind.TEXT ? Stream.of(v) : Arrays.stream(v.split(",")))
        .map(String::trim)
        .filter(v -> !v.isEmpty())
        .toList();
  }
}
