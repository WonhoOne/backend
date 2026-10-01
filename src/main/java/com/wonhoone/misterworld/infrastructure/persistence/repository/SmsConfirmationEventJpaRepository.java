package com.wonhoone.misterworld.infrastructure.persistence.repository;

import com.wonhoone.misterworld.infrastructure.persistence.entity.SmsConfirmationEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsConfirmationEventJpaRepository extends JpaRepository<SmsConfirmationEventJpaEntity, Long> {}
