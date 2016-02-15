package com.keedio.listener;

/**
 * Created by luca on 13/2/16.
 */
public interface FileEventListener {
    String rotated(long lastPosition);
    void handle(String line);
    void notExists();
    void handleException(Exception e);
    boolean isValid(String partialLine);

}
