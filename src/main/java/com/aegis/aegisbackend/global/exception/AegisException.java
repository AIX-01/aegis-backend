package com.aegis.aegisbackend.global.exception;

import lombok.Getter;

@Getter
public class AegisException extends RuntimeException {
    private final ErrorCode errorCode;

    public AegisException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AegisException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}

