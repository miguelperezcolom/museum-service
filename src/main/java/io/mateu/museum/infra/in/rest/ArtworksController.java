package io.mateu.museum.infra.in.rest;

import io.mateu.museum.domain.Artwork;
import io.mateu.museum.infra.in.rest.dto.ArtworkDto;
import io.mateu.museum.infra.in.rest.query.FilterKind;
import io.mateu.museum.infra.in.rest.query.QuerySpec;
import io.mateu.museum.infra.out.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artworks")
@Tag(name = "Artworks", description = "Public-domain works held by the museums (CC0 records and images)")
public class ArtworksController extends CrudController<Artwork, ArtworkDto> {

  private final ArtworkRepository artworks;
  private final ArtistRepository artists;
  private final MuseumRepository museums;
  private final DepartmentRepository departments;

  public ArtworksController(
      ArtworkRepository artworks,
      ArtistRepository artists,
      MuseumRepository museums,
      DepartmentRepository departments) {
    this.artworks = artworks;
    this.artists = artists;
    this.museums = museums;
    this.departments = departments;
  }

  @Override
  protected String collection() {
    return "artworks";
  }

  @Override
  protected ApiRepository<Artwork> repository() {
    return artworks;
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("title", FilterKind.TEXT)
        .filter("artistDisplay", FilterKind.TEXT)
        .filter("artistId", "artist.id", FilterKind.REF)
        .filter("museumId", "museum.id", FilterKind.REF)
        .filter("museumCode", "museum.code", FilterKind.EXACT)
        .filter("departmentId", "department.id", FilterKind.REF)
        // the works an exhibition shows: through the many-to-many it owns
        .filter("exhibitionId", "exhibitions.id", FilterKind.REF)
        // the museums' own vocabulary (Painting, Sculpture, Photograph…): a pick, matched exactly
        .filter("classification", FilterKind.EXACT)
        .filter("culture", FilterKind.TEXT)
        .filter("medium", FilterKind.TEXT)
        .filter("gallery", FilterKind.TEXT)
        .filter("tags", FilterKind.TEXT)
        .filter("onView", FilterKind.BOOLEAN)
        .filter("highlight", FilterKind.BOOLEAN)
        .filter("beginYear", FilterKind.NUMBER)
        .filter("endYear", FilterKind.NUMBER)
        .filter("acquisitionYear", FilterKind.NUMBER)
        .filter("estimatedValue", FilterKind.NUMBER)
        .filter("heightCm", FilterKind.NUMBER)
        .filter("widthCm", FilterKind.NUMBER)
        .searchable("title", "artistDisplay", "culture", "medium", "classification", "tags")
        .defaultSort("title")
        .build();
  }

  @Override
  protected Map<String, String> sortAliases() {
    return Map.of(
        "artistName", "artist.name",
        "museumName", "museum.name",
        "museumCode", "museum.code",
        "departmentName", "department.name");
  }

  @Override
  protected Artwork newEntity() {
    return new Artwork();
  }

  @Override
  protected Long idOf(ArtworkDto dto) {
    return dto.id();
  }

  @Override
  protected ArtworkDto toDto(Artwork a) {
    var artist = a.getArtist();
    var museum = a.getMuseum();
    var department = a.getDepartment();
    return new ArtworkDto(
        a.getId(),
        a.getTitle(),
        artist == null ? null : artist.getId(),
        artist == null ? null : artist.getName(),
        a.getArtistDisplay(),
        museum == null ? null : museum.getId(),
        museum == null ? null : museum.getName(),
        museum == null ? null : museum.getCode(),
        department == null ? null : department.getId(),
        department == null ? null : department.getName(),
        a.getClassification(),
        a.getObjectType(),
        a.getMedium(),
        a.getObjectDate(),
        a.getBeginYear(),
        a.getEndYear(),
        a.getDimensions(),
        a.getHeightCm(),
        a.getWidthCm(),
        a.getDepthCm(),
        a.getCulture(),
        a.getOnView(),
        a.getGallery(),
        a.getAcquisitionYear(),
        a.getAccessionNumber(),
        a.getCreditLine(),
        a.getEstimatedValue(),
        a.getTags(),
        a.getDescription(),
        a.getHighlight(),
        a.getImageSmall(),
        a.getImageLarge(),
        a.getImageCredit(),
        a.getSourceUrl(),
        a.getSourceId(),
        urlOf(a.getId()));
  }

  @Override
  protected void apply(ArtworkDto dto, Artwork a) {
    // The image, its credit and the source link are the museum's: kept when the body omits them.
    a.setTitle(dto.title());
    a.setArtist(resolveOne(dto.artistId(), artists, "artist"));
    a.setArtistDisplay(
        dto.artistDisplay() != null || a.getArtist() == null
            ? dto.artistDisplay()
            : a.getArtist().getName());
    a.setMuseum(resolveOne(dto.museumId(), museums, "museum"));
    a.setDepartment(resolveOne(dto.departmentId(), departments, "department"));
    a.setClassification(dto.classification());
    a.setObjectType(dto.objectType());
    a.setMedium(dto.medium());
    a.setObjectDate(dto.objectDate());
    a.setBeginYear(dto.beginYear());
    a.setEndYear(dto.endYear());
    a.setDimensions(dto.dimensions());
    a.setHeightCm(dto.heightCm());
    a.setWidthCm(dto.widthCm());
    a.setDepthCm(dto.depthCm());
    a.setCulture(dto.culture());
    a.setOnView(dto.onView());
    a.setGallery(dto.gallery());
    a.setAcquisitionYear(dto.acquisitionYear());
    a.setAccessionNumber(dto.accessionNumber());
    a.setCreditLine(dto.creditLine());
    a.setEstimatedValue(dto.estimatedValue());
    a.setTags(dto.tags());
    a.setDescription(dto.description());
    a.setHighlight(dto.highlight());
    a.setImageSmall(keep(dto.imageSmall(), a.getImageSmall()));
    a.setImageLarge(keep(dto.imageLarge(), a.getImageLarge()));
    a.setImageCredit(keep(dto.imageCredit(), a.getImageCredit()));
    a.setSourceUrl(keep(dto.sourceUrl(), a.getSourceUrl()));
    a.setSourceId(keep(dto.sourceId(), a.getSourceId()));
  }
}
