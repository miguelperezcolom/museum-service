package io.mateu.museum.infra.out;

import io.mateu.museum.domain.Artwork;

public interface ArtworkRepository extends ApiRepository<Artwork> {

  long countByMuseum_Id(Long museumId);

  long countByArtist_Id(Long artistId);

  long countByDepartment_Id(Long departmentId);
}
