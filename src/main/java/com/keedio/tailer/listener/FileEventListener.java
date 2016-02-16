package com.keedio.tailer.listener;

import com.keedio.tailer.LRTailer;

/**
 * Common interface to be implemented to listen for events produced by {@link com.keedio.tailer.LRTailer}.
 *
 * Created by luca on 13/2/16.
 */
public interface FileEventListener {

    /**
     * Init hook.
     *
     * @param lrTailer the tailer this listener will be registered to.
     */
    void init(LRTailer lrTailer);

    /**
     * handles a rotation event.
     *
     * @param lastPosition the position in the file (before being rotated) of the last fully read line.
     * @param currPosition the position in the file of the last char read.
     * @return (optional) the name of the rotated file in order to let the LRTailer properly process missing lines.
     */
    String rotated(long lastPosition, long currPosition);

    /**
     * Called by the tailer when a full valid line is detected.
     *
     * @param line a valid line.
     */
    void handle(String filename, String line);

    /**
     * Called when the file to tail does not exists.
     */
    void notExists();

    /**
     * Called when an un-recoverable exception occurred while tailine the file.
     *
     * @param e the thrown exception.
     */
    void handleException(Exception e);

    /**
     * <p>
     * {@link com.keedio.tailer.LRTailer} maintains a buffer where the returning values of successive calls to {@link java.io.BufferedReader#readLine()}
     * are accumulated. At each iteration {@link com.keedio.tailer.LRTailer} call this method to check if the buffer contains a valid line.
     * </p>
     *
     * @param partialLine the line to be validated.
     * @return true if the line is valid, false otherwise.
     */
    boolean isValid(String partialLine);

}
