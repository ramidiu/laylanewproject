package com.remitz.modules.fx.service;

import com.remitz.common.dto.NostroAccountResponse;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.fx.entity.NostroAccountEntity;
import com.remitz.modules.fx.repository.NostroAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NostroService {

    private static final String NOSTRO_LOW_BALANCE_CHANNEL = "nostro:low-balance";

    private final NostroAccountRepository nostroAccountRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public List<NostroAccountResponse> getBalances() {
        return nostroAccountRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public NostroAccountResponse updateBalance(Long id, BigDecimal newBalance) {
        NostroAccountEntity entity = nostroAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NostroAccount", "id", id));

        entity.setCurrentBalance(newBalance);
        entity.setLastReconciledAt(LocalDateTime.now());
        entity = nostroAccountRepository.save(entity);

        checkLowBalance(entity);

        log.info("Updated nostro account id={} balance={}", id, newBalance);
        return mapToResponse(entity);
    }

    @Transactional
    public void deductBalance(String currency, String country, BigDecimal amount) {
        NostroAccountEntity entity = nostroAccountRepository.findByCurrencyAndCountry(currency, country)
                .orElseThrow(() -> new ResourceNotFoundException("NostroAccount", "currency/country",
                        currency + "/" + country));

        if (entity.getCurrentBalance().compareTo(amount) < 0) {
            throw new RemitzException(
                    String.format("Insufficient nostro balance for %s/%s. Available: %s, Required: %s",
                            currency, country, entity.getCurrentBalance(), amount),
                    HttpStatus.CONFLICT);
        }

        entity.setCurrentBalance(entity.getCurrentBalance().subtract(amount));
        nostroAccountRepository.save(entity);

        checkLowBalance(entity);

        log.info("Deducted {} {} from nostro account id={}, remaining={}",
                amount, currency, entity.getId(), entity.getCurrentBalance());
    }

    private void checkLowBalance(NostroAccountEntity entity) {
        if (entity.getLowBalanceThreshold() != null
                && entity.getCurrentBalance().compareTo(entity.getLowBalanceThreshold()) < 0) {
            log.warn("NOSTRO LOW BALANCE ALERT: {} {} account at {} (threshold: {})",
                    entity.getCurrency(), entity.getCountry(),
                    entity.getCurrentBalance(), entity.getLowBalanceThreshold());

            try {
                String message = String.format("LOW_BALANCE:%s:%s:%s:%s",
                        entity.getCurrency(), entity.getCountry(),
                        entity.getCurrentBalance(), entity.getLowBalanceThreshold());
                redisTemplate.convertAndSend(NOSTRO_LOW_BALANCE_CHANNEL, message);
            } catch (Exception e) {
                log.error("Failed to publish nostro low balance event: {}", e.getMessage());
            }
        }
    }

    private NostroAccountResponse mapToResponse(NostroAccountEntity entity) {
        return NostroAccountResponse.builder()
                .id(entity.getId())
                .bankName(entity.getBankName())
                .accountNumber(entity.getAccountNumber())
                .currency(entity.getCurrency())
                .country(entity.getCountry())
                .currentBalance(entity.getCurrentBalance())
                .lowBalanceThreshold(entity.getLowBalanceThreshold())
                .lastReconciledAt(entity.getLastReconciledAt())
                .build();
    }
}
