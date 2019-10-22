package org.wso2.mgw.spring.exception;

/**
 * Exception to be thrown when generating openAPI fails from the spring service
 */
public class OpenAPIBuilderException extends Exception {

    public OpenAPIBuilderException(String message) {
        super(message);
    }

    public OpenAPIBuilderException(String message, Throwable cause) {
        super(message, cause);
    }
}
