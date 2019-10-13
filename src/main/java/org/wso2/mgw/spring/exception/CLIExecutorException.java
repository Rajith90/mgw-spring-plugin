package org.wso2.mgw.spring.exception;

/**
 * Exception to be thrown by cli execution
 */
public class CLIExecutorException extends Exception {

    public CLIExecutorException(String message) {
        super(message);
    }

    public CLIExecutorException(String message, Throwable cause) {
        super(message, cause);
    }
}

