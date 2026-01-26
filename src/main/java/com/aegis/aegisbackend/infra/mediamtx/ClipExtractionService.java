package com.aegis.aegisbackend.infra.mediamtx;

import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.infra.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 클립 추출 서비스
 * - MediaMTX HLS에서 fMP4 세그먼트(.m4s) 다운로드
 * - init.mp4 + 세그먼트 합쳐서 MinIO에 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClipExtractionService {

    private final S3Service s3Service;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${mediamtx.hls-url:http://localhost:8888}")
    private String hlsBaseUrl;

    @Value("${clip.extraction.segment-count:10}")
    private int defaultSegmentCount;

    // fMP4 세그먼트 패턴 (.m4s)
    private static final Pattern SEGMENT_PATTERN = Pattern.compile("^([^#\\s].+\\.m4s)$", Pattern.MULTILINE);
    // 초기화 세그먼트 패턴 (#EXT-X-MAP:URI="init.mp4")
    private static final Pattern INIT_SEGMENT_PATTERN = Pattern.compile("#EXT-X-MAP:URI=\"([^\"]+)\"");
    private static final Pattern STREAM_PLAYLIST_PATTERN = Pattern.compile("^([^#\\s].+\\.m3u8)$", Pattern.MULTILINE);

    public String extractAndSaveClip(String cameraName, UUID eventId) {
        return extractAndSaveClip(cameraName, eventId, defaultSegmentCount);
    }

    public String extractAndSaveClip(String cameraName, UUID eventId, int segmentCount) {
        try {
            // 1. 마스터 플레이리스트 조회
            String masterUrl = hlsBaseUrl + "/" + cameraName + "/index.m3u8";
            String masterPlaylist = httpGet(masterUrl);

            if (masterPlaylist == null || masterPlaylist.isEmpty()) {
                throw new BusinessException(ErrorCode.CLIP_EXTRACTION_FAILED,
                        "마스터 플레이리스트 조회 실패: " + cameraName);
            }

            // 2. 스트림 플레이리스트 이름 추출
            String streamPlaylistName = parseStreamPlaylistName(masterPlaylist);
            if (streamPlaylistName == null) {
                throw new BusinessException(ErrorCode.CLIP_EXTRACTION_FAILED,
                        "스트림 플레이리스트를 찾을 수 없음: " + cameraName);
            }

            // 3. 스트림 플레이리스트 조회
            String streamUrl = hlsBaseUrl + "/" + cameraName + "/" + streamPlaylistName;
            String playlist = httpGet(streamUrl);

            if (playlist == null || playlist.isEmpty()) {
                throw new BusinessException(ErrorCode.CLIP_EXTRACTION_FAILED,
                        "스트림 플레이리스트 조회 실패: " + streamUrl);
            }

            // 4. 초기화 세그먼트 (init.mp4) 파싱 - fMP4 필수
            String initSegmentName = parseInitSegmentName(playlist);

            // 5. 세그먼트 목록 추출
            List<String> segments = parseSegmentNames(playlist);
            if (segments.isEmpty()) {
                throw new BusinessException(ErrorCode.CLIP_EXTRACTION_FAILED,
                        "세그먼트를 찾을 수 없음: " + cameraName);
            }

            // 6. 최신 N개 선택
            int start = Math.max(0, segments.size() - segmentCount);
            List<String> targetSegments = segments.subList(start, segments.size());

            log.info("클립 추출: camera={}, segments={}, init={}",
                    cameraName, targetSegments.size(), initSegmentName);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int success = 0;

            // 7. fMP4: 초기화 세그먼트 먼저 다운로드 (필수)
            if (initSegmentName != null) {
                String initUrl = hlsBaseUrl + "/" + cameraName + "/" + initSegmentName;
                byte[] initData = httpGetBytes(initUrl);
                if (initData != null && initData.length > 0) {
                    out.write(initData);
                    log.debug("초기화 세그먼트: {} ({}KB)", initSegmentName, initData.length / 1024);
                }
            }

            // 8. 미디어 세그먼트 다운로드
            for (String seg : targetSegments) {
                String segUrl = hlsBaseUrl + "/" + cameraName + "/" + seg;
                byte[] data = httpGetBytes(segUrl);

                if (data != null && data.length > 0) {
                    out.write(data);
                    success++;
                    log.debug("세그먼트: {} ({}KB)", seg, data.length / 1024);
                } else {
                    log.warn("세그먼트 다운로드 실패: {}", seg);
                }
            }

            if (success == 0) {
                throw new BusinessException(ErrorCode.CLIP_EXTRACTION_FAILED,
                        "모든 세그먼트 다운로드 실패");
            }

            // 9. MinIO 업로드 (fMP4 = video/mp4)
            byte[] clipData = out.toByteArray();
            String clipKey = s3Service.uploadEventClip(eventId, clipData, "video/mp4");

            log.info("클립 저장 완료: camera={}, event={}, size={}KB",
                    cameraName, eventId, clipData.length / 1024);

            return clipKey;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("클립 추출 실패: camera={}, error={}", cameraName, e.getMessage(), e);
            throw new BusinessException(ErrorCode.CLIP_EXTRACTION_FAILED);
        }
    }

    /**
     * 초기화 세그먼트 이름 추출 (fMP4)
     */
    private String parseInitSegmentName(String playlist) {
        Matcher m = INIT_SEGMENT_PATTERN.matcher(playlist);
        return m.find() ? m.group(1) : null;
    }


    private String parseStreamPlaylistName(String masterPlaylist) {
        Matcher m = STREAM_PLAYLIST_PATTERN.matcher(masterPlaylist);
        return m.find() ? m.group(1) : null;
    }

    private List<String> parseSegmentNames(String playlist) {
        List<String> list = new ArrayList<>();
        Matcher m = SEGMENT_PATTERN.matcher(playlist);
        while (m.find()) {
            list.add(m.group(1));
        }
        return list;
    }

    /**
     * HTTP GET - 텍스트 응답
     */
    private String httpGet(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 ? res.body() : null;
        } catch (Exception e) {
            log.error("HTTP GET 실패: {}, error={}", url, e.getMessage());
            return null;
        }
    }

    /**
     * HTTP GET - 바이너리 응답
     */
    private byte[] httpGetBytes(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<byte[]> res = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            return res.statusCode() == 200 ? res.body() : null;
        } catch (Exception e) {
            log.error("HTTP GET 실패: {}, error={}", url, e.getMessage());
            return null;
        }
    }
}
