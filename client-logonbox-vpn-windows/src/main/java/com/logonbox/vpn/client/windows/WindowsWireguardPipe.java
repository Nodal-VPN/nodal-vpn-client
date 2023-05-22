package com.logonbox.vpn.client.windows;

import com.logonbox.vpn.client.WireguardPipe;
import com.sshtools.forker.pipes.DefaultPipeFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class WindowsWireguardPipe extends WireguardPipe {

    final static Logger LOG = LoggerFactory.getLogger(WindowsWireguardPipe.class);

    private String pipeName;

    public WindowsWireguardPipe(String name) throws IOException {
        super(name);
    }

    protected List<String> command(String command) throws IOException {
        if (LOG.isDebugEnabled())
            LOG.debug(String.format("Opening named pipe for %s to run %s", pipeName, command));
        List<String> l = new ArrayList<String>();
        try {
            return tryPipe(command, l, "ProtectedPrefix\\Administrators\\WireGuard\\" + name);
        } catch (Exception e) {
            return tryPipe(command, l,
                    "ProtectedPrefix\\"
                            + WindowsPlatformServiceImpl.getBestRealName(
                                    WindowsPlatformServiceImpl.SID_ADMINISTRATORS_GROUP, "Administrators")
                            + "\\WireGuard\\" + name);
        }
    }

    protected List<String> tryPipe(String command, List<String> l, String pipeSuffix)
            throws IOException, UnsupportedEncodingException {
        pipeName = "\\\\.\\pipe\\" + pipeSuffix;

        if (LOG.isDebugEnabled())
            LOG.debug(String.format("Trying pipe %s", pipeName));

        try (Socket pipe = new DefaultPipeFactory().createPipe(pipeSuffix)) {
            try (OutputStream out = pipe.getOutputStream()) {
                return writeThenReadPipe(command, l, pipe.getInputStream(), pipe.getOutputStream());
            }
        }
    }
}
