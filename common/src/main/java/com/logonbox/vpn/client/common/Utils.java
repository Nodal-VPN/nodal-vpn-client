package com.logonbox.vpn.client.common;

import com.sshtools.liftlib.OS;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Utils {
    public static String getHostName() {
        return OS.isWindows() ? System.getenv("COMPUTERNAME") : System.getenv("HOSTNAME");
    }
    
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
            try (var out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                return out.toByteArray();
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
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
        // TODO Auto-generated method stub
        return null;
    }
}
