package io.mateu.museum.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The published contract: /v3/api-docs, rendered at /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI museumOpenApi(@Value("${museum.write-key-header}") String writeKeyHeader) {
    return new OpenAPI()
        .info(
            new Info()
                .title("Museum service")
                .version("0.1.0")
                .description(
                    """
                    Several art museums behind one writable API: public-domain artworks of The \
                    Metropolitan Museum of Art, the Art Institute of Chicago and the Cleveland Museum \
                    of Art — CC0 records, each linking the museum's own images — plus synthetic \
                    exhibitions, loans, conservation tasks and daily visits, over an in-memory H2 \
                    seeded at boot.

                    **Reads are open. Writes need the `%s` header.**

                    Every collection answers `GET /api/<collection>` with a paged envelope \
                    (`content` + `totalElements`) and supports `search`, its own declared filters, \
                    `<field>_from`/`<field>_to` ranges, `sort` and `page`/`size`. A filter the \
                    collection does not declare is a 400, never a silently unfiltered page.

                    References travel as ids plus the display name for the single-valued ones \
                    (`museumId` + `museumName`), so a table renders without a second request.

                    Images: every artwork carries `imageSmall`, `imageLarge`, `imageCredit` and \
                    `sourceUrl`; `/api/credits` lists them all. The database is in memory and \
                    re-seeded on every restart.
                    """
                        .formatted(writeKeyHeader))
                .license(new License().name("Code: MIT · Data and images: CC0 (see CREDITS.md)")))
        .components(
            new Components()
                .addSecuritySchemes(
                    "writeKey",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(writeKeyHeader)
                        .description("Required on POST, PUT, PATCH and DELETE.")));
  }
}
