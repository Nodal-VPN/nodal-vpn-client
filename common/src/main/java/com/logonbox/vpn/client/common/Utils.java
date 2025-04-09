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
package com.logonbox.vpn.client.common;

import com.sshtools.liftlib.OS;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class Utils {
    
    public static String defaultIfBlank(String str, String defaultStr) {
        return isBlank(str) ? defaultStr : str;
    }

    public static boolean isBlank(String str) {
        return str == null || str.length() == 0;
    }

    public static boolean isNotBlank(String str) {
        return str != null && str.length() > 0;
    }

    public static String abbreviate(final String str, final int maxWidth) {
        return abbreviate(str, maxWidth, 0);
    }

    public static String abbreviate(final String str, final int maxWidth, int minHide) {
        if (str.length() > maxWidth) {
            if (str.length() - maxWidth < minHide - 3) {
                return str.substring(0, str.length() - minHide) + "...";
            } else {
                return str.substring(0, maxWidth - 3) + "...";
            }
        } else
            return str;
    }

    public final static List<String> readLines(Reader rdr) {
        var l = new ArrayList<String>();
        var brdr = new BufferedReader(rdr);
        String line;
        try {
            while ((line = brdr.readLine()) != null)
                l.add(line);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        return l;
    }

    public static byte[] toByteArray(File file) {
        try (var in = new FileInputStream(file)) {
            return toByteArray(in);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }
    
    public static byte[] toByteArray(Path file) {
        try (var in = Files.newInputStream(file)) {
            return toByteArray(in);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }
    
    public static byte[] toByteArray(URL url) {
        try (var in = url.openStream()) {
            return toByteArray(in);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }
    
    public static byte[] toByteArray(InputStream in) {
        try (var out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    public final static String toString(InputStream in, String enc) {
        try {
            return toString(new InputStreamReader(in, enc));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public final static String toString(Reader rdr) {
        var strwrt = new StringWriter();
        try {
            rdr.transferTo(strwrt);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return strwrt.toString();
    }

    public static void writeLines(Collection<String> lines, String sep, Writer wtr) {
        try {
            for (var l : lines) {
                wtr.append(l);
                wtr.append(sep);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    public static String escapeEcmaScript(String string) {
        return string;
    }
    
    public static String findCommandPath(String name) {
        String cmd = null;
        var fn = OS.isWindows() ? name + ".exe" : name;
        for(var path : Arrays.asList(System.getenv("PATH").split(File.pathSeparator)).stream().map(Paths::get).toList())  {
            if(Files.exists(path.resolve(fn))) {
                cmd = path.resolve(fn).toString();
                break;
            }
        }
        if(cmd == null) {
            cmd = fn;
        }
        return cmd;
    }
    
    public static Collection<String> runCommandAndCaptureOutput(String... args) throws IOException {
        var largs = new ArrayList<String>(Arrays.asList(args));
        var pb = new ProcessBuilder(largs);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        var lines = Utils.readLines(new InputStreamReader(p.getInputStream()));
        try {
            int ret = p.waitFor();
            if (ret != 0) {
                throw new IOException("Command '" + String.join(" ", largs) + "' returned non-zero status. Returned "
                        + ret + ". " + String.join("\n", lines));
            }
        } catch (InterruptedException e) {
            throw new IOException(e.getMessage(), e);
        }
        return lines;
    }

    public static void applicationLock(String name) {
        var lockFile = AppConstants.CLIENT_HOME.resolve(name + ".lock");
        if(!Files.exists(lockFile)) {
            try {
                Files.createDirectories(AppConstants.CLIENT_HOME);
                Files.createFile(lockFile);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        try {
            var chan = FileChannel.open(lockFile, StandardOpenOption.APPEND);
            if(chan.tryLock() == null)
                throw new IllegalStateException("Application '" + name  +"' already in use."); 
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    try {
                       chan.close(); 
                    }
                    catch(Exception e) {
                        try {
                            Files.delete(lockFile);
                        } catch (IOException e1) {
                        }
                    }
                }
            });
        }
        catch(IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        
    }
    
    /**
     * Converts a standard POSIX Shell globbing pattern into a regular expression
     * pattern. The result can be used with the standard {@link java.util.regex} API to
     * recognize strings which match the glob pattern.
     * <p/>
     * https://stackoverflow.com/questions/1247772/is-there-an-equivalent-of-java-util-regex-for-glob-type-patterns
     * <p/>
     * See also, the POSIX Shell language:
     * http://pubs.opengroup.org/onlinepubs/009695399/utilities/xcu_chap02.html#tag_02_13_01
     * 
     * @param pattern A glob pattern.
     * @return A regex pattern to recognize the given glob pattern.
     */
    public static final String convertGlobToRegex(String pattern) {
        StringBuilder sb = new StringBuilder(pattern.length());
        int inGroup = 0;
        int inClass = 0;
        int firstIndexInClass = -1;
        char[] arr = pattern.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char ch = arr[i];
            switch (ch) {
                case '\\':
                    if (++i >= arr.length) {
                        sb.append('\\');
                    } else {
                        char next = arr[i];
                        switch (next) {
                            case ',':
                                // escape not needed
                                break;
                            case 'Q':
                            case 'E':
                                // extra escape needed
                                sb.append('\\');
                            default:
                                sb.append('\\');
                        }
                        sb.append(next);
                    }
                    break;
                case '*':
                    if (inClass == 0)
                        sb.append(".*");
                    else
                        sb.append('*');
                    break;
                case '?':
                    if (inClass == 0)
                        sb.append('.');
                    else
                        sb.append('?');
                    break;
                case '[':
                    inClass++;
                    firstIndexInClass = i+1;
                    sb.append('[');
                    break;
                case ']':
                    inClass--;
                    sb.append(']');
                    break;
                case '.':
                case '(':
                case ')':
                case '+':
                case '|':
                case '^':
                case '$':
                case '@':
                case '%':
                    if (inClass == 0 || (firstIndexInClass == i && ch == '^'))
                        sb.append('\\');
                    sb.append(ch);
                    break;
                case '!':
                    if (firstIndexInClass == i)
                        sb.append('^');
                    else
                        sb.append('!');
                    break;
                case '{':
                    inGroup++;
                    sb.append('(');
                    break;
                case '}':
                    inGroup--;
                    sb.append(')');
                    break;
                case ',':
                    if (inGroup > 0)
                        sb.append('|');
                    else
                        sb.append(',');
                    break;
                default:
                    sb.append(ch);
            }
        }
        return sb.toString();
    }
}
