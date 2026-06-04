package com.remitz.modules.compliance.repository;

import com.remitz.common.enums.ScreeningListType;
import com.remitz.modules.compliance.entity.SanctionsListEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SanctionsListRepository extends JpaRepository<SanctionsListEntity, Long> {

    List<SanctionsListEntity> findByListName(ScreeningListType listName);

    @Query("SELECT s FROM SanctionsListEntity s WHERE LOWER(s.entryName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<SanctionsListEntity> searchByName(@Param("name") String name);

    @Query("SELECT s FROM SanctionsListEntity s " +
            "WHERE s.listType = :type AND s.deletedAt IS NULL")
    List<SanctionsListEntity> findActiveByListType(@Param("type") SanctionsListEntity.ListType type);
}
