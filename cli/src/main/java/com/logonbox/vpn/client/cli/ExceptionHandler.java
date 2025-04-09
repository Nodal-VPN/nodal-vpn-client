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

import org.freedesktop.dbus.exceptions.DBusExecutionException;

import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.function.Supplier;

import javax.net.ssl.SSLHandshakeException;

import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

public class ExceptionHandler implements IExecutionExceptionHandler {
	
	private final Supplier<Boolean> verboseExceptions;
	private final Supplier<String> commandName;
    private final Supplier<Boolean> quietExceptions;

	public ExceptionHandler(Supplier<Boolean> verboseExceptions, Supplier<Boolean> quietExceptions, Supplier<String> commandName) {
		this.verboseExceptions = verboseExceptions;
		this.commandName = commandName;
		this.quietExceptions = quietExceptions;
	}

	@Override
	public int handleExecutionException(Exception ex, CommandLine commandLine, ParseResult parseResult)
			throws Exception {
	    if(!quietExceptions.get()) {
    		var report = new StringBuilder();
    		var msg = ex.getMessage() == null ? "An unknown error occured." : ex.getMessage();
    		if(ex instanceof DBusExecutionException dee) {
    		    msg = dee.getMessage().substring(dee.getMessage().indexOf(':') + 2);
    		}
    		else if(ex instanceof UnknownHostException) {
    			msg = MessageFormat.format("Could not resolve hostname {0}: Name or service not known.", ex.getMessage());
    		}
    		if(ex instanceof SSLHandshakeException sshe) {
    			if(sshe.getMessage().contains("No subject alternative")) {
    				msg = sshe.getMessage() + 
    					". Check that this address matches that which is in the SSL certificate on this host. " +
    					"In a non-production environment, it is probably safe to use the `--ignore-ssl-trust` " +
    					"option temporarily until this issue is corrected.";
    					
    			}
    		}
    		if(verboseExceptions.get()) {
    			report.append(ex.getClass().getName());
    			report.append(": ");
    		}
    		report.append(Ansi.AUTO.string("@|9 " + commandName.get() + ": " + msg + "|@"));
    		report.append(System.lineSeparator());
    		if(verboseExceptions.get()) {
    			Throwable nex = ex;
    			int indent = 0;
    			while(nex != null) {
    				if(indent > 0) {
    					report.append(String.format("%" + ( 8 + ((indent - 1 )* 2) ) + "s", ""));
    					report.append(nex.getClass().getName());
    					report.append(": ");
    			        report.append(Ansi.AUTO.string("@|9 " + (nex.getMessage() == null ? "No message." : nex.getMessage())+ "|@"));
    					report.append(System.lineSeparator());
    				}
    				
    				for(var el : nex.getStackTrace()) {
    					report.append(String.format("%" + ( 8 + (indent * 2) ) + "s", ""));
    					report.append("at ");
    					if(el.getModuleName() != null) {
    						report.append(el.getModuleName());
    						report.append('/');
    					}
                        report.append(Ansi.AUTO.string("@|yellow " + el.getClassName() + "." + el.getMethodName() + "|@"));
    					if(el.getFileName() != null) {
    						report.append('(');
    						report.append(el.getFileName());
    						if(el.getLineNumber() > -1) {
    							report.append(':');
    		                    report.append(Ansi.AUTO.string("@|yellow " + String.valueOf(el.getLineNumber()) + "|@"));
    							report.append(')');
    						}
    					}
    					report.append(System.lineSeparator());
    				}
    				indent++;
    				nex = nex.getCause();
    			}
    		}
    		System.err.print(report.toString());
	    }
		return 1;
	}

}
