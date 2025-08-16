package com.fund.stockProject.global.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    @Value("${app.aws.s3.bucket}")
    private String bucket;

    @Value("${app.aws.s3.region}")
    private String region;

    @Value("${app.aws.s3.public-base-url}")
    private String publicBaseUrl;

    @Value("${app.aws.credentials.access-key:}")
    private String accessKey;

    @Value("${app.aws.credentials.secret-key:}")
    private String secretKey;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"
    );

    private S3Client getClient() {
        S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
        builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        return builder.build();
    }

    public String uploadUserImage(MultipartFile file, String keyPrefix) {
        if (file == null || file.isEmpty()) return null;

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다. 허용: jpg, png, webp");
        }

        String ext = resolveExtension(contentType);
        String key = String.format("%s/%s.%s", keyPrefix, UUID.randomUUID(), ext);

        try (S3Client s3 = getClient()) {
            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3.putObject(put, RequestBody.fromBytes(file.getBytes()));
        } catch (S3Exception | IOException e) {
            log.error("S3 업로드 실패", e);
            throw new RuntimeException("이미지 업로드에 실패했습니다.");
        }

        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8).replaceAll("%2F", "/");
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl.endsWith("/") ? publicBaseUrl + encodedKey : publicBaseUrl + "/" + encodedKey;
        }
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, encodedKey);
    }

    private String resolveExtension(String contentType) {
        return switch (contentType) {
            case MediaType.IMAGE_JPEG_VALUE -> "jpg";
            case MediaType.IMAGE_PNG_VALUE -> "png";
            case "image/webp" -> "webp";
            default -> "bin";
        };
    }
}
