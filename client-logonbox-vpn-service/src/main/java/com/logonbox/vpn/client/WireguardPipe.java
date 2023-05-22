package com.logonbox.vpn.client;

import com.logonbox.vpn.common.client.Keys;
import com.logonbox.vpn.common.client.StatusDetail;
import com.logonbox.vpn.common.client.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.List;

public abstract class WireguardPipe implements StatusDetail {

	final static Logger LOG = LoggerFactory.getLogger(WireguardPipe.class);

	private long rx;
	private long tx;
	private long rxBps;
	private long txBps;
	private Object lock = new Object();
	private long lastHandshakeTimeNSec;
	private long lastHandshakeTimeSec;
	private String userPublicKey;
	private String publicKey;
	private int errno;

    protected final String name;

	public WireguardPipe(String name) throws IOException {
		this.name = name;
		collect();
	}
	
	public int getErrno() {
		return errno;
	}

	public String getUserPublicKey() {
		return userPublicKey;
	}

	public String getPublicKey() {
		return publicKey;
	}

	public long getLastHandshakeNSec() {
		return lastHandshakeTimeNSec;
	}

	public long getLastHandshakeSec() {
		return lastHandshakeTimeSec;
	}

	public long getRxBps() {
		return rxBps;
	}

	public long getTxBps() {
		return txBps;
	}

	public long getRx() {
		return rx;
	}

	public long getTx() {
		return tx;
	}

	protected abstract List<String> command(String command) throws IOException;

	public void collect() throws IOException, UnsupportedEncodingException {
		synchronized(lock) {
			//
			long thisRx = 0;
			long thisTx = 0;
			for (String line : command("get=1")) {
				if (line.startsWith("rx_bytes=")) {
					thisRx = Long.parseLong(line.substring(9));
				} else if (line.startsWith("tx_bytes=")) {
					thisTx = Long.parseLong(line.substring(9));
				} else if (line.startsWith("errno=")) {
					errno = Integer.parseInt(line.substring(6));
				} else if (line.startsWith("last_handshake_time_nsec=")) {
					lastHandshakeTimeNSec = Long.parseLong(line.substring(25));
				} else if (line.startsWith("last_handshake_time_sec=")) {
					lastHandshakeTimeSec = Long.parseLong(line.substring(24));
				} else if (line.startsWith("public_key=")) {
					publicKey = Base64.getEncoder().encodeToString(Util.decodeHexString(line.substring(11)));
				} else if (line.startsWith("private_key=")) {
					String privateKey = Base64.getEncoder().encodeToString(Util.decodeHexString(line.substring(12)));
					userPublicKey = Keys.pubkey(privateKey).getBase64PublicKey();
				}
			}
			rxBps = thisRx - rx;
			txBps = thisRx - tx;
			rx = thisRx;
			tx = thisTx;
		}
	}

	@Override
	public long getLastHandshake() {
		return lastHandshakeTimeSec * 1000;
	}

	@Override
	public String getInterfaceName() {
		return name;
	}

	@Override
	public String getError() {
		return errno == 0 ? "" : String.format("Error " + errno);
	}

    protected List<String> writeThenReadPipe(String command, List<String> l, InputStream in, OutputStream out)
            throws IOException, UnsupportedEncodingException {
        try (BufferedReader rdr = new BufferedReader(new InputStreamReader(in))) {
            out.write((command + "\n\n").getBytes("UTF-8"));
            out.flush();
            String line;
            while ((line = rdr.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0)
                    break;
                l.add(line);
            }
            return l;
        }
    }
}
