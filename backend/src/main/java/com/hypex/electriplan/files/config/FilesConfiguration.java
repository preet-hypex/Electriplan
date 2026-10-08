package com.hypex.electriplan.files.config;

import java.net.URI;

import com.hypex.electriplan.files.service.FileStore;
import com.hypex.electriplan.files.service.S3FileStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * The S3 client and bucket. In AWS: the bucket and region, and credentials
 * from the environment (an IAM role, normally). Locally: the docker-compose
 * "s3" service, by endpoint, with any key. See README "File storage".
 */
@Configuration(proxyBeanMethods = false)
public class FilesConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    S3Client s3Client(@Value("${electriplan.files.s3.region:ap-southeast-2}") String region,
                      @Value("${electriplan.files.s3.endpoint:}") String endpoint,
                      @Value("${electriplan.files.s3.access-key:}") String accessKey,
                      @Value("${electriplan.files.s3.secret-key:}") String secretKey) {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(credentials(accessKey, secretKey));
        if (!endpoint.isBlank()) {
            // An S3-compatible store at its own address, which wants bucket names in the path.
            builder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(FileStore.class)
    FileStore fileStore(S3Client s3, @Value("${electriplan.files.s3.bucket:electriplan-files}") String bucket) {
        return new S3FileStore(s3, bucket);
    }

    private static AwsCredentialsProvider credentials(String accessKey, String secretKey) {
        return accessKey.isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
}
