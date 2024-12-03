package dev.ddanny165.iotCamera.exceptions;

public class DynamoDbServiceException extends Exception {
    public DynamoDbServiceException() {
    }

    public DynamoDbServiceException(String message) {
        super(message);
    }

    public DynamoDbServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
