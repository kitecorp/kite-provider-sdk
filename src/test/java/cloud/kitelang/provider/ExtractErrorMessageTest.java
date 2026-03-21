package cloud.kitelang.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link ProviderServiceImpl#extractErrorMessage(Exception)}.
 * Verifies that cloud SDK error details are properly extracted from exceptions
 * via reflection, and that fallback paths work correctly.
 */
class ExtractErrorMessageTest {

    @Test
    @DisplayName("should return exception message for regular exceptions")
    void shouldReturnMessageForRegularException() {
        var exception = new RuntimeException("Something went wrong");

        var actual = ProviderServiceImpl.extractErrorMessage(exception);

        assertEquals("Something went wrong", actual);
    }

    @Test
    @DisplayName("should return class name when exception message is null")
    void shouldReturnClassNameWhenMessageIsNull() {
        var exception = new RuntimeException((String) null);

        var actual = ProviderServiceImpl.extractErrorMessage(exception);

        assertEquals("RuntimeException", actual);
    }

    @Test
    @DisplayName("should return cause info when exception message is blank")
    void shouldReturnCauseInfoWhenMessageIsBlank() {
        var cause = new IllegalArgumentException("Invalid bucket name");
        var exception = new RuntimeException("  ", cause);

        var actual = ProviderServiceImpl.extractErrorMessage(exception);

        assertEquals("IllegalArgumentException: Invalid bucket name", actual);
    }

    @Test
    @DisplayName("should extract AWS error details from exception with awsErrorDetails method")
    void shouldExtractAwsErrorDetails() {
        var exception = new FakeAwsException(
                "IllegalLocationConstraintException",
                "The unspecified location constraint is incompatible",
                400
        );

        var actual = ProviderServiceImpl.extractErrorMessage(exception);

        assertEquals("[IllegalLocationConstraintException] The unspecified location constraint is incompatible (HTTP 400)", actual);
    }

    @Test
    @DisplayName("should handle AWS exception with empty error message by falling back to exception message")
    void shouldHandleEmptyAwsErrorMessage() {
        var exception = new FakeAwsException(
                "BucketAlreadyExists",
                null,  // empty error message
                409
        );
        // Override getMessage to return the SDK-style message
        var exWithMessage = new FakeAwsExceptionWithMessage(
                "BucketAlreadyExists",
                null,
                409,
                " (Service: S3, Status Code: 409, Request ID: ABC123)"
        );

        var actual = ProviderServiceImpl.extractErrorMessage(exWithMessage);

        assertEquals("[BucketAlreadyExists]  (Service: S3, Status Code: 409, Request ID: ABC123) (HTTP 409)", actual);
    }

    @Test
    @DisplayName("should handle AWS exception with only error code")
    void shouldHandleAwsExceptionWithOnlyErrorCode() {
        var exception = new FakeAwsException("AccessDenied", null, null);

        var actual = ProviderServiceImpl.extractErrorMessage(exception);

        assertEquals("[AccessDenied]", actual);
    }

    @Test
    @DisplayName("should fall through gracefully for non-AWS exceptions")
    void shouldFallThroughForNonAwsExceptions() {
        var exception = new IllegalStateException("Connection refused");

        var actual = ProviderServiceImpl.extractErrorMessage(exception);

        assertEquals("Connection refused", actual);
    }

    /**
     * Simulates an AWS SDK exception with awsErrorDetails() method for reflection testing.
     * The real AwsServiceException is not on the classpath of kite-provider-sdk.
     */
    static class FakeAwsException extends Exception {
        private final FakeAwsErrorDetails details;

        FakeAwsException(String errorCode, String errorMessage, Integer statusCode) {
            super(errorMessage != null ? errorMessage : "");
            this.details = new FakeAwsErrorDetails(errorCode, errorMessage,
                    statusCode != null ? new FakeSdkHttpResponse(statusCode) : null);
        }

        /** Mimics AwsServiceException.awsErrorDetails() */
        @SuppressWarnings("unused")
        public FakeAwsErrorDetails awsErrorDetails() {
            return details;
        }
    }

    /**
     * Variant that allows overriding getMessage() independently.
     */
    static class FakeAwsExceptionWithMessage extends FakeAwsException {
        private final String message;

        FakeAwsExceptionWithMessage(String errorCode, String errorMessage, Integer statusCode, String message) {
            super(errorCode, errorMessage, statusCode);
            this.message = message;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    /** Simulates AwsErrorDetails */
    static class FakeAwsErrorDetails {
        private final String errorCode;
        private final String errorMessage;
        private final FakeSdkHttpResponse sdkHttpResponse;

        FakeAwsErrorDetails(String errorCode, String errorMessage, FakeSdkHttpResponse sdkHttpResponse) {
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.sdkHttpResponse = sdkHttpResponse;
        }

        @SuppressWarnings("unused")
        public String errorCode() {
            return errorCode;
        }

        @SuppressWarnings("unused")
        public String errorMessage() {
            return errorMessage;
        }

        @SuppressWarnings("unused")
        public FakeSdkHttpResponse sdkHttpResponse() {
            return sdkHttpResponse;
        }
    }

    /** Simulates SdkHttpResponse */
    static class FakeSdkHttpResponse {
        private final int statusCode;

        FakeSdkHttpResponse(int statusCode) {
            this.statusCode = statusCode;
        }

        @SuppressWarnings("unused")
        public int statusCode() {
            return statusCode;
        }
    }
}
