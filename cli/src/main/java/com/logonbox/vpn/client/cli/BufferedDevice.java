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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;

public class BufferedDevice implements ConsoleProvider {
	private final BufferedReader reader;
	private final PrintWriter out;
	private final PrintWriter err;

	public BufferedDevice() {
		this(new BufferedReader(new InputStreamReader(System.in)), new PrintWriter(System.out, true), new PrintWriter(System.err, true));
	}

	public BufferedDevice(BufferedReader reader, PrintWriter out, PrintWriter err) {
		this.reader = reader;
		this.out = out;
		this.err = err;
	}

	@Override
	public String readLine(String fmt, Object... params) throws IOException {
		out.printf(fmt, params);
		out.flush();
		try {
			return reader.readLine();
		} catch (IOException e) {
			throw new IllegalStateException();
		}
	}

	@Override
	public char[] readPassword(String fmt, Object... params) throws IOException {
		return readLine(fmt, params).toCharArray();
	}

	@Override
	public Reader reader() throws IOException {
		return reader;
	}

	@Override
	public PrintWriter out() throws IOException {
		return out;
	}

	@Override
	public PrintWriter err() throws IOException {
		return err;
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}

	@Override
	public boolean isAnsi() {
		return false;
	}

	@Override
	public int width() {
		return 80;
	}
}
