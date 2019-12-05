/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.couchbase.sdkd.util;


import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.*;

/**
 *
 * @author mnunberg
 */
public class LogUtil {
    private static boolean isOnWire = false;
    private static boolean isInitialized = false;
    private static List<Logger> libLoggers = new LinkedList<Logger>();
    private static List<Logger> sdkdLoggers = new LinkedList<Logger>();
    private static List<Logger> vbLoggers = new LinkedList<Logger>();

    private static List<Logger> getAllLoggers() {
        LinkedList<Logger> ret = new LinkedList<Logger>();
        ret.addAll(libLoggers);
        ret.addAll(sdkdLoggers);
        return ret;
    }

    static final String logPrefix = "com.couchbase.sdkd";

    public static Logger sdkdLogger() {
        return Logger.getLogger(logPrefix);
    }

    public static Logger getLogger(String s) {
        return Logger.getLogger(logPrefix + "." + s);
    }

    private static void applyLevel(Level slevel, List<Logger> targets) {
        for (Logger logger:targets) {
            logger.setLevel(slevel);
        }
    }



    private static void selfTest() {
        ArrayList<Level> allLevels = new ArrayList<Level>();
        allLevels.add(Level.SEVERE);
        allLevels.add(Level.WARNING);
        allLevels.add(Level.INFO);
        allLevels.add(Level.CONFIG);
        allLevels.add(Level.FINE);
        allLevels.add(Level.FINER);
        allLevels.add(Level.FINEST);

        for (Logger logger : getAllLoggers()) {
            for (Level lvl : allLevels) {
                logger.log(lvl, logger.getName() + ": Test Message");
            }
        }
    }

    public static void setOnWire(boolean enabled) {
        isOnWire = enabled;
    }


    static class ShortFormatter extends Formatter {
        @Override
        public String format(LogRecord rec) {
            StringBuilder sb = new StringBuilder();
            // Log <= info to stdout, else to stderr.
            sb.append('[').append(rec.getLevel().toString()).append("] ");
            String name = rec.getLoggerName();
            int lastDot = name.lastIndexOf('.');
            if (lastDot != -1) {
                name = name.substring(lastDot + 1);
            }
            sb.append('(')
                    .append(name).append(':')
                    .append(rec.getSourceMethodName())
                    .append(')');

            sb.append(' ').append(name).append(") ").append(rec.getMessage());
            Throwable e = rec.getThrown();
            if (e != null) {
                sb.append("\n");
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                sb.append(sw.toString());
            }
            sb.append("\n");
            return sb.toString();
        }
    }

    private static void initShortFormat(Handler consoleHandler) {
        consoleHandler.setFormatter(new ShortFormatter());
    }

    public static void initLogging(
            boolean do_self_test,
            String logFile) {
        Logger topLogger = Logger.getLogger("");
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(logFile, true);
        } catch(Exception ex) {
            //ignore
        }


        Handler handler = new StreamHandler(outputStream, new SimpleFormatter());
        handler.flush();
        for (Handler h : topLogger.getHandlers()) {
            topLogger.removeHandler(h);
        }


        if (handler == null) {
            handler = new ConsoleHandler();
        }
        topLogger.addHandler(handler);
        handler.setLevel(Level.ALL);
        libLoggers.add(Logger.getLogger("com.couchbase.client"));
        sdkdLoggers.add(sdkdLogger());

        applyLevel(Level.FINE, getAllLoggers());

        if (isOnWire) {
            initShortFormat(handler);
        }

        if (do_self_test) {
            selfTest();
        }
    }

    public static void restartLogging(String logFile) {
        LogManager.getLogManager().reset();
        File file = new File(logFile);
        file.delete();
        initLogging(false, logFile);
    }
}
