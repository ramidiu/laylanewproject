package com.remitz.modules.user.service;

import com.remitz.common.exception.RemitzException;
import com.remitz.modules.user.entity.WalletEntity;
import com.remitz.modules.user.entity.WalletLedgerEntity;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.modules.user.repository.WalletLedgerRepository;
import com.remitz.modules.user.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final UserRepository userRepository;

    @Transactional
    public WalletEntity getOrCreateWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    userRepository.findById(userId)
                            .orElseThrow(() -> new RemitzException("User not found with id: " + userId, HttpStatus.NOT_FOUND));

                    String walletNumber = "WLT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

                    WalletEntity wallet = WalletEntity.builder()
                            .userId(userId)
                            .walletNumber(walletNumber)
                            .balance(BigDecimal.ZERO)
                            .currency("GBP")
                            .isActive(true)
                            .build();

                    log.info("Creating new wallet for userId={}, walletNumber={}", userId, walletNumber);
                    return walletRepository.save(wallet);
                });
    }

    public WalletEntity getWalletByUserId(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RemitzException("Wallet not found for userId: " + userId, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public WalletEntity debit(Long userId, BigDecimal amount, String description, String referenceId, String referenceType) {
        WalletEntity wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> getOrCreateWallet(userId));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new RemitzException("Insufficient wallet balance. Available: " + wallet.getBalance() + ", Required: " + amount, HttpStatus.BAD_REQUEST);
        }

        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        wallet.setBalance(balanceAfter);
        walletRepository.save(wallet);

        WalletLedgerEntity ledger = WalletLedgerEntity.builder()
                .walletId(wallet.getId())
                .entryType("DEBIT")
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .description(description)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .build();
        walletLedgerRepository.save(ledger);

        log.info("Wallet debited: userId={}, amount={}, balanceAfter={}", userId, amount, balanceAfter);
        return wallet;
    }

    @Transactional
    public WalletEntity credit(Long userId, BigDecimal amount, String description, String referenceId, String referenceType) {
        WalletEntity wallet = getOrCreateWallet(userId);

        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);
        wallet.setBalance(balanceAfter);
        walletRepository.save(wallet);

        WalletLedgerEntity ledger = WalletLedgerEntity.builder()
                .walletId(wallet.getId())
                .entryType("CREDIT")
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .description(description)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .build();
        walletLedgerRepository.save(ledger);

        log.info("Wallet credited: userId={}, amount={}, balanceAfter={}", userId, amount, balanceAfter);
        return wallet;
    }

    public List<WalletLedgerEntity> getTransactions(Long userId) {
        WalletEntity wallet = getWalletByUserId(userId);
        return walletLedgerRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId());
    }

    public List<WalletEntity> getAllWallets() {
        return walletRepository.findAll();
    }

    @Transactional
    public WalletEntity adminCredit(Long userId, BigDecimal amount, String description) {
        return credit(userId, amount, description, null, "ADMIN_CREDIT");
    }

    @Transactional
    public WalletEntity adminDebit(Long userId, BigDecimal amount, String description) {
        return debit(userId, amount, description, null, "ADMIN_DEBIT");
    }
}
