package com.remitz.modules.user.repository;

import com.remitz.common.enums.KycDocumentStatus;
import com.remitz.common.enums.KycDocumentType;
import com.remitz.modules.user.entity.KycDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KycDocumentRepository extends JpaRepository<KycDocumentEntity, Long> {

    List<KycDocumentEntity> findByUserId(Long userId);

    List<KycDocumentEntity> findByUserIdAndStatus(Long userId, KycDocumentStatus status);

    // Active documents only — excludes rows superseded by a newer upload of the same type.
    List<KycDocumentEntity> findByUserIdAndSupersededAtIsNull(Long userId);

    // Active documents of a specific type for a user (used to supersede on re-upload).
    List<KycDocumentEntity> findByUserIdAndDocumentTypeAndSupersededAtIsNull(Long userId, KycDocumentType documentType);

    long countByUserId(Long userId);
}
