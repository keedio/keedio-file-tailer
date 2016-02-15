package com.keedio.tailer;

import com.google.common.io.Files;
import com.keedio.tailer.exception.TailerException;
import com.keedio.tailer.listener.FileEventListener;
import com.keedio.tailer.listener.impl.LogFileEventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;


/**
 * Created by luca on 13/2/16.
 */
public class QuickDataGeneratorTailerTest {
    private final static Logger LOGGER = LogManager.getLogger(QuickDataGeneratorTailerTest.class);
    private static File logFile;
    private File logDir;

    private ExecutorService service = Executors.newCachedThreadPool();

    @Before
    public void init() throws Exception {
        logDir = Files.createTempDir();

        logFile = new File(logDir, "test.log");
        Files.createParentDirs(logFile);
    }

    @After
    public void destroy() throws Exception {
        //monitor.stop(0);
        logFile.delete();
    }

    @Test
    public void testInexistentFile() {
        FileEventListener mockListener = mock(FileEventListener.class);

        Tailer tailer = new Tailer(mockListener, 1000, "/tmp/garbage");

        Future<?> future = service.submit(tailer);

        try {
            future.get();

            fail("Tailer should not have finished successfully");
        } catch (InterruptedException e) {
            fail("Tailer should not have been interrupted");
        } catch (ExecutionException e) {
            verify(mockListener, times(1)).notExists();
            verify(mockListener, never()).rotated(anyLong(), anyLong());
            assertTrue(e.getCause() instanceof TailerException);
            assertTrue(e.getCause().getCause() instanceof FileNotFoundException);
        }
    }

    @Test
    public void testNullListener() {

        Tailer tailer = new Tailer(null, 1000, "/tmp/garbage");

        Future<?> future = service.submit(tailer);

        try {
            future.get();

            fail("Tailer should not have finished successfully");
        } catch (InterruptedException e) {
            fail("Tailer should not have been interrupted");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof TailerException);
            assertTrue(e.getCause().getCause() instanceof IllegalStateException);

        }
    }

    @Test
    public void testFailureOnDir() {
        FileEventListener mockListener = mock(FileEventListener.class);

        Tailer tailer = new Tailer(mockListener, 1000, "/tmp");

        Future<?> future = service.submit(tailer);

        try {
            future.get();

            fail("Tailer should not have finished successfully");
        } catch (InterruptedException e) {
            fail("Tailer should not have been interrupted");
        } catch (ExecutionException e) {
            verify(mockListener, times(1)).handleException(any(Exception.class));
            verify(mockListener, never()).rotated(anyLong(), anyLong());
            assertTrue(e.getCause() instanceof TailerException);
            assertTrue(e.getCause().getCause() instanceof FileNotFoundException);

        }
    }

    @Test
    public void testHandlePartialLine() {
        FileEventListener mockListener = mock(FileEventListener.class);
        when(mockListener.isValid(anyString())).thenReturn(Boolean.FALSE);

        Tailer tailer = new Tailer(mockListener, 1000, logFile.getAbsolutePath());

        String data = "[TRACE] 2016-02-09 16:41:09.873 " +
                "[pool-3-thread-1] out - \nAccountTransaction(1455032469864," +
                "1455032469864,SzptpSNPWNVqojsHPbYH,0399158778252679 05623732," +
                "bIowQQUwFLBRbbb,329475618292398,2,-1180.0,40861.41,None,0.0)";

        Future<?> dgFuture = service.submit(new DataGenerator(data, 1,100));

        Tailer.sleepSilently(500);

        Future<?> future = service.submit(tailer);

        try {
            future.get(2, TimeUnit.SECONDS);
            fail("Tailer should not have finished");
        } catch (InterruptedException e) {
            fail("Tailer should not have been interrupted");
        } catch (ExecutionException e) {
            fail("Tailer should not throw any exception");
        } catch (TimeoutException e) {
            verify(mockListener, never()).handle(anyString());
            verify(mockListener, never()).rotated(anyLong(), anyLong());
        }
    }

    static class RegexpValidatorListener extends LogFileEventListener {

        private static final String regexp = "^\\[TRACE\\].*\\)$";
        private static final Pattern pattern = Pattern.compile(regexp);

        @Override
        public boolean isValid(String partialLine) {
            boolean res = pattern.matcher(partialLine).matches();

            if (res){
                //LOGGER.info("Found match on line: " +partialLine);
            }

            return res;
        }
    }

    @Test
    public void testHandleLineWithPattern() {
        FileEventListener mockListener = mock(RegexpValidatorListener.class);
        when(mockListener.isValid(anyString())).thenCallRealMethod();

        String data = "[TRACE] 2016-02-09 16:41:09.873 " +
                "[pool-3-thread-1] out - \nAccountTransaction(1455032469864," +
                "1455032469864,SzptpSNPWNVqojsHPbYH,0399158778252679 05623732," +
                "bIowQQUwFLBRbbb,329475618292398,2,-1180.0,40861.41,None,0.0)";


        Tailer tailer = new Tailer(mockListener, 1000, logFile.getAbsolutePath());

        Future<?> dgFuture = service.submit(new DataGenerator(data, 1,100));

        Tailer.sleepSilently(500);

        Future<?> future = service.submit(tailer);

        try {
            future.get(2, TimeUnit.SECONDS);

            fail("Tailer should not have finished");
        } catch (InterruptedException e) {
            fail("Tailer should not have been interrupted");
        } catch (ExecutionException e) {
            fail("Tailer should not throw any exception");
        } catch (TimeoutException e) {
            verify(mockListener, atLeastOnce()).isValid(anyString());
            verify(mockListener, atLeastOnce()).handle(anyString());
            verify(mockListener, never()).rotated(anyLong(), anyLong());
        }
    }

    static class RegexpDegeneratedDataValidatorListener extends LogFileEventListener {

        private static final String regexp = "^\\{\"log\":.*\\}$";
        private static final Pattern pattern = Pattern.compile(regexp);

        @Override
        public boolean isValid(String partialLine) {
            boolean res = pattern.matcher(partialLine).matches();

            if (res){
                //LOGGER.info("Found match on line: " +partialLine);
            }

            return res;
        }

        @Override
        public String rotated(long lastPosition, long currPosition) {
            return logFile.getAbsolutePath()+".1";
        }
    }

    @Test
    public void testHandleLineWithPatternDegeneratedData() {
        FileEventListener mockListener = mock(RegexpDegeneratedDataValidatorListener.class);
        when(mockListener.isValid(anyString())).thenCallRealMethod();

        String data = "{\"log\":\"2016-02-02 10:53:22.285 [main] " +
                "INFO  org.springframework.context.supp\rort.DefaultLifecycleProcessor " +
                "- {\"category\":\"applicationlog\",\"requestId\":\"\",\"timestamp\": " +
                "2016-02-02 10:53:22.285,\"description\":\"Starting beans in \nphase 0\"," +
                "\"returnCode\":\"\",\"trace\":\"\",\"appName\":\"bootstrap\",\"serverId\":" +
                "\"null\"}\",\"stream\":\"stdout\",\"time\":\"2016-02-02T09:53:22.287849201Z\"}";

        Tailer tailer = new Tailer(mockListener, 1000, logFile.getAbsolutePath());

        Future<?> dgFuture = service.submit(new DataGenerator(data, 1,100));

        Tailer.sleepSilently(500);

        Future<?> future = service.submit(tailer);

        try {
            future.get(2, TimeUnit.SECONDS);

            fail("Tailer should not have finished");
        } catch (InterruptedException e) {
            fail("Tailer should not have been interrupted");
        } catch (ExecutionException e) {
            fail("Tailer should not throw any exception");
        } catch (TimeoutException e) {
            verify(mockListener, atLeastOnce()).isValid(anyString());
            verify(mockListener, atLeastOnce()).handle(anyString());
            verify(mockListener, never()).rotated(anyLong(), anyLong());
        }
    }

    @Test
    public void testFileRotation(){
        FileEventListener mockListener = mock(RegexpDegeneratedDataValidatorListener.class);
        when(mockListener.isValid(anyString())).thenCallRealMethod();
        when(mockListener.rotated(anyLong(), anyLong())).thenCallRealMethod();


        String data = "{\"log\":\"2016-02-02 10:53:22.285 [main] " +
                "INFO  org.springframework.context.supp\rort.DefaultLifecycleProcessor " +
                "- {\"category\":\"applicationlog\",\"requestId\":\"\",\"timestamp\": " +
                "2016-02-02 10:53:22.285,\"description\":\"Starting beans in \nphase 0\"," +
                "\"returnCode\":\"\",\"trace\":\"\",\"appName\":\"bootstrap\",\"serverId\":" +
                "\"null\"}\",\"stream\":\"stdout\",\"time\":\"2016-02-02T09:53:22.287849201Z\"}\n";

        Tailer tailer = new Tailer(mockListener, 1000, logFile.getAbsolutePath());

        Future<?> dgFuture = service.submit(new DataGenerator(data, 100,10));

        Tailer.sleepSilently(500);

        Future<?> future = service.submit(tailer);

        try {
            dgFuture.get();

            try {
                LOGGER.info("Rotating file");
                logFile.renameTo(new File(logDir, "test.log.1"));
                logFile = new File(logDir, "test.log");

                service.submit(new DataGenerator(data, 100,10));

                future.get(4, TimeUnit.SECONDS);

                fail("Tailer should not have finished");
            } catch (InterruptedException ex) {
                fail("Tailer should not have been interrupted");
            } catch (ExecutionException ex) {
                LOGGER.error("ExecutionException",ex);
                fail("Tailer should not throw any exception");


            } catch (TimeoutException ex) {
                verify(mockListener, times(20)).handle(anyString());
                verify(mockListener, times(1)).rotated(anyLong(), anyLong());
            }
        } catch (InterruptedException e) {
            fail("DataGenerator should not have been interrupted");
        } catch (ExecutionException e) {
            fail("DataGenerator should not throw any exception");
        }
    }

    /**
     * Generates a constant string of data char by char, outputted to a temp file.
     */
    class DataGenerator implements Runnable {
        String data;// = "[TRACE] 2016-02-09 16:41:09.873 [pool-3-thread-1] out - \nAccountTransaction(1455032469864,1455032469864,SzptpSNPWNVqojsHPbYH,0399158778252679 05623732,bIowQQUwFLBRbbb,329475618292398,2,-1180.0,40861.41,None,0.0)";
        long timeout;
        int maxLines;

        public DataGenerator(String data, long timeout, int maxLines) {
            this.timeout = timeout;
            this.maxLines = maxLines;
            this.data = data;
        }

        @Override
        public void run() {

            LOGGER.info("Generating log to:\n" + logFile.getAbsolutePath());

            try (BufferedWriter w = new BufferedWriter(new FileWriter(logFile))) {
                int k = 0;
                while (k < maxLines) {

                    String realData = data.substring(0, 8) + k + " " + data.substring(8);

                    w.write(realData);
                    w.flush();
                    k++;
                    Thread.sleep(timeout);
                }
            } catch (Exception e) {
                LOGGER.error(e);
            } finally {
                LOGGER.debug("Exiting Data generator");
            }
        }
    }
}
