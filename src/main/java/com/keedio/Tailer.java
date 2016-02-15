package com.keedio;

import com.keedio.exception.TailerException;
import com.keedio.listener.FileEventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Created by luca on 13/2/16.
 */
public class Tailer implements Runnable {
    private final static Logger LOGGER = LogManager.getLogger(Tailer.class);

    private FileEventListener listener;
    private long sleepTime;
    private String filename;
    private long position = 0;
    private long lastFullLinePosition = 0;

    private long creationTime;

    public Tailer(FileEventListener listener, long sleepTime, String filename) {
        this.listener = listener;
        this.sleepTime = sleepTime;
        this.filename = filename;
    }

    @Override
    public void run() {

        if (listener == null) {
            throw new TailerException(new IllegalStateException("Listener cannot be null"));
        }

        File file = new File(filename);

        if (!file.exists()) {
            listener.notExists();
            throw new TailerException(new FileNotFoundException(filename + " does not exists"));
        }

        boolean reopen = true;

        while (reopen){
            reopen = handleFile(file);

            sleepSilently(sleepTime);
        }
    }

    private void handleRotatedFile(String rotatedFileName) throws IOException {
        if (rotatedFileName == null){
            return;
        }

        File rotatedFile = new File(rotatedFileName);

        if (!rotatedFile.exists()){
            return;
        }

        LOGGER.debug("Handling rotated file '"+rotatedFileName+"' starting at position: "+lastFullLinePosition);

        long rotatedPosition = lastFullLinePosition;

        try (BufferedReader reader = new BufferedReader(new FileReader(rotatedFile))) {
            reader.skip(rotatedPosition);

            String currentLine;
            StringBuffer buffer = new StringBuffer();

            while ((currentLine = reader.readLine()) != null) {
                buffer.append(currentLine);
                if (listener.isValid(buffer.toString())){
                    listener.handle(currentLine);

                    buffer = new StringBuffer();
                }

                rotatedPosition += currentLine.length();
            }
        }
    }

    private boolean handleFile(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {

            creationTime = getCreationTime(file);

            while (true) {

                long newCreationTime = getCreationTime(file);

                if (file.length()+1 < position || newCreationTime > creationTime){
                    // file rotated
                    handleRotatedFile(listener.rotated(lastFullLinePosition));

                    position = 0;

                    return true;
                }

                String currentLine;
                StringBuffer buffer = new StringBuffer();

                while ((currentLine = reader.readLine()) != null) {
                    buffer.append(currentLine);

                    // update position taking into account carriage return
                    position += currentLine.length() + 1;

                    if (listener.isValid(buffer.toString())){
                        listener.handle(currentLine);

                        lastFullLinePosition = position;
                        buffer = new StringBuffer();
                    }
                }

                sleepSilently(sleepTime);
            }

        } catch (NoSuchFileException e){
          return true;
        } catch (Exception e) {
            listener.handleException(e);
            throw new TailerException(e);
        }

    }

    private long getCreationTime(File file) throws IOException {
        BasicFileAttributes attr =
                Files.readAttributes(Paths.get(file.toURI()),
                        BasicFileAttributes.class);

        return attr.creationTime().toMillis();
    }

    public static void sleepSilently(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            LOGGER.error(e);
        }
    }
}
