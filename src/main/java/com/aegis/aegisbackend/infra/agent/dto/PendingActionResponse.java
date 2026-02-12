package com.aegis.aegisbackend.infra.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pending Action 응답 DTO (Spring → Agent)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingActionResponse {
    private String userId;
    private String userName;
    private String userEmail;
    private boolean result;

    public static PendingActionResponse approved(String userId, String userName, String userEmail) {
        return PendingActionResponse.builder()
                .userId(userId)
                .userName(userName)
                .userEmail(userEmail)
                .result(true)
                .build();
    }

    public static PendingActionResponse rejected(String userId, String userName, String userEmail) {
        return PendingActionResponse.builder()
                .userId(userId)
                .userName(userName)
                .userEmail(userEmail)
                .result(false)
                .build();
    }
}

