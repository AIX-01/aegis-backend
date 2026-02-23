package com.aegis.aegisbackend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Auth
    EMAIL_NOT_FOUND(HttpStatus.UNAUTHORIZED, "등록되지 않은 이메일입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호가 일치하지 않습니다."),
    USER_NOT_APPROVED(HttpStatus.FORBIDDEN, "관리자 승인 대기 중입니다."),
    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "이미 등록된 이메일입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "Refresh token이 없습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 refresh token입니다."),
    INVALID_USER(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    PASSWORD_TOO_SHORT(HttpStatus.BAD_REQUEST, "새 비밀번호는 6자 이상이어야 합니다."),
    USER_DELETED(HttpStatus.FORBIDDEN, "탈퇴한 계정입니다."),

    // User
    USER_ID_REQUIRED(HttpStatus.BAD_REQUEST, "사용자 ID가 필요합니다."),
    USER_NOT_FOUND_BY_ID(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // Camera
    CAMERA_NOT_FOUND(HttpStatus.NOT_FOUND, "카메라를 찾을 수 없습니다."),
    CAMERA_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 카메라에 대한 접근 권한이 없습니다."),
    CAMERA_NOT_CONNECTED(HttpStatus.BAD_REQUEST, "카메라가 연결되어 있지 않습니다."),

    // Event
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다."),
    EVENT_ACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트 액션을 찾을 수 없습니다."),


    // S3/Storage
    S3_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "S3 업로드에 실패했습니다."),
    S3_DOWNLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "S3 다운로드에 실패했습니다."),
    S3_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "S3 삭제에 실패했습니다."),

    // Clip Extraction
    CLIP_EXTRACTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "클립 추출에 실패했습니다."),

    // General
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}

