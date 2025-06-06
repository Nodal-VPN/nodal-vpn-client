/**
 * Copyright ©2023-2025 LogonBox Ltd
 * All changes post March 2025 Copyright © ${project.inceptionYear} JADAPTIVE Limited (support@jadaptive.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.logonbox.vpn.client.cli;

import static java.util.Locale.ENGLISH;

import com.sshtools.liftlib.OS;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NativeConsoleDevice implements ConsoleProvider {
	static Logger log = LoggerFactory.getLogger(NativeConsoleDevice.class);

	private final Console console;
	private boolean ansi;
	private int width = -1;
	private long last;

	public NativeConsoleDevice() {
		this(System.console());
	}

	public NativeConsoleDevice(Console console) {
		if (console == null)
			throw new IllegalArgumentException("No console.");
		this.console = console;
	}

	@Override
	public Reader reader() throws IOException {
		return console.reader();
	}

	@Override
	public String readLine(String fmt, Object... args) throws IOException {
		return console.readLine(fmt, args);
	}

	@Override
	public char[] readPassword(String fmt, Object... args) throws IOException {
		return console.readPassword(fmt, args);
	}

	@Override
	public PrintWriter out() throws IOException {
		return console.writer();
	}

	@Override
	public PrintWriter err() throws IOException {
		return out();
	}

	@Override
	public void flush() throws IOException {
		console.flush();
	}

	@Override
	public boolean isAnsi() {
		return ansi;
	}

	@Override
	public int width() {
		long now = System.currentTimeMillis();
		if(width == -1 || last < now - 1000) {
			last = now;
			final AtomicInteger size = new AtomicInteger(-1);
			final String[] cmd = (OS.isWindows() && !isPseudoTTY())
					? new String[] { "cmd.exe", "/c", "mode con" }
					: (OS.isMacOs() ? new String[] { "tput", "cols" }
							: new String[] { "stty", "-a", "-F", "/dev/tty" });
			Thread t = new Thread(new Runnable() {
				public void run() {
					Process proc = null;
					BufferedReader reader = null;
					try {
						ProcessBuilder pb = new ProcessBuilder(cmd);
						log.debug("getTerminalWidth() executing command %s%n", pb.command());
						// proc = Runtime.getRuntime().exec(new String[] { "sh", "-c", "tput cols 2>
						// /dev/tty" });
						Class<?> redirectClass = Class.forName("java.lang.ProcessBuilder$Redirect");
						Object INHERIT = redirectClass.getField("INHERIT").get(null);
						Method redirectError = ProcessBuilder.class.getDeclaredMethod("redirectError", redirectClass);
						redirectError.invoke(pb, INHERIT);
						proc = pb.start();
						reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
						String txt = "";
						String line;
						while ((line = reader.readLine()) != null) {
							txt += " " + line;
						}
						log.debug("getTerminalWidth() parsing output: %s%n", txt);
						Pattern pattern = (OS.isWindows() && !isPseudoTTY())
								? Pattern.compile(".*?:\\s*(\\d+)\\D.*?:\\s*(\\d+)\\D.*", Pattern.DOTALL)
								: (OS.isMacOs() ? Pattern.compile("(\\s*)(\\d+)\\s*")
										: Pattern.compile(".*olumns(:)?\\s+(\\d+)\\D.*", Pattern.DOTALL));
						Matcher matcher = pattern.matcher(txt);
						if (matcher.matches()) {
							size.set(Integer.parseInt(matcher.group(2)));
						}
					} catch (Exception ignored) { // nothing to do...
						log.debug("getTerminalWidth() ERROR: %s%n", ignored);
					} finally {
						if (proc != null) {
							proc.destroy();
						}
						if (reader != null) {
							try {
								reader.close();
							} catch (IOException e) {
							}
						}
					}
				}
			});
			t.start();
			now = System.currentTimeMillis();
			do {
				if (size.intValue() >= 0) {
					break;
				}
				try {
					Thread.sleep(25);
				} catch (InterruptedException ignored) {
				}
			} while (System.currentTimeMillis() < now + 2000 && t.isAlive());
			width = size.intValue();
		}
		return width;
	}

	private static boolean isPseudoTTY() {
		return OS.isWindows() && (isXterm() || isCygwin() || hasOsType());
	}

	private static boolean hasOsType() {
		return System.getenv("OSTYPE") != null;
	}

	private static boolean isCygwin() {
		return System.getenv("TERM") != null && System.getenv("TERM").toLowerCase(ENGLISH).contains("cygwin");
	}

	private static boolean isXterm() {
		return System.getenv("TERM") != null && System.getenv("TERM").startsWith("xterm");
	}
}