package com.remitz.modules.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.user.entity.SystemConfig;
import com.remitz.modules.user.entity.SystemConfigAudit;
import com.remitz.modules.user.repository.SystemConfigAuditRepository;
import com.remitz.modules.user.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Code added by Naresh: System Controls Phase 2 — typed access, validation and
 * Redis-backed read cache for {@code system_config}.
 *
 * Phase 2 is purely a service-layer addition. Business callsites (auth, fx,
 * compliance, transaction) still read their values via the existing {@code @Value}
 * and {@code app.*} property mechanism; migrating those reads is Phase 3 scope.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    private static final String CACHE_PREFIX = "system_config:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    // Supported value_type codes stored on each row.
    private static final String TYPE_STRING  = "STRING";
    private static final String TYPE_BOOLEAN = "BOOLEAN";
    private static final String TYPE_INT     = "INT";
    private static final String TYPE_DECIMAL = "DECIMAL";
    private static final String TYPE_JSON    = "JSON";

    private final SystemConfigRepository repository;
    // Code added by Naresh: Phase 3 — append-only audit trail for config writes.
    private final SystemConfigAuditRepository auditRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    // ---------- READ API (bulk + single) ----------

    public List<SystemConfig> findAll() {
        return repository.findAll();
    }

    public SystemConfig findByKey(String configKey) {
        return repository.findByConfigKey(configKey)
                .orElseThrow(() -> new ResourceNotFoundException("SystemConfig", "key", configKey));
    }

    // ---------- TYPED GETTERS (cached, never throw) ----------

    public String getString(String key, String defaultValue) {
        String cached = safeCacheGet(key);
        if (cached != null) return cached;
        return repository.findByConfigKey(key)
                .map(c -> {
                    safeCachePut(key, c.getConfigValue());
                    return c.getConfigValue();
                })
                .orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String raw = getString(key, null);
        if (raw == null || raw.isBlank()) return defaultValue;
        if ("true".equalsIgnoreCase(raw.trim()))  return true;
        if ("false".equalsIgnoreCase(raw.trim())) return false;
        log.warn("system_config[{}] value '{}' is not a boolean; returning default", key, raw);
        return defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String raw = getString(key, null);
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("system_config[{}] value '{}' is not an integer; returning default", key, raw);
            return defaultValue;
        }
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        String raw = getString(key, null);
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("system_config[{}] value '{}' is not a decimal; returning default", key, raw);
            return defaultValue;
        }
    }

    public <T> T getJson(String key, Class<T> type, T defaultValue) {
        String raw = getString(key, null);
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            log.warn("system_config[{}] value is not valid JSON for {}; returning default: {}",
                    key, type.getSimpleName(), e.getMessage());
            return defaultValue;
        }
    }

    // ---------- WRITE API (validates + increments version + refreshes cache) ----------

    /**
     * Updates a config's value after validating the new value against its
     * {@code value_type} and {@code allowed_values}. Bumps {@code version} on success.
     * If {@code expectedVersion} is not null and does not match the current row,
     * throws 409. Refreshes the Redis cache after the DB commit.
     */
    @Transactional
    public SystemConfig updateValue(String configKey,
                                    String newValue,
                                    Integer expectedVersion,
                                    String updatedBy) {
        return updateValue(configKey, newValue, expectedVersion, updatedBy, null);
    }

    // Code added by Naresh: System Controls Phase 7 — reason-carrying overload.
    @Transactional
    public SystemConfig updateValue(String configKey,
                                    String newValue,
                                    Integer expectedVersion,
                                    String updatedBy,
                                    String reason) {
        if (newValue == null) {
            throw new RemitzException("value is required", HttpStatus.BAD_REQUEST);
        }

        SystemConfig config = repository.findByConfigKey(configKey)
                .orElseThrow(() -> new ResourceNotFoundException("SystemConfig", "key", configKey));

        if (expectedVersion != null && !expectedVersion.equals(config.getVersion())) {
            log.info("system_config[{}] stale update rejected (client version {} != current {})",
                    configKey, expectedVersion, config.getVersion());
            throw new RemitzException(
                    "Config has been updated by someone else. Refresh and try again.",
                    HttpStatus.CONFLICT);
        }

        validateAgainstType(config.getValueType(), newValue, configKey);
        validateAgainstAllowedValues(config.getAllowedValues(), newValue, configKey);

        // Code added by Naresh: Phase 3 — capture before-state BEFORE mutating the entity.
        String oldValue = config.getConfigValue();
        Integer oldVersion = config.getVersion();
        String actor = (updatedBy != null && !updatedBy.isBlank()) ? updatedBy : "SYSTEM";

        config.setConfigValue(newValue);
        config.setUpdatedBy(actor);
        config.setVersion((oldVersion == null ? 1 : oldVersion) + 1);

        SystemConfig saved = repository.save(config);

        // Append audit row inside the same transaction — rolls back with the config update on failure.
        auditRepository.save(SystemConfigAudit.builder()
                .configKey(configKey)
                .oldValue(oldValue)
                .newValue(saved.getConfigValue())
                .oldVersion(oldVersion)
                .newVersion(saved.getVersion())
                .changedBy(actor)
                .changeSource("API")
                .reason(reason != null && !reason.isBlank() ? reason.trim() : null)
                .build());

        safeCachePut(configKey, saved.getConfigValue());
        log.info("system_config[{}] updated '{}' -> '{}' (v{} -> v{}) by {}",
                configKey, oldValue, saved.getConfigValue(), oldVersion, saved.getVersion(), actor);
        return saved;
    }

    // Code added by Naresh: Phase 3 — history read for the admin UI.
    public List<SystemConfigAudit> findHistory(String configKey) {
        return auditRepository.findByConfigKeyOrderByChangedAtDescIdDesc(configKey);
    }

    // ---------- VALIDATION ----------

    private void validateAgainstType(String valueType, String value, String key) {
        String type = (valueType == null ? TYPE_STRING : valueType.trim().toUpperCase());
        switch (type) {
            case TYPE_BOOLEAN -> {
                String v = value.trim();
                if (!"true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
                    throw new RemitzException(
                            "Invalid BOOLEAN value for " + key + " — expected 'true' or 'false'",
                            HttpStatus.BAD_REQUEST);
                }
            }
            case TYPE_INT -> {
                try {
                    Integer.parseInt(value.trim());
                } catch (NumberFormatException e) {
                    throw new RemitzException(
                            "Invalid INT value for " + key + " — expected an integer",
                            HttpStatus.BAD_REQUEST);
                }
            }
            case TYPE_DECIMAL -> {
                try {
                    new BigDecimal(value.trim());
                } catch (NumberFormatException e) {
                    throw new RemitzException(
                            "Invalid DECIMAL value for " + key + " — expected a decimal number",
                            HttpStatus.BAD_REQUEST);
                }
            }
            case TYPE_JSON -> {
                try {
                    objectMapper.readTree(value);
                } catch (Exception e) {
                    throw new RemitzException(
                            "Invalid JSON value for " + key + " — " + e.getMessage(),
                            HttpStatus.BAD_REQUEST);
                }
            }
            case TYPE_STRING -> { /* anything non-null is fine */ }
            default -> log.warn("Unknown value_type '{}' on system_config[{}]; treating as STRING",
                    valueType, key);
        }
    }

    /**
     * When {@code allowed_values} is populated it must be a JSON array of strings;
     * the incoming value must match one element exactly (case-sensitive).
     * Blank/null allowed_values disables the check.
     */
    private void validateAgainstAllowedValues(String allowedValuesJson, String value, String key) {
        if (allowedValuesJson == null || allowedValuesJson.isBlank()) return;
        List<String> allowed;
        try {
            allowed = objectMapper.readValue(allowedValuesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("system_config[{}] allowed_values is not a JSON string array; skipping check: {}",
                    key, e.getMessage());
            return;
        }
        if (!allowed.contains(value)) {
            throw new RemitzException(
                    "Value '" + value + "' is not in allowed_values for " + key + " " + allowed,
                    HttpStatus.BAD_REQUEST);
        }
    }

    // ---------- CACHE HELPERS (never let Redis failures break the app) ----------

    private String safeCacheGet(String key) {
        try {
            return redis.opsForValue().get(CACHE_PREFIX + key);
        } catch (Exception e) {
            log.debug("Redis GET failed for system_config[{}]: {}", key, e.getMessage());
            return null;
        }
    }

    private void safeCachePut(String key, String value) {
        try {
            if (value == null) {
                redis.delete(CACHE_PREFIX + key);
            } else {
                redis.opsForValue().set(CACHE_PREFIX + key, value, CACHE_TTL);
            }
        } catch (Exception e) {
            log.debug("Redis SET failed for system_config[{}]: {}", key, e.getMessage());
        }
    }
}
