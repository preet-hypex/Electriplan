package com.hypex.electriplan.files.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import com.hypex.electriplan.files.domain.FileStorageException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/** The S3 adapter's requests, against a pretend S3 client. */
class S3FileStoreTest {

    final S3Client s3 = mock(S3Client.class);
    final S3FileStore store = new S3FileStore(s3, "electriplan-files");

    @Test
    @SuppressWarnings("unchecked")
    void putsTheObjectInTheBucketWithItsTypeAndLength() {
        when(s3.putObject(any(Consumer.class), any(RequestBody.class))).thenReturn(PutObjectResponse.builder().build());
        store.put("organisations/o1/files/abc.png", new byte[] {1, 2, 3}, "image/png");

        ArgumentCaptor<Consumer<PutObjectRequest.Builder>> request = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(request.capture(), body.capture());
        PutObjectRequest.Builder built = PutObjectRequest.builder();
        request.getValue().accept(built);
        PutObjectRequest put = built.build();
        assertThat(put.bucket()).isEqualTo("electriplan-files");
        assertThat(put.key()).isEqualTo("organisations/o1/files/abc.png");
        assertThat(put.contentType()).isEqualTo("image/png");
        assertThat(put.contentLength()).isEqualTo(3);
        assertThat(body.getValue().optionalContentLength()).contains(3L);
        assertThat(store.backend()).isEqualTo("s3");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getsTheObjectsBytesOrNothingWhenThereIsNone() {
        when(s3.getObjectAsBytes(any(Consumer.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "png".getBytes(StandardCharsets.UTF_8)));
        assertThat(store.get("k")).hasValueSatisfying(b -> assertThat(new String(b, StandardCharsets.UTF_8)).isEqualTo("png"));

        ArgumentCaptor<Consumer<GetObjectRequest.Builder>> request = ArgumentCaptor.forClass(Consumer.class);
        verify(s3).getObjectAsBytes(request.capture());
        GetObjectRequest.Builder built = GetObjectRequest.builder();
        request.getValue().accept(built);
        assertThat(built.build().bucket()).isEqualTo("electriplan-files");

        when(s3.getObjectAsBytes(any(Consumer.class))).thenThrow(NoSuchKeyException.builder().message("gone").build());
        assertThat(store.get("missing")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void anS3FailureIsAStorageFailure() {
        when(s3.putObject(any(Consumer.class), any(RequestBody.class))).thenThrow(SdkClientException.create("connection refused"));
        assertThatThrownBy(() -> store.put("k", new byte[] {1}, "image/png"))
                .isInstanceOf(FileStorageException.class).hasMessageContaining("could not be stored");
        when(s3.getObjectAsBytes(any(Consumer.class))).thenThrow(SdkClientException.create("connection refused"));
        assertThatThrownBy(() -> store.get("k")).isInstanceOf(FileStorageException.class).hasMessageContaining("could not be read");
    }
}
