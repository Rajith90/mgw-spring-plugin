package org.wso2.mgw.spring.loggers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import org.apache.mina.util.ConcurrentHashSet;

/**
 * The log reader which reads the micro-gw log and passes them to the slf4j logger.
 */
public class CLILogReader implements Runnable {
    private final Logger log = LoggerFactory.getLogger(CLILogReader.class);
    private String streamType;
    private InputStream inputStream;
    private static final String STREAM_TYPE_IN = "inputStream";
    private static final String STREAM_TYPE_ERROR = "errorStream";
    private Thread thread;
    private volatile boolean running = true;

    private ConcurrentHashSet<LogLeecher> leechers = new ConcurrentHashSet<>();

    /**
     * Initialize the reader with reader name and stream to read.
     *
     * @param name The name of the reader
     * @param inputStream The input stream to read
     */
    public CLILogReader(String name, InputStream inputStream) {
        this.streamType = name;
        this.inputStream = inputStream;
    }

    /**
     * Start reading the stream.
     */
    public void start() {
        thread = new Thread(this);
        thread.start();
    }

    /**
     * Stop reading the stream.
     */
    public void stop() {
        running = false;

        for (LogLeecher leecher : leechers) {
            leecher.forceExit();
        }
    }

    /**
     * Add a Leecher to this log reader.
     *
     * @param leecher The Leecher instance that is going to listen to each log line for expected text
     */
    public void addLeecher(LogLeecher leecher) {
        leechers.add(leecher);
    }

    /**
     * Feed the current log line to all the leechers to validate.
     *
     * @param logLine The currently read log line
     */
    private void feedLeechers(String logLine) {
        // Not doing if (leechers.size() > 0) since it is a costly call in concurrent data structures
        for (LogLeecher leecher : leechers) {
            leecher.feedLine(logLine);
        }
    }

    /**
     * This will get executed when log reading is started.
     */
    public void run() {
        InputStreamReader inputStreamReader = null;
        BufferedReader bufferedReader = null;
        try {
            inputStreamReader = new InputStreamReader(inputStream, Charset.defaultCharset());
            bufferedReader = new BufferedReader(inputStreamReader);
            while (running) {
                if (bufferedReader.ready()) {
                    String s = bufferedReader.readLine();
                    if (s == null) {
                        break;
                    }
                    if (STREAM_TYPE_IN.equals(streamType)) {
                        feedLeechers(s);
                        log.info(s);
                    } else if (STREAM_TYPE_ERROR.equals(streamType)) {
                        log.error(s);
                    }
                } else {
                    TimeUnit.MILLISECONDS.sleep(1);
                }
            }
        } catch (Exception ex) {
            log.error("Problem reading the [" + streamType + "] due to: " + ex.getMessage(), ex);
        } finally {
            if (inputStreamReader != null) {
                try {
                    inputStream.close();
                    inputStreamReader.close();
                } catch (IOException e) {
                    log.error("Error occurred while closing the server log stream: " + e.getMessage(), e);
                }
            }
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    log.error("Error occurred while closing the server log stream: " + e.getMessage(), e);
                }
            }
        }
    }
}
