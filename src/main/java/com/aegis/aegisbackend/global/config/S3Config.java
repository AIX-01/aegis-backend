package com.aegis.aegisbackend.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Slf4j
@Configuration
public class S3Config {

    @Value("${aws.s3.access-key}")
    private String accessKey;

    @Value("${aws.s3.secret-key}")
    private String secretKey;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.endpoint:}")
    private String endpoint;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${clip.download-endpoint:https://localhost}")
    private String downloadEndpoint;

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ));

        // MinIO 또는 custom S3 호환 endpoint 설정
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true);
        }

        S3Client client = builder.build();

        checkBucketExists(client);

        return client;
    }

    private void checkBucketExists(S3Client client) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            log.info("S3 버킷 확인 완료: {}", bucketName);
        } catch (NoSuchBucketException e) {
            log.info("S3 버킷이 존재하지 않아 생성합니다: {}", bucketName);
            createBucket(client);
        } catch (Exception e) {
            log.warn("S3 버킷 확인 중 오류: {}", e.getMessage());
        }
    }

    private void createBucket(S3Client client) {
        try {
            client.createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            log.info("S3 버킷 생성 완료: {}", bucketName);
        } catch (Exception e) {
            log.error("S3 버킷 생성 실패: {}", e.getMessage());
        }
    }

    @Bean
    public S3Presigner s3Presigner() {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ));

        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build());
        }

        return builder.build();
    }

    /**
     * 다운로드용 Presigner (Caddy 프록시 도메인으로 서명)
     */
    @Bean("downloadPresigner")
    public S3Presigner downloadPresigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .endpointOverride(URI.create(downloadEndpoint))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
