package com.wonhoone.misterworld.infrastructure.persistence.repository;

import com.wonhoone.misterworld.infrastructure.persistence.entity.SmsConfirmationRecipientJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SmsConfirmationRecipientJpaRepository extends JpaRepository<SmsConfirmationRecipientJpaEntity, Long> {
    // Lock only the recipient. Load its immutable event lazily after obtaining the lock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SmsConfirmationRecipientJpaEntity r where r.id = :id")
    Optional<SmsConfirmationRecipientJpaEntity> findByIdForDelivery(@Param("id") long id);

    @Query("""
            select r.id from SmsConfirmationRecipientJpaEntity r
            where r.status = com.wonhoone.misterworld.infrastructure.persistence.entity.SmsDeliveryStatus.PENDING
              and r.nextAttemptAt <= :now order by r.id asc
            """)
    List<Long> findDueIds(@Param("now") Instant now, Pageable batch);

    @Query("""
            select r.id from SmsConfirmationRecipientJpaEntity r
            where r.confirmationEvent.id = :eventId
              and r.status = com.wonhoone.misterworld.infrastructure.persistence.entity.SmsDeliveryStatus.PENDING
              and r.nextAttemptAt <= :now and r.id > :afterId order by r.id asc
            """)
    List<Long> findDueEventIds(@Param("eventId") long eventId, @Param("now") Instant now,
                               @Param("afterId") long afterId, Pageable batch);
}
