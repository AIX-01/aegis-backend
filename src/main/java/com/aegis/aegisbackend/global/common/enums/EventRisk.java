package com.aegis.aegisbackend.global.common.enums;

/**
 * 이벤트 위험 수준 (1차 분류)
 */
public enum EventRisk {
    NORMAL("normal"),
    SUSPICIOUS("suspicious"),
    ABNORMAL("abnormal");

    private final String value;

    EventRisk(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static EventRisk fromValue(String value) {
        for (EventRisk risk : values()) {
            if (risk.value.equalsIgnoreCase(value)) {
                return risk;
            }
        }
        throw new IllegalArgumentException("Unknown EventRisk: " + value);
    }
}
