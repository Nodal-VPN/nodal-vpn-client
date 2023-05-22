package com.logonbox.vpn.client.macos;

import com.logonbox.vpn.client.AbstractDesktopPlatformServiceImpl;
import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.DNSIntegrationMethod;
import com.sshtools.forker.client.ForkerBuilder;
import com.sshtools.forker.client.OSCommand;
import com.sshtools.forker.client.EffectiveUserFactory.DefaultEffectiveUserFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class OSXPlatformServiceImpl extends AbstractDesktopPlatformServiceImpl<OSXIP> {

	final static Logger LOG = LoggerFactory.getLogger(OSXPlatformServiceImpl.class);

	private static final String INTERFACE_PREFIX = "wg";

	public OSXPlatformServiceImpl() {
		super(INTERFACE_PREFIX);
	}

	@Override
	public String[] getMissingPackages() {
		return new String[0];
	}

    @Override
    protected void addRouteAll(Connection connection) throws IOException {
        LOG.info("Routing traffic all through VPN");
        String gw = getDefaultGateway();
        LOG.info(String.join(" ", Arrays.asList("route", "add", connection.getEndpointAddress(), "gw", gw)));
        OSCommand.admin("route", "add", connection.getEndpointAddress(), "gw", gw);
    }

    @Override
    protected void removeRouteAll(VPNSession session) throws IOException {
        LOG.info("Removing routing of all traffic through VPN");
        String gw = getDefaultGateway();
        LOG.info(String.join(" ", Arrays.asList("route", "del", session.getConnection().getEndpointAddress(), "gw", gw)));
        OSCommand.admin("route", "del", session.getConnection().getEndpointAddress(), "gw", gw);
    }

	@Override
	protected String getDefaultGateway() throws IOException {
		for(String line : OSCommand.adminCommandAndIterateOutput("route", "-n", "get", "default")) {
			line = line.trim();
			if(line.startsWith("gateway:")) {
				String[] args = line.split(":");
				if(args.length > 1)
					return args[1].trim();
			}
		}
		throw new IOException("Could not get default gateway.");
	}

	@Override
	protected OSXIP createVirtualInetAddress(NetworkInterface nif) {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	protected OSXIP onConnect(VPNSession logonBoxVPNSession, Connection configuration)
			throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public DNSIntegrationMethod dnsMethod() {
		return DNSIntegrationMethod.SCUTIL_COMPATIBLE;
	}

    @Override
    protected void runCommand(List<String> commands) throws IOException {
        OSCommand.admin(commands);
    }

    @Override
    protected Process startProcess(Map<String, String> env, String... args) throws IOException {
        var cmd = new ForkerBuilder(args);
        cmd.redirectErrorStream(true);
        cmd.environment().putAll(env);
        cmd.effectiveUser(DefaultEffectiveUserFactory.getDefault().administrator());
        return cmd.start();
    }

}
