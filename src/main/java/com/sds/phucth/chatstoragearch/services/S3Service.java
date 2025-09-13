package com.sds.phucth.chatstoragearch.services;

import com.sds.phucth.chatstoragearch.consts.S3Constants;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import jakarta.annotation.PostConstruct;
import java.util.Optional;
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class S3Service {
    @Value("${app.s3.bucket}")
    @NonFinal
    String bucket;
    @Value("${app.s3.region}")
    @NonFinal
    String region;
    @Value("${app.s3.kmsKeyId:}")
    @NonFinal
    Optional<String> kmsKey;
    @Value("${app.s3.prefix}")
    @NonFinal
    String prefix;

    @NonFinal
    S3Client s3Client;

    @PostConstruct
    public void initS3Client() {
        try {
            Region awsRegion = Region.of(region);
            s3Client = S3Client.builder()
                    .region(awsRegion)
                    .build();
            log.info("S3Client initialized for region: {}", region);
        } catch (Exception e) {
            log.error("Failed to initialize S3Client for region: {}", region, e);
            throw new RuntimeException("Failed to initialize S3Client", e);
        }
    }

    public void putBytes(String key, byte[] bytes, String contentType) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("S3 key cannot be null or empty");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Bytes cannot be null or empty");
        }
        if (contentType == null || contentType.trim().isEmpty()) {
            throw new IllegalArgumentException("Content type cannot be null or empty");
        }

        try {
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) bytes.length);
            
            kmsKey.ifPresent(k -> requestBuilder
                    .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                    .ssekmsKeyId(k));
            
            s3Client.putObject(requestBuilder.build(), RequestBody.fromBytes(bytes));
            log.debug("Successfully uploaded {} bytes to S3 key: {}", bytes.length, key);
            
        } catch (S3Exception e) {
            log.error("S3 error uploading to key {}: {}", key, e.getMessage(), e);
            throw new RuntimeException("Failed to upload to S3: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error uploading to S3 key {}: {}", key, e.getMessage(), e);
            throw new RuntimeException("Failed to upload to S3", e);
        }
    }

    public byte[] getBytes(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("S3 key cannot be null or empty");
        }

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            
            var response = s3Client.getObject(request, ResponseTransformer.toBytes());
            byte[] result = response.asByteArray();
            log.debug("Successfully downloaded {} bytes from S3 key: {}", result.length, key);
            return result;
            
        } catch (NoSuchKeyException e) {
            log.warn("Object not found in S3: {}", key);
            throw new RuntimeException("Object not found: " + key, e);
        } catch (S3Exception e) {
            log.error("S3 error downloading key {}: {}", key, e.getMessage(), e);
            throw new RuntimeException("Failed to download from S3: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error downloading from S3 key {}: {}", key, e.getMessage(), e);
            throw new RuntimeException("Failed to download from S3", e);
        }
    }

    public byte[] rangeGet(String key, long start, long endInclusive) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("S3 key cannot be null or empty");
        }
        if (start < 0) {
            throw new IllegalArgumentException("Start position cannot be negative");
        }
        if (endInclusive < start) {
            throw new IllegalArgumentException("End position cannot be less than start position");
        }

        try {
            String range = S3Constants.Range.BYTE_FORMAT.formatted(start, endInclusive);
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .range(range)
                    .build();
            
            var response = s3Client.getObject(request, ResponseTransformer.toBytes());
            byte[] result = response.asByteArray();
            log.debug("Successfully downloaded range {} from S3 key: {} ({} bytes)", range, key, result.length);
            return result;
            
        } catch (NoSuchKeyException e) {
            log.warn("Object not found in S3 for range request: {}", key);
            throw new RuntimeException("Object not found: " + key, e);
        } catch (S3Exception e) {
            log.error("S3 error downloading range [{}, {}] from key {}: {}", start, endInclusive, key, e.getMessage(), e);
            throw new RuntimeException("Failed to download range from S3: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error downloading range from S3 key {}: {}", key, e.getMessage(), e);
            throw new RuntimeException("Failed to download range from S3", e);
        }
    }
}
