package org.bank.identity.repository;

public interface LoginAttemptRepository extends org.springframework.data.jpa.repository.JpaRepository<org.bank.identity.model.LoginAttempt, java.util.UUID> ,org.springframework.data.jpa.repository.JpaSpecificationExecutor<org.bank.identity.model.LoginAttempt> {
  }