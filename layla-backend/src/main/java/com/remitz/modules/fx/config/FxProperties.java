package com.remitz.modules.fx.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.fx")
@Getter
@Setter
public class FxProperties {

    private ExchangeRatesApi exchangeRatesApi = new ExchangeRatesApi();
    private long rateFetchIntervalMs = 300000;
    private long rateCacheTtlSeconds = 300;
    private long quoteLockTtlSeconds = 60;
    private long staleRateThresholdSeconds = 600;

    @Getter
    @Setter
    public static class ExchangeRatesApi {
        private String apiKey = "";
        private String baseUrl = "https://api.exchangeratesapi.io/v1";
    }
}
