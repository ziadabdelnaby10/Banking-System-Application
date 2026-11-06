package org.bank.identity.repository;

public interface UserRepository extends org.springframework.data.jpa.repository.JpaRepository<org.bank.identity.model.User, java.util.UUID> ,org.springframework.data.jpa.repository.JpaSpecificationExecutor<org.bank.identity.model.User> {
  }