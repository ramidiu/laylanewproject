package com.remitz.modules.auth.repository;

import com.remitz.modules.auth.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByTokenAndRevokedFalse(String token);

    void deleteByUserId(Long userId);

    long countByUserIdAndRevokedFalse(Long userId);
}
