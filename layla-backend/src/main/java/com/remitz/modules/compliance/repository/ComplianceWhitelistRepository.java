package com.remitz.modules.compliance.repository;

import com.remitz.modules.compliance.entity.ComplianceWhitelistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplianceWhitelistRepository extends JpaRepository<ComplianceWhitelistEntity, Long> {

    Optional<ComplianceWhitelistEntity> findBySubjectTypeAndSubjectIdAndListEntryId(
            ComplianceWhitelistEntity.SubjectType subjectType,
            Long subjectId,
            Long listEntryId);

    boolean existsBySubjectTypeAndSubjectIdAndListEntryId(
            ComplianceWhitelistEntity.SubjectType subjectType,
            Long subjectId,
            Long listEntryId);

}
