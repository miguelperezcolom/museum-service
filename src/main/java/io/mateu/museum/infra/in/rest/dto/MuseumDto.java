package io.mateu.museum.infra.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record MuseumDto(
    Long id,
    @NotBlank(message = "must not be blank") String code,
    @NotBlank(message = "must not be blank") String name,
    String shortName,
    String city,
    String country,
    BigDecimal latitude,
    BigDecimal longitude,
    Integer founded,
    String website,
    String description,
    /** A highlight of its own collection (CC0), linked from the museum. */
    String image,
    String imageCredit,
    /** Read-only, computed. */
    Long departmentCount,
    /** Read-only, computed. */
    Long artworkCount,
    String url) {}
