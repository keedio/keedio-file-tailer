package com.keedio.listener.impl;

import com.keedio.listener.FileEventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Created by luca on 13/2/16.
 */
public class LogFileEventListener implements FileEventListener {
    private final static Logger LOGGER = LogManager.getLogger(LogFileEventListener.class);

    @Override
    public String rotated(long lastPosition) {
        LOGGER.info("File Rotated");

        return null;
    }

    @Override
    public void handle(String line) {
        LOGGER.info("New line: "+line);
    }

    @Override
    public void notExists() {
        LOGGER.info("File does not exists");
    }

    @Override
    public void handleException(Exception e) {
        LOGGER.error("Received exception",e);
    }

    @Override
    public boolean isValid(String partialLine) {
        LOGGER.info("Calling isValid on partial line: " + partialLine);
        return true;
    }
}
