package io.mateu.museum.infra.in.rest.query;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * The envelope every collection answers with.
 *
 * <p>The API this clones returns a bare JSON array, which cannot say how many rows matched — so a
 * client can never render "390 artworks found" or a pager without fetching everything. The envelope
 * carries the total, and a consumer that maps responses by path (a Mateu listing, for one) points
 * `itemsPath` at `content` and `totalPath` at `totalElements`.
 */
public record PageResponse<T>(
    List<T> content, long totalElements, int totalPages, int page, int size) {

  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }
}
