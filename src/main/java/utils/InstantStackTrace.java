package utils;

import java.lang.Thread;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;

public class InstantStackTrace {
	public static void log(Logger logger) {
	    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
	    Throwable throwable = new Throwable("Instant Stack Trace");
	    throwable.setStackTrace(stack);
	    logger.log(Level.INFO, "Instant Stack Trace", throwable);
	}
	
	private InstantStackTrace() {}
}
