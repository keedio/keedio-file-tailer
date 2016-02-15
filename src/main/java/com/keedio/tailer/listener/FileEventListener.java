package com.keedio.tailer.listener;

/**
 * Common interface to be implemented to listen for events produced by {@see com.keedio.tailer.Tailer}.
 *
 * Created by luca on 13/2/16.
 */
public interface FileEventListener {

    /**
     * handles a rotation event.
     *
     * @param lastPosition the position in the file (before being rotated) of the last fully read line.
     * @param currPosition the postion in the file of the last char read.
     * @return (optional) the name of the rotated file in order to let the Tailer properly process missing lines.
     */
    String rotated(long lastPosition, long currPosition);

    /**
     * Called by the tailer when a full valid line is detected.
     *
     * @param line a valid line.
     */
    void handle(String line);

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
     * {@see com.keedio.tailer.Tailer} maintains a buffer where the returning values of successive calls to {@see java.io.BufferedReader#readLine()}
     * are accumulated. At each iteration {@see com.keedio.tailer.Tailer} call this method to check if the buffer contains a valid line.
     * </p>
     *
     * @param partialLine the line to be validated.
     * @return true if the line is valid, false otherwise.
     */
    boolean isValid(String partialLine);

}
