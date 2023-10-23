package com.logonbox.vpn.client.common;

import java.io.IOException;

public class DummyUpdateService extends AbstractUpdateService {

	public DummyUpdateService(VpnManager<?> context) {
		super(context);
	}

	@Override
	protected String doUpdate(boolean check) throws IOException {
		return null;
	}

}
