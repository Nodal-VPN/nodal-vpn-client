package com.logonbox.vpn.client.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hypersocket.json.JsonPrincipal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Peer {

	private JsonPrincipal principal;

	public JsonPrincipal getPrincipal() {
		return principal;
	}

	public void setPrincipal(JsonPrincipal principal) {
		this.principal = principal;
	}
	
	
}
