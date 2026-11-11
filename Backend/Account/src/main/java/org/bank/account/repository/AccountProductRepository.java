package org.bank.account.repository;

import org.bank.account.model.AccountProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountProductRepository extends JpaRepository<AccountProduct, UUID> {
}
