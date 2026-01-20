package com.aegis.aegisbackend.global.common.enums;

public enum EventStatus {
    PROCESSING("processing"),
    RESOLVED("resolved");

    private final String value;

    EventStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static EventStatus fromValue(String value) {
        for (EventStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown EventStatus: " + value);
    }
}

