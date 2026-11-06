package org.bank.identity.repository;

import jakarta.persistence.LockModeType;
import org.bank.identity.model.MfaChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MfaChallenge c where c.id = :id")
    Optional<MfaChallenge> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("delete from MfaChallenge c where c.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
