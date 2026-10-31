package io.mateu.museum.infra.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record ArtistDto(
    Long id,
    @NotBlank(message = "must not be blank") String name,
    String nationality,
    Integer birthYear,
    Integer deathYear,
    /** Read-only: "1853–1890", "born 1900", or null. */
    String lifespan,
    String bio,
    /** A work by the artist (not a portrait), linked from its museum. */
    String image,
    String imageCredit,
    /** Read-only, computed. */
    Long artworkCount,
    String url) {}
