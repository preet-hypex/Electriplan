package com.hypex.electriplan.files.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.hypex.electriplan.files.config.FilesConfigurationAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * The S3 adapter against a real S3-compatible store (docker-compose's "s3",
 * or CI's), through the same client the application builds: the AWS SDK,
 * path-style addressing, any key. Runs when APP_TEST_S3_ENDPOINT is set.
 */
@EnabledIfEnvironmentVariable(named = "APP_TEST_S3_ENDPOINT", matches = "https?://.+")
class S3FileStoreRealS3Tests {

    static final String BUCKET = "electriplan-files";

    @Test
    void storesAndReadsBackAnObject() {
        try (S3Client s3 = FilesConfigurationAccess.client("ap-southeast-2", System.getenv("APP_TEST_S3_ENDPOINT"), "local", "local")) {
            S3FileStore store = new S3FileStore(s3, BUCKET);
            String key = "organisations/test/files/" + UUID.randomUUID() + ".png";
            byte[] bytes = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

            store.put(key, bytes, "image/png");

            assertThat(store.get(key)).hasValueSatisfying(b -> assertThat(b).isEqualTo(bytes));
            assertThat(s3.headObject(b -> b.bucket(BUCKET).key(key)).contentType()).isEqualTo("image/png");
            assertThat(store.get("organisations/test/files/missing.png")).isEmpty();
        }
    }
}
