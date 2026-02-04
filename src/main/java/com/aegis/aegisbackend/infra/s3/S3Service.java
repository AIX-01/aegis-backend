package com.aegis.aegisbackend.infra.s3;

import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * S3/MinIO 스토리지 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${clip.path:clips}")
    private String clipPath;

    @Value("${clip.temp-path:temp/clips}")
    private String tempClipPath;

    @Value("${clip.presigned-url-expiration:3600}")
    private int presignedUrlExpiration;

    /**
     * 클립 업로드용 presigned PUT URL 생성
     */
    public String generateUploadUrl(UUID eventId) {
        String key = clipPath + "/" + eventId + ".mp4";

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("video/mp4")
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(putRequest)
                .build();

        String url = s3Presigner.presignPutObject(presignRequest).url().toString();
        log.debug("업로드 presigned URL 생성: eventId={}", eventId);
        return url;
    }

    /**
     * 클립 다운로드용 presigned GET URL 생성 (Caddy 프록시 경로로 변환)
     */
    public String generateDownloadUrl(UUID eventId) {
        String key = clipPath + "/" + eventId + ".mp4";

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(presignedUrlExpiration))
                .getObjectRequest(getRequest)
                .build();

        String minioUrl = s3Presigner.presignGetObject(presignRequest).url().toString();

        // MinIO URL을 Caddy 프록시 경로로 변환
        // 예: http://localhost:9000/aegis/clips/xxx.mp4?... → /clips/xxx.mp4?...
        String clipUrl = minioUrl.replaceFirst(".*?/" + bucketName + "/", "/");
        log.debug("다운로드 presigned URL 생성: eventId={}", eventId);
        return clipUrl;
    }

    /**
     * temp/clips/{eventId}.mp4 존재 확인
     */
    public boolean tempClipExists(UUID eventId) {
        String key = tempClipPath + "/" + eventId + ".mp4";
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

    /**
     * temp/clips/{eventId}.mp4 → clips/{eventId}.mp4 이동
     */
    public String moveClipFromTemp(UUID eventId) {
        String sourceKey = tempClipPath + "/" + eventId + ".mp4";
        String destKey = clipPath + "/" + eventId + ".mp4";

        try {
            // 복사
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(sourceKey)
                    .destinationBucket(bucketName)
                    .destinationKey(destKey)
                    .build();
            s3Client.copyObject(copyRequest);

            // 원본 삭제
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(sourceKey)
                    .build();
            s3Client.deleteObject(deleteRequest);

            log.info("클립 이동 완료: {} → {}", sourceKey, destKey);
            return destKey;
        } catch (S3Exception e) {
            log.error("클립 이동 실패: eventId={}, error={}", eventId, e.getMessage());
            throw new BusinessException(ErrorCode.S3_UPLOAD_FAILED);
        }
    }

    /**
     * temp/clips/ 전체 삭제
     */
    public void cleanupTempClips() {
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(tempClipPath + "/")
                    .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
            List<S3Object> objects = listResponse.contents();

            if (objects.isEmpty()) {
                log.debug("정리할 임시 클립 없음");
                return;
            }

            List<ObjectIdentifier> toDelete = objects.stream()
                    .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                    .toList();

            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(toDelete).build())
                    .build();

            s3Client.deleteObjects(deleteRequest);
            log.info("임시 클립 정리 완료: {}개 삭제", objects.size());
        } catch (S3Exception e) {
            log.error("임시 클립 정리 실패: {}", e.getMessage());
        }
    }

    /**
     * 클립 다운로드
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
     * 클립 삭제
     */
    public void deleteClip(String clipUrl) {
        if (clipUrl == null || clipUrl.isEmpty()) {
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
     * 클립 존재 여부 확인
     */
    public boolean clipExists(UUID eventId) {
        String key = clipPath + "/" + eventId + ".mp4";
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
}
