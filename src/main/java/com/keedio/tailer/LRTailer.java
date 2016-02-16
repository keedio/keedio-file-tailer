package com.keedio.tailer;

import com.keedio.tailer.exception.TailerException;
import com.keedio.tailer.listener.FileEventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * <p>
 *     Tails a single file for changes, and notifies changes to an external listener
 *     {@link com.keedio.tailer.listener.FileEventListener}.
 * </p>
 * <p>
 *     This component tries to read full lines using a {@link java.io.BufferedReader#readLine()}. The line read can either
 *     be complete or partial (if the file generator is especially slow, for example).
 *
 *     This tailer maintains a buffer where the returning values of successive calls to {@link java.io.BufferedReader#readLine()}
 *     are accumulated. At each iteration we validate if the buffer contains valid line.
 *     Line validation logic is delegated to {@link com.keedio.tailer.listener.FileEventListener#isValid}.
 * </p>
 * <p>
 *     This component supports file rotation. This tailer keeps track of the last character read from the originally tailed file.
 *     This way, when file rotation is detected, the listener is notified, and,
 *     if the listener provides the rotated filename, the rotated file is opened and read starting from the appropiate position.
 * </p>
 * <p>
 *     Waits <code>sleepTime</code> milliseconds between line reads.
 * </p>
 *
 * Created by luca on 13/2/16.
 */
public class LRTailer implements Runnable {
    private final static Logger LOGGER = LogManager.getLogger(LRTailer.class);

    /* the listener that will be notified of events ocurring on the tailed file */
    private FileEventListener listener;

    /* time to sleep between successive reads */
    private long sleepTime;

    /* the name of the file to tail */
    private File file;

    /* the current offset (next read char will be position + 1 */
    private long position = 0;

    /* the position in the file of last fully read line */
    private long lastFullLinePosition = 0;

    /* timestamp of the time of creation of the tailed file. Helps in detecting file rotation */
    private long creationTime;

    private boolean run = true;

    /**
     * Builds a new tailer.
     *
     * @param listener the component that will be notified when events on the tailed file occur.
     * @param sleepTime the sleep time, in milliseconds, between line reads.
     * @param filename the name of the file to tail.
     */
    public LRTailer(FileEventListener listener, long sleepTime, String filename) {
        this.listener = listener;
        this.sleepTime = sleepTime;
        this.file = new File(filename);
        this.listener.init(this);
    }

    /**
     * Stops tailing.
     */
    public void stop(){
        run = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {

        if (!file.exists()) {
            listener.notExists();
            throw new TailerException(new FileNotFoundException(file.getAbsolutePath() + " does not exists"));
        }

        boolean reopen = true;

        while (reopen && run){
            reopen = handleFile(file);

            sleepSilently(sleepTime);
        }
    }

    /**
     * Handles the complexity of opening and reading the rotated file.
     *
     * When a rotation happens, a line could only have been read partially. This method
     * takes <code>prevBuffer</code> as a parameter, containing the last partially read line from the
     * tailed file before rotation happened.
     *
     * @param prevBuffer buffer containing the last partially read line from the originally tailed file.
     * @param rotatedFileName the name of the rotate file.
     * @throws IOException when an error occurs.
     */
    private void handleRotatedFile(StringBuffer prevBuffer, String rotatedFileName) throws IOException {
        if (rotatedFileName == null){
            return;
        }

        File rotatedFile = new File(rotatedFileName);

        if (!rotatedFile.exists()){
            return;
        }

        LOGGER.debug("Handling rotated file '"+rotatedFileName+"' starting at position: "+lastFullLinePosition);

        /* At the time of rotation, the last line of the tailed file could only have been read
         * partially. In this case position > lastFullLinePosition and prevBuffer is not empty.
         *
         * We have to keep accumulating chars in the prevBuffer in order to fully reconstruct the partially read line.
         */
        long rotatedPosition = Math.max(lastFullLinePosition, position);

        try (BufferedReader reader =
                     new BufferedReader(new FileReader(rotatedFile))) {

            reader.skip(rotatedPosition);

            String currentLine;
            StringBuffer buffer = new StringBuffer(prevBuffer.toString());

            /* keeps accumulating until a valid line is read completely */
            while ((currentLine = reader.readLine()) != null) {
                buffer.append(currentLine);
                if (listener.isValid(buffer.toString())){
                    listener.handle(rotatedFileName, currentLine);

                    buffer = new StringBuffer();
                }

                rotatedPosition += currentLine.length();
            }
        }
    }

    /**
     * Opens the file and starts tailing it. It keeps accumulating partial lines to a buffer
     * until the buffer contains a valid line.
     *
     * Line validation logic is delegated to {@link com.keedio.tailer.listener.FileEventListener#isValid}.
     *
     * @param file the file to tail.
     * @return true if something strange has happened to the tailed file and it should be re-opened.
     */
    private boolean handleFile(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            LOGGER.debug("Opened: " + file.getAbsolutePath());

            creationTime = getCreationTime(file);

            StringBuffer buffer = new StringBuffer();
            while (run) {

                try {

                    if (checkRotateCondition(buffer, file)) {
                        sleepSilently(sleepTime);
                        return true;
                    }

                    String currentLine;

                    while ((currentLine = reader.readLine()) != null) {
                        buffer.append(currentLine);

                        // update position taking into account carriage return
                        position += currentLine.length() + 1;

                        String accumulated = buffer.toString();

                        if (listener.isValid(accumulated)) {
                            listener.handle(file.getAbsolutePath(), accumulated);

                            lastFullLinePosition = position;
                            buffer = new StringBuffer();
                        }
                    }

                    sleepSilently(sleepTime);
                } catch (NoSuchFileException e) {

                    /*
                    We were processing the file peacefully an suddenly it doesn't exist anymore.
                    Maybe a file rotation ocurred, let's sleep a bit a check if it's created again.
                    */
                    sleepSilently(sleepTime);
                }
            }

        }  catch (Exception e) {
            /* Something very bad happened, aborting */
            listener.handleException(e);
            throw new TailerException(e);
        }

        return false;
    }

    /**
     * Checks if the file has rotated.
     *
     * <p></p>
     * A file is considered rotated if its size is smaller than the accumulated position and if
     * its creation time is newer than the last known creation time.
     *</p>
     * <p>
     * At the time of rotation, the last line of the tailed file could only have been read
     * partially. In this case position > lastFullLinePosition and prevBuffer is not empty.
     * </p>
     * @param prevBuffer the buffer with the partially read line before (potential) rotation occurred.
     * @param file the tailed file.
     * @return true if the file rotated, false otherwise.
     * @throws IOException if an error occurred processing the file.
     */
    private boolean checkRotateCondition(StringBuffer prevBuffer, File file) throws IOException {
        long newCreationTime = getCreationTime(file);

        if (file.length()+1 < position && newCreationTime > creationTime){
            // file rotated
            handleRotatedFile(prevBuffer,
                    listener.rotated(lastFullLinePosition, position));

            position = 0;

            return true;
        }
        return false;
    }

    /**
     * Returns the creation time of the given file.
     *
     * @param file the file whose creation time should be retrieved.
     * @return the timestamp (in milliseconds) of the creation time of the file.
     * @throws IOException if an error occurred processing the file.
     */
    private long getCreationTime(File file) throws IOException {

        int retries = 0;

        while (retries < 3) {
            try {
                BasicFileAttributes attr =
                        Files.readAttributes(Paths.get(file.toURI()),
                                BasicFileAttributes.class);
                return attr.creationTime().toMillis();
            } catch (NoSuchFileException e) {
                retries++;

                if (retries == 3){
                    throw e;
                }

                sleepSilently(100);
            }
        }

        return 0L;
    }

    /**
     * Sleeps for the given amount of milliseconds.
     *
     * @param time the duration (in milliseconds) to sleep.
     */
    public static void sleepSilently(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            LOGGER.error(e);
        }
    }

    /**
     * Returns the name of file being tailed.
     * @return the name of file being tailed.
     */
    public File getTailedFile(){
        return file;
    }
}
