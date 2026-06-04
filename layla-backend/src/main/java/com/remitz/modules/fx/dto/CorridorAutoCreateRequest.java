package com.remitz.modules.fx.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorridorAutoCreateRequest {

    private String sendCurrency;
    private String sendCountry;
    private String receiveCurrency;
    private String receiveCountry;
}
