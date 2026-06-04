package com.remitz.modules.payout.usi.dto;

import lombok.Data;

@Data
public class RemitterVerifyResponse {
    private String remitterId;
    private String firstName;
    private String lastName;
    private boolean verified;
    private String status;
    private boolean valid;
}
