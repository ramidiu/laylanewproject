package com.remitz.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoAccessResponse {
    private String email;
    private String role;
    private String loginUrl;
    private LocalDateTime expiresAt;
    private String message;
}
