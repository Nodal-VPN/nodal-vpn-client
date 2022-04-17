package com.logonbox.vpn.common.client;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map; 

import javax.net.ssl.HostnameVerifier;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersocket.json.AuthenticationRequiredResult;
import com.hypersocket.json.AuthenticationResult;
import com.hypersocket.json.JsonLogonResult;
import com.hypersocket.json.JsonSession;
import com.hypersocket.json.input.InputField;
import com.logonbox.vpn.common.client.api.PeerResponse;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

public class ServiceClient {

	public static class NameValuePair {

		private String name;
		private String value;

		public NameValuePair(String name, String value) {
			super();
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}

	}

	public interface Authenticator {

		String getUUID();

		HostnameVerifier getHostnameVerifier();

		void authorized() throws IOException;

		void collect(JsonNode i18n, AuthenticationRequiredResult result, Map<InputField, NameValuePair> results)
				throws IOException;

		void error(JsonNode i18n, AuthenticationResult logonResult);

	}

	static Logger log = LoggerFactory.getLogger(ServiceClient.class);

	private ObjectMapper mapper;
	private CookieHandler cookieHandler;
	private Authenticator authenticator;
	private CookieStore cookieStore;

	public ServiceClient(CookieStore cookieStore, Authenticator authenticator) {
		mapper = new ObjectMapper();
		this.cookieStore = cookieStore;
		this.authenticator = authenticator;
	}

	protected String doGet(VPNConnection connection, String url, NameValuePair... headers)
			throws IOException, InterruptedException, URISyntaxException {

		if (!url.startsWith("/")) {
			url = "/" + url;
		}

		var client = getHttpClient(connection);
		var builder = HttpRequest.newBuilder(new URI(connection.getUri(false) + url));
		for (NameValuePair nvp : headers)
			builder.header(nvp.getName(), nvp.getValue());
		var request = builder.build();

		log.info("Executing request " + request.toString());

		var response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200)
			throw new IOException("Expected status code 200 for doPost. " + response.statusCode());
		return response.body();
	}

	protected HttpClient getHttpClient(VPNConnection connection) {
		if (cookieHandler == null) {
//			cookieHandler = CookieManager.getDefault();
			cookieHandler = new CookieManager(cookieStore, CookiePolicy.ACCEPT_ALL);
			HttpCookie cookie = new HttpCookie(AbstractDBusClient.DEVICE_IDENTIFIER, authenticator.getUUID());
			cookie.setSecure(true);
			cookie.setPath("/");
			cookie.setDomain(connection.getHostname());
//			cookie.setAttribute(CustomCookieStore.DOMAIN_ATTR, "true");
			try {
				cookieStore.add(new URI(connection.getUri(false)), cookie);
			} catch (URISyntaxException e) {
				throw new IllegalStateException("Failed to add device identifier cookie.");
			}
		}

		return HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).cookieHandler(cookieHandler)
				.connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();

	}

	protected void debugJSON(String json) throws JsonParseException, JsonMappingException, IOException {
		if (log.isDebugEnabled()) {
			Object obj = mapper.readValue(json, Object.class);
			String ret = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
			log.debug(ret);
		}

	}

	public void register(VPNConnection connection) throws IOException, URISyntaxException {
		JsonSession session;
		try {
			session = auth(connection);
		} catch (InterruptedException e) {
			throw new IOException("Authentication interrupted.");
		}
		String deviceId = authenticator.getUUID();
		try {
			try {
				StringBuilder keyParameters = new StringBuilder();
				keyParameters.append("os=" + Util.getOS());
				keyParameters.append('&');
				keyParameters.append("mode=" + connection.getMode());
				keyParameters.append('&');
				keyParameters.append("deviceName=" + URLEncoder.encode(Util.getOS(), "UTF-8"));
				if (StringUtils.isNotBlank(connection.getUserPublicKey())) {
					keyParameters.append('&');
					keyParameters.append("publicKey=" + URLEncoder.encode(connection.getUserPublicKey(), "UTF-8"));
				}
				keyParameters.append('&');
				keyParameters.append("token=" + URLEncoder.encode(session.getCsrfToken(), "UTF-8"));
				log.info(String.format("Retrieving peers for %s", deviceId));
				String json = doGet(connection, "/api/peers/get?" + keyParameters.toString());
				debugJSON(json);
				PeerResponse result = mapper.readValue(json, PeerResponse.class);
				boolean success = result.isSuccess();

				if (success) {
					log.info("Retrieved peers");
					String deviceUUID = result.getDeviceUUID();
					if (StringUtils.isNotBlank(deviceUUID) && result.getResource() == null) {
						json = doGet(connection, "/api/peers/register?" + keyParameters.toString());
						debugJSON(json);
						result = mapper.readValue(json, PeerResponse.class);
						if (result.isSuccess()) {
							log.info(String.format("Device UUID registered. %s", deviceUUID));
							configure(connection, result.getContent(),
									session.getCurrentPrincipal().getPrincipalName());
						} else {
							throw new IOException("Failed to register. " + result.getMessage());
						}
					} else {
						log.info("Already have UUID, passing on configuration");
						configure(connection, result.getContent(), session.getCurrentPrincipal().getPrincipalName());
					}
				} else
					throw new IOException("Failed to query for existing peers. " + result.getMessage());
			} finally {
				try {
					doGet(connection, "/api/logoff?token=" + URLEncoder.encode(session.getCsrfToken(), "UTF-8"));
				}
				catch(Exception e) {
					log.warn("Failed to logoff session from server. Continuing, but the session may still exist on the server until it times-out.", e);
				}
			}
		} catch (InterruptedException ie) {
			throw new IOException("Interrupted.", ie);
		}
	}

	protected void configure(VPNConnection config, String configIniFile, String usernameHint) throws IOException {
		log.info(String.format("Configuration for %s", usernameHint));
		config.setUsernameHint(usernameHint);
		String error = config.parse(configIniFile);
		if (StringUtils.isNotBlank(error))
			throw new IOException(error);

		config.save();
		authenticator.authorized();
		config.authorized();
	}

	protected JsonSession auth(VPNConnection connection) throws IOException, URISyntaxException, InterruptedException {

		JsonNode i18n;
		try {
			/* 2.4 server */
			String i18njson = doGet(connection, "/api/i18n/group/_default_i18n_group");
			i18n = mapper.readTree(i18njson);
		} catch (IOException cpe) {
			/* 2.3 server */
			String i18njson = doGet(connection, "/api/i18n");
			i18n = mapper.readTree(i18njson);
		}

		// main.getServer().
		String json = doGet(connection, "/api/logon");
		debugJSON(json);

		AuthenticationRequiredResult result;
		String logonJson;
		Map<InputField, NameValuePair> results = new HashMap<>();
		while (true) {
			result = mapper.readValue(json, AuthenticationRequiredResult.class);

			if (result.getSuccess())
				throw new IllegalStateException("Didn't expect to be already logged in.");

			authenticator.collect(i18n, result, results);

			logonJson = doPost(connection, "/api/logon", results.values().toArray(new NameValuePair[0]));
			debugJSON(logonJson);

			AuthenticationResult logonResult = mapper.readValue(logonJson, AuthenticationResult.class);
			if (logonResult.getSuccess()) {
				JsonLogonResult logon = mapper.readValue(logonJson, JsonLogonResult.class);
				return logon.getSession();
			} else {
				authenticator.error(i18n, logonResult);
			}

			if (result.isLast())
				break;

			json = doGet(connection, "/api/logon");
			debugJSON(json);
		}

		throw new IOException("Authentication failed.");
	}

	protected String doPost(VPNConnection connection, String url, NameValuePair... postVariables)
			throws URISyntaxException, IOException, InterruptedException {

		if (!url.startsWith("/")) {
			url = "/" + url;
		}

		var client = getHttpClient(connection);
		var request = HttpRequest.newBuilder(new URI(connection.getUri(false) + url))
				.header("Content-Type", "application/x-www-form-urlencoded").POST(ofNameValuePairs(postVariables))
				.build();

		log.info("Executing request " + request.toString());

		var response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200)
			throw new IOException("Expected status code 200 for doPost");
		return response.body();

	}

	public static HttpRequest.BodyPublisher ofNameValuePairs(NameValuePair... parms) {
		var b = new StringBuilder();
		for (var n : parms) {
			if (b.length() > 0) {
				b.append("&");
			}
			b.append(URLEncoder.encode(n.getName(), StandardCharsets.UTF_8));
			if (n.getValue() != null) {
				b.append('=');
				b.append(URLEncoder.encode(n.getValue(), StandardCharsets.UTF_8));
			}
		}
		return HttpRequest.BodyPublishers.ofString(b.toString());
	}
}
