package com.sds.phucth.chatstoragearch.services;

import com.sds.phucth.chatstoragearch.consts.S3Constants;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
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
    @Value("${app.s3.accessKeyId:}")
    @NonFinal
    String accessKeyId;
    @Value("${app.s3.secretAccessKey:}")
    @NonFinal
    String secretAccessKey;

    @NonFinal
    S3Client s3Client;
    
    @NonFinal
    boolean kmsAvailable = false;

    @PostConstruct
    public void initS3Client() {
        try {
            Region awsRegion = Region.of(region);
            
            // Configure credentials if provided
            if (accessKeyId != null && !accessKeyId.trim().isEmpty() && 
                secretAccessKey != null && !secretAccessKey.trim().isEmpty()) {
                AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
                s3Client = S3Client.builder()
                        .region(awsRegion)
                        .credentialsProvider(StaticCredentialsProvider.create(credentials))
                        .build();
                log.info("S3Client initialized with explicit credentials for region: {}", region);
            } else {
                s3Client = S3Client.builder()
                        .region(awsRegion)
                        .build();
                log.info("S3Client initialized with default credential chain for region: {}", region);
            }
            
            // Check KMS availability if KMS key is configured
            checkKmsAvailability();
            
        } catch (Exception e) {
            log.error("Failed to initialize S3Client for region: {}", region, e);
            throw new RuntimeException("Failed to initialize S3Client", e);
        }
    }
    
    private void checkKmsAvailability() {
        if (kmsKey.isPresent() && !kmsKey.get().trim().isEmpty()) {
            // Bật lên nếu có
            kmsAvailable = true;
            log.warn("KMS key {} is configured but KMS encryption is disabled to avoid permission issues. Using AES256 encryption instead.", kmsKey.get());
        } else {
            kmsAvailable = false;
            log.info("No KMS key configured, will use AES256 encryption");
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
            
            // Use KMS encryption if available, otherwise use AES256
            if (kmsAvailable && kmsKey.isPresent()) {
                requestBuilder
                    .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                    .ssekmsKeyId(kmsKey.get());
                log.debug("Using KMS encryption with key: {}", kmsKey.get());
            } else {
                requestBuilder.serverSideEncryption(ServerSideEncryption.AES256);
                log.debug("Using AES256 encryption");
            }
            
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
