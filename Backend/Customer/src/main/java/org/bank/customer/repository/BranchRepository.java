package org.bank.customer.repository;

import org.bank.customer.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, java.util.UUID> {

    Optional<Branch> findByCode(String code);

    boolean existsByCode(String code);

    List<Branch> findAllByOrderByCodeAsc();
}
