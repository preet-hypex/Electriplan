package com.hypex.electriplan.files.config;

import software.amazon.awssdk.services.s3.S3Client;

/** The application's own S3 client construction, for tests in other packages. */
public final class FilesConfigurationAccess {

    private FilesConfigurationAccess() {
    }

    public static S3Client client(String region, String endpoint, String accessKey, String secretKey) {
        return new FilesConfiguration().s3Client(region, endpoint, accessKey, secretKey);
    }
}
