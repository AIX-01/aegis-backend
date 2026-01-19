package com.aegis.aegisbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    /**
     * 이벤트 클립 업로드
     */
    public String uploadEventClip(UUID eventId, byte[] clipData, String contentType) {
        String key = buildEventClipKey(eventId);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(clipData));
            log.info("Uploaded clip for event: {}", eventId);

            return key;
        } catch (S3Exception e) {
            log.error("Failed to upload clip for event {}: {}", eventId, e.getMessage());
            throw new RuntimeException("Failed to upload clip", e);
        }
    }

    /**
     * 이벤트 클립 업로드 (InputStream)
     */
    public String uploadEventClip(UUID eventId, InputStream inputStream, long contentLength, String contentType) {
        String key = buildEventClipKey(eventId);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
            log.info("Uploaded clip for event: {}", eventId);

            return key;
        } catch (S3Exception e) {
            log.error("Failed to upload clip for event {}: {}", eventId, e.getMessage());
            throw new RuntimeException("Failed to upload clip", e);
        }
    }

    /**
     * 이벤트 클립 다운로드
     */
    public byte[] downloadEventClip(UUID eventId) {
        String key = buildEventClipKey(eventId);

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObjectAsBytes(request).asByteArray();
        } catch (NoSuchKeyException e) {
            log.warn("Clip not found for event: {}", eventId);
            return null;
        } catch (S3Exception e) {
            log.error("Failed to download clip for event {}: {}", eventId, e.getMessage());
            throw new RuntimeException("Failed to download clip", e);
        }
    }

    /**
     * 이벤트 클립 삭제
     */
    public void deleteEventClip(UUID eventId) {
        String key = buildEventClipKey(eventId);

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(request);
            log.info("Deleted clip for event: {}", eventId);
        } catch (S3Exception e) {
            log.error("Failed to delete clip for event {}: {}", eventId, e.getMessage());
            throw new RuntimeException("Failed to delete clip", e);
        }
    }

    /**
     * 클립 존재 여부 확인
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

    /**
     * 스토리지 사용량 계산 (GB 단위)
     */
    public long getUsedStorageGB() {
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();

            long totalBytes = 0;
            ListObjectsV2Response response;

            do {
                response = s3Client.listObjectsV2(request);
                for (S3Object object : response.contents()) {
                    totalBytes += object.size();
                }
                request = request.toBuilder()
                        .continuationToken(response.nextContinuationToken())
                        .build();
            } while (response.isTruncated());

            return totalBytes / (1024 * 1024 * 1024); // Convert to GB
        } catch (S3Exception e) {
            log.error("Failed to calculate storage usage: {}", e.getMessage());
            return 0;
        }
    }

    private String buildEventClipKey(UUID eventId) {
        return "events/" + eventId + "/clip.mp4";
    }
}

