package org.bank.identity.repository;

import org.bank.identity.model.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID>, JpaSpecificationExecutor<LoginAttempt> {
}