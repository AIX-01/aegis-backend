package com.aegis.aegisbackend.global.common.enums;

public enum NotificationType {
    ALERT("alert"),
    WARNING("warning"),
    INFO("info"),
    SUCCESS("success");

    private final String value;

    NotificationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static NotificationType fromValue(String value) {
        for (NotificationType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown NotificationType: " + value);
    }
}

