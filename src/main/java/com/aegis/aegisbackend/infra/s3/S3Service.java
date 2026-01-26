package com.aegis.aegisbackend.infra.s3;

import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.UUID;

/**
 * S3/MinIO 스토리지 서비스
 * - 이벤트 클립 업로드/다운로드/삭제
 * - 이벤트 = 메타데이터 + 클립이 함께 있는 단일 객체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    /**
     * 이벤트 클립 업로드
     * @param eventId 이벤트 ID
     * @param clipData 클립 바이트 데이터
     * @param contentType MIME 타입 (video/mp2t)
     * @return 저장된 클립 URL (events/{eventId}/clip.ts)
     */
    public String uploadEventClip(UUID eventId, byte[] clipData, String contentType) {
        String key = buildEventClipKey(eventId);

        log.info("S3 업로드 시작: bucket={}, key={}, size={}KB, contentType={}",
                bucketName, key, clipData.length / 1024, contentType);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(clipData));
            log.info("S3 업로드 완료: eventId={}, key={}, size={}KB", eventId, key, clipData.length / 1024);

            return key;
        } catch (S3Exception e) {
            log.error("S3 업로드 실패: eventId={}, bucket={}, key={}, error={}",
                    eventId, bucketName, key, e.getMessage(), e);
            throw new BusinessException(ErrorCode.S3_UPLOAD_FAILED);
        } catch (Exception e) {
            log.error("S3 업로드 중 예외 발생: eventId={}, error={}", eventId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.S3_UPLOAD_FAILED);
        }
    }

    /**
     * 클립 다운로드 (clipUrl로 직접 다운로드)
     * @param clipUrl 클립 URL (events/{eventId}/clip.mp4)
     * @return 클립 바이트 데이터 (없으면 null)
     */
    public byte[] downloadClip(String clipUrl) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(clipUrl)
                    .build();

            return s3Client.getObjectAsBytes(request).asByteArray();
        } catch (NoSuchKeyException e) {
            log.warn("클립을 찾을 수 없음: {}", clipUrl);
            return null;
        } catch (S3Exception e) {
            log.error("클립 다운로드 실패: clipUrl={}, error={}", clipUrl, e.getMessage());
            throw new BusinessException(ErrorCode.S3_DOWNLOAD_FAILED);
        }
    }

    /**
     * 클립 삭제 (clipUrl로 직접 삭제)
     * @param clipUrl 클립 URL (events/{eventId}/clip.mp4)
     */
    public void deleteClip(String clipUrl) {
        if (clipUrl == null || clipUrl.isEmpty()) {
            log.debug("삭제할 클립 URL이 없음");
            return;
        }

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(clipUrl)
                    .build();

            s3Client.deleteObject(request);
            log.info("클립 삭제 완료: {}", clipUrl);
        } catch (S3Exception e) {
            log.error("클립 삭제 실패: clipUrl={}, error={}", clipUrl, e.getMessage());
            throw new BusinessException(ErrorCode.S3_DELETE_FAILED);
        }
    }

    /**
     * 이벤트 클립 존재 여부 확인
     */
    public boolean clipExists(UUID eventId) {
        String key = buildEventClipKey(eventId);

        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private String buildEventClipKey(UUID eventId) {
        return "events/" + eventId + "/clip.mp4";
    }
}
