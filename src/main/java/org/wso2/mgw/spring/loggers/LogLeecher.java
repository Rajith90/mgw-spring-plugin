package org.wso2.mgw.spring.loggers;

import org.wso2.mgw.spring.exception.CLIExecutorException;

/**
 * A Leecher can attach to a {@link CLILogReader} and wait until a specific text is printed in the log.
 */
public class LogLeecher {

    private String text;

    private boolean textFound = false;

    private boolean forcedExit = false;

    /**
     * Initializes the Leecher with expected log.
     *
     * @param text The log line expected
     */
    public LogLeecher(String text) {
        this.text = text;
    }

    /**
     * Feed a log line to check if it matches the expected text.
     *
     * @param logLIne The log line which was read
     */
    void feedLine(String logLIne) {
        if (text.contains(logLIne)) {
            textFound = true;

            synchronized (this) {
                this.notifyAll();
            }
        }
    }

    /**
     * Exit the wait loop forcibly.
     */
    void forceExit() {
        forcedExit = true;

        synchronized (this) {
            notifyAll();
        }
    }

    /**
     * Wait until a specific log is found.
     *
     * @throws CLIExecutorException if waiting is interrupted
     */
    public void waitForText(long timeout) throws CLIExecutorException {

        long startTime = System.currentTimeMillis();

        synchronized (this) {
            while (!textFound || forcedExit) {
                try {
                    this.wait(timeout);

                    if (System.currentTimeMillis() - startTime > timeout) {
                        throw new CLIExecutorException("Timeout expired waiting for matching log");
                    }
                } catch (InterruptedException e) {
                    throw new CLIExecutorException("Error waiting for text", e);
                }
            }
        }
    }
}