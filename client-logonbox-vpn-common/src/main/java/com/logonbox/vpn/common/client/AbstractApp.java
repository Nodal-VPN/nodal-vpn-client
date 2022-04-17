package com.logonbox.vpn.common.client;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Reflectable
@TypeReflect(classes = true, fields = true, methods = true)
public abstract class AbstractApp implements Callable<Integer> {

	private static Logger log;

	@Option(names = { "-L", "--log-level" }, description = "Log level.")
	private String logLevel;
	@Option(names = { "-LF", "--log-to-file" }, description = "Log output to file.")
	private boolean logToFile = true;
	@Option(names = { "--log-file" }, description = "Location of log file.")
	private Path logFile;
	@Option(names = { "--log-pattern" }, description = "Logging output formatter string.")
	private String logPattern = "%4$s: [%1$tc] %5$s%6$s%n";
	@Option(names = { "-LC", "--log-to-console" }, description = "Log output to console.")
	private boolean logToConsole;
	@Spec
	private CommandSpec spec;

	private Level defaultLogLevel;
	private String defaultLogFilePattern;

	private FileHandler fileHandler;

	private ConsoleHandler consoleHandler;

	private java.util.logging.Logger rootLogger;

	private Formatter fmt;

	protected AbstractApp(String defaultLogFilePattern) {
		this.defaultLogFilePattern = defaultLogFilePattern;
	}

	public static Level parseLogLevel(String logLevel) {
		try {
			return Level.parse(logLevel);
		} catch (Exception iae) {
			return Level.WARNING;
		}
	}

	@Override
	public final Integer call() throws Exception {
		onBeforeCall();

		initLogging();

		return onCall();
	}

	protected void initLogging() throws IOException {
		if(rootLogger == null) {
			Level lvl;
			if (logLevel == null) {
				lvl = getConfiguredLogLevel();
			} else {
				lvl = parseLogLevel(logLevel);
			}
	
			fmt = new java.util.logging.Formatter() {
				@Override
				public String format(LogRecord record) {
					ZonedDateTime zdt = ZonedDateTime.ofInstant(record.getInstant(), ZoneId.systemDefault());
					String source;
					if (record.getSourceClassName() != null) {
						source = record.getSourceClassName();
						if (record.getSourceMethodName() != null) {
							source += " " + record.getSourceMethodName();
						}
					} else {
						source = record.getLoggerName();
					}
					String message = formatMessage(record);
					String throwable = "";
					if (record.getThrown() != null) {
						StringWriter sw = new StringWriter();
						PrintWriter pw = new PrintWriter(sw);
						pw.println();
						record.getThrown().printStackTrace(pw);
						pw.close();
						throwable = sw.toString();
					}
					return String.format(logPattern, zdt, source, record.getLoggerName(), record.getLevel().toString(),
							message, throwable);
				}
			};
	
			rootLogger = java.util.logging.Logger.getLogger("");
			rootLogger.setLevel(lvl);
			reloadConfig(lvl);
		}
	}

	protected void reloadConfig(Level lvl) throws IOException {
		var h = rootLogger.getHandlers();
		for (int i = h.length - 1; i >= 0; i--)
			rootLogger.removeHandler(h[i]);

		if (logToFile) {
			fileHandler = new FileHandler(logFile == null ? defaultLogFilePattern : logFile.toString());
			fileHandler.setLevel(lvl);
			fileHandler.setFormatter(fmt);
			rootLogger.addHandler(fileHandler);
		}

		if (logToConsole) {
			consoleHandler = new ConsoleHandler();
			consoleHandler.setLevel(lvl);
			consoleHandler.setFormatter(fmt);
			rootLogger.addHandler(consoleHandler);
		}
	}

	public final String getLogLevelArgument() {
		return logLevel;
	}

	public final Level getDefaultLogLevel() {
		return defaultLogLevel;
	}

	public void setLogLevel(Level level) {
		if(!Objects.equals(level, rootLogger.getLevel())) {
			getLog().info("Set log level to {}", level);
			rootLogger.setLevel(level);
			if(fileHandler != null)
				fileHandler.setLevel(level);
			if(consoleHandler != null)
				consoleHandler.setLevel(level);
		}
	}

	protected abstract Level getConfiguredLogLevel();

	protected abstract void onBeforeCall() throws Exception;

	protected abstract Integer onCall() throws Exception;

	protected final Logger getLog() {
		if (log == null) {
			log = LoggerFactory.getLogger(AbstractApp.class);
		}
		return log;
	}

}
