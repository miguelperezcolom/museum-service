package io.mateu.museum.infra.in.rest;

import io.mateu.museum.domain.Loan;
import io.mateu.museum.infra.in.rest.dto.LoanDto;
import io.mateu.museum.infra.in.rest.query.FilterKind;
import io.mateu.museum.infra.in.rest.query.QuerySpec;
import io.mateu.museum.infra.out.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans")
@Tag(name = "Loans", description = "Works lent between museums: requested → approved → in_transit → on_display → returned")
public class LoansController extends CrudController<Loan, LoanDto> {

  private final LoanRepository loans;
  private final ArtworkRepository artworks;
  private final MuseumRepository museums;
  private final ExhibitionRepository exhibitions;

  public LoansController(
      LoanRepository loans,
      ArtworkRepository artworks,
      MuseumRepository museums,
      ExhibitionRepository exhibitions) {
    this.loans = loans;
    this.artworks = artworks;
    this.museums = museums;
    this.exhibitions = exhibitions;
  }

  @Override
  protected String collection() {
    return "loans";
  }

  @Override
  protected ApiRepository<Loan> repository() {
    return loans;
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("status", FilterKind.EXACT)
        .filter("artworkId", "artwork.id", FilterKind.REF)
        .filter("fromMuseumId", "fromMuseum.id", FilterKind.REF)
        .filter("toMuseumId", "toMuseum.id", FilterKind.REF)
        .filter("exhibitionId", "exhibition.id", FilterKind.REF)
        .filter("startDate", FilterKind.DATE)
        .filter("endDate", FilterKind.DATE)
        .filter("insuranceValue", FilterKind.NUMBER)
        .filter("courier", FilterKind.TEXT)
        .searchable("courier", "notes", "artwork.title")
        .defaultSort("startDate")
        .build();
  }

  @Override
  protected Map<String, String> sortAliases() {
    return Map.of(
        "artworkTitle", "artwork.title",
        "fromMuseumName", "fromMuseum.name",
        "toMuseumName", "toMuseum.name",
        "exhibitionTitle", "exhibition.title");
  }

  @Override
  protected Loan newEntity() {
    return new Loan();
  }

  @Override
  protected Long idOf(LoanDto dto) {
    return dto.id();
  }

  @Override
  protected LoanDto toDto(Loan l) {
    var a = l.getArtwork();
    var from = l.getFromMuseum();
    var to = l.getToMuseum();
    var x = l.getExhibition();
    return new LoanDto(
        l.getId(),
        a == null ? null : a.getId(),
        a == null ? null : a.getTitle(),
        a == null ? null : a.getImageSmall(),
        from == null ? null : from.getId(),
        from == null ? null : from.getName(),
        to == null ? null : to.getId(),
        to == null ? null : to.getName(),
        x == null ? null : x.getId(),
        x == null ? null : x.getTitle(),
        l.getStartDate(),
        l.getEndDate(),
        l.getStatus(),
        l.getInsuranceValue(),
        l.getCourier(),
        l.getNotes(),
        urlOf(l.getId()));
  }

  @Override
  protected void apply(LoanDto dto, Loan l) {
    l.setArtwork(resolveOne(dto.artworkId(), artworks, "artwork"));
    var from = resolveOne(dto.fromMuseumId(), museums, "museum");
    // a loan leaves from where the work is held, unless the caller says otherwise
    l.setFromMuseum(from == null && l.getArtwork() != null ? l.getArtwork().getMuseum() : from);
    l.setExhibition(resolveOne(dto.exhibitionId(), exhibitions, "exhibition"));
    var to = resolveOne(dto.toMuseumId(), museums, "museum");
    // …and goes to the exhibition's museum, when it is for an exhibition and names no museum
    l.setToMuseum(to == null && l.getExhibition() != null ? l.getExhibition().getMuseum() : to);
    l.setStartDate(dto.startDate());
    l.setEndDate(dto.endDate());
    l.setStatus(dto.status() == null || dto.status().isBlank() ? "requested" : dto.status());
    l.setInsuranceValue(dto.insuranceValue());
    l.setCourier(dto.courier());
    l.setNotes(dto.notes());
  }
}
