package org.bank.identity.repository;

import jakarta.persistence.LockModeType;
import org.bank.identity.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, Long> {

    /** Serialises concurrent refreshes of the same session so rotation and reuse detection are race-free. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Session s where s.id = :id")
    Optional<Session> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("update Session s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("delete from Session s where s.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
