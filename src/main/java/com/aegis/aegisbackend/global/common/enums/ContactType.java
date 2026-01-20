package com.aegis.aegisbackend.global.common.enums;

public enum ContactType {
    PRIMARY("primary"),
    SECONDARY("secondary");

    private final String value;

    ContactType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ContactType fromValue(String value) {
        for (ContactType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ContactType: " + value);
    }
}

