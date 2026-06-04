package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.BankDatabase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankDatabaseRepository extends JpaRepository<BankDatabase, Long> {

    Optional<BankDatabase> findByCountryCodeAndBankIdentifier(String countryCode, String bankIdentifier);

    List<BankDatabase> findByCountryCodeAndBankIdentifierStartingWith(String countryCode, String bankIdentifierPrefix);

    List<BankDatabase> findByCountryCodeAndBankNameContainingIgnoreCase(String countryCode, String bankName);

    @Query("SELECT DISTINCT b.bankName FROM BankDatabase b WHERE b.countryCode = :countryCode AND b.isActive = true")
    List<String> findDistinctBankNameByCountryCode(@Param("countryCode") String countryCode);
}
