package com.logonbox.vpn.common.client;

import java.io.IOException;

public class DummyUpdateService extends AbstractUpdateService {

	public DummyUpdateService(AbstractUpdateableDBusClient context) {
		super(context);
	}

	@Override
	protected String doUpdate(boolean check) throws IOException {
		return null;
	}

}
