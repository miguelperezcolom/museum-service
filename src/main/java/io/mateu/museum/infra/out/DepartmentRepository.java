package io.mateu.museum.infra.out;

import io.mateu.museum.domain.Department;

public interface DepartmentRepository extends ApiRepository<Department> {

  long countByMuseum_Id(Long museumId);
}
