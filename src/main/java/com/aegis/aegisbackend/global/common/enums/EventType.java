package com.aegis.aegisbackend.global.common.enums;

public enum EventType {
    ASSAULT("assault"),
    THEFT("theft"),
    SUSPICIOUS("suspicious"),
    NORMAL("normal");

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

