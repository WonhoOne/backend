package com.wonhoone.misterworld.infrastructure.persistence.repository;

import com.wonhoone.misterworld.infrastructure.persistence.entity.UserAccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountJpaRepository extends JpaRepository<UserAccountJpaEntity, Long> {
    Optional<UserAccountJpaEntity> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
