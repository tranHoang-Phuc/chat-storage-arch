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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

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

    S3Client s3Client = S3Client.builder().region(Region.US_EAST_1).build();

    public void putBytes(String key, byte[] bytes, String contentType) {
        PutObjectRequest.Builder b = PutObjectRequest.builder()
                .bucket(bucket).key(key)
                .contentType(contentType)
                .contentLength((long) bytes.length);
        kmsKey.ifPresent(k -> b.serverSideEncryption(ServerSideEncryption.AWS_KMS).ssekmsKeyId(k));
        s3Client.putObject(b.build(), RequestBody.fromBytes(bytes));
    }

    public byte[] getBytes(String key) {
        var resp = s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(),
                ResponseTransformer.toBytes());
        return resp.asByteArray();
    }

    public byte[] rangeGet(String key, long start, long endInclusive) {
        var req = GetObjectRequest.builder()
                .bucket(bucket).key(key)
                .range(S3Constants.Range.BYTE_FORMAT.formatted(start, endInclusive))
                .build();
        return s3Client.getObject(req, ResponseTransformer.toBytes()).asByteArray();
    }
}
