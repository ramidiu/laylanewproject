package com.remitz.modules.compliance.repository;

import com.remitz.common.enums.MonitoringRuleType;
import com.remitz.modules.compliance.entity.MonitoringRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MonitoringRuleRepository extends JpaRepository<MonitoringRuleEntity, Long> {

    List<MonitoringRuleEntity> findByIsActiveTrue();

    List<MonitoringRuleEntity> findByRuleType(MonitoringRuleType ruleType);
}
