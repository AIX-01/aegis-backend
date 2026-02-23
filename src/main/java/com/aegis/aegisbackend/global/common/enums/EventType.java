package com.aegis.aegisbackend.global.common.enums;

public enum EventType {
    ASSAULT("assault"),      // 폭행
    BURGLARY("burglary"),    // 절도
    DUMP("dump"),            // 투기
    SWOON("swoon"),          // 실신
    VANDALISM("vandalism");  // 파손

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static EventType fromValue(String value) {
        for (EventType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown EventType: " + value);
    }
}

