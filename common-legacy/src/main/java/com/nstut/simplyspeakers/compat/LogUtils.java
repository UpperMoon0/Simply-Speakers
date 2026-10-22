package com.nstut.simplyspeakers.compat;
public final class LogUtils {
    private LogUtils() {}
    public static org.apache.logging.log4j.Logger getLogger() { return org.apache.logging.log4j.LogManager.getLogger("simplyspeakers"); }
}
