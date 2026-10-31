package io.mateu.museum.infra.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record ArtworkDto(
    Long id,
    @NotBlank(message = "must not be blank") String title,
    Long artistId,
    /** Read-only. */
    String artistName,
    String artistDisplay,
    Long museumId,
    /** Read-only. */
    String museumName,
    /** Read-only. */
    String museumCode,
    Long departmentId,
    /** Read-only. */
    String departmentName,
    String classification,
    String objectType,
    String medium,
    String objectDate,
    Integer beginYear,
    Integer endYear,
    String dimensions,
    BigDecimal heightCm,
    BigDecimal widthCm,
    BigDecimal depthCm,
    String culture,
    Boolean onView,
    String gallery,
    Integer acquisitionYear,
    String accessionNumber,
    String creditLine,
    /** Synthetic, in USD — museums do not publish valuations. */
    BigDecimal estimatedValue,
    String tags,
    String description,
    Boolean highlight,
    /** The museum's own image URLs (IIIF / CDN). */
    String imageSmall,
    String imageLarge,
    /** "The Metropolitan Museum of Art · CC0" — what to show next to the image. */
    String imageCredit,
    /** The work's page on the museum's website. */
    String sourceUrl,
    String sourceId,
    String url) {}
