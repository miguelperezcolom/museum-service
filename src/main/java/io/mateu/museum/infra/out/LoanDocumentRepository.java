package io.mateu.museum.infra.out;

import io.mateu.museum.domain.LoanDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanDocumentRepository extends JpaRepository<LoanDocument, Long> {

  List<LoanDocument> findByLoanIdOrderByUploadedAtDescIdDesc(Long loanId);

  Optional<LoanDocument> findByIdAndLoanId(Long id, Long loanId);

  long countByLoanId(Long loanId);

  void deleteByLoanId(Long loanId);
}
