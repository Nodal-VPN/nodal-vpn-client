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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;

public interface ConsoleProvider {
	String readLine(String fmt, Object... args) throws IOException;

	char[] readPassword(String fmt, Object... args) throws IOException;

	Reader reader() throws IOException;

	PrintWriter out() throws IOException;

	PrintWriter err() throws IOException;
	
	void flush() throws IOException;
	
	boolean isAnsi();

	int width();
}