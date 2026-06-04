package com.remitz.modules.user.repository;

import com.remitz.modules.user.entity.KycDocumentTypeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KycDocumentTypeConfigRepository extends JpaRepository<KycDocumentTypeConfig, Long> {

    List<KycDocumentTypeConfig> findByCountryCodeAndCategoryAndIsActiveOrderByDisplayOrder(
            String countryCode, String category, Boolean isActive);

    List<KycDocumentTypeConfig> findByCategoryAndIsActiveOrderByDisplayOrder(
            String category, Boolean isActive);
}
