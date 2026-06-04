package com.remitz.modules.user.repository;

import com.remitz.modules.user.entity.Relation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelationRepository extends JpaRepository<Relation, Long> {

    List<Relation> findByIsActive(Boolean isActive);
}
