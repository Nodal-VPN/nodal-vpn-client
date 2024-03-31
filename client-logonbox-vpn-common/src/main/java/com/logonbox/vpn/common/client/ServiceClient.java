package com.logonbox.vpn.common.client;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
import java.util.UUID;

import javax.net.ssl.HostnameVerifier;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
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

	public static String genToken() {
		return UUID.randomUUID().toString().replace("-", "");
	}
	
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

		@Deprecated
		void collect(JsonNode i18n, AuthenticationRequiredResult result, Map<InputField, NameValuePair> results)
				throws IOException;
		
		void prompt(DeviceCode code) throws AuthenticationCancelledException;

		void error(JsonNode i18n, AuthenticationResult logonResult);

	}

	static Logger log = LoggerFactory.getLogger(ServiceClient.class);

	private ObjectMapper mapper;
	private CookieHandler cookieHandler;
	private Authenticator authenticator;
	private CookieStore cookieStore;
	private PromptingCertManager certManager;

	public ServiceClient(CookieStore cookieStore, Authenticator authenticator, PromptingCertManager certManager) {
		mapper = new ObjectMapper();
		this.cookieStore = cookieStore;
		this.authenticator = authenticator;
		this.certManager = certManager;
	}

	protected String doGet(VPNConnection connection, String url, NameValuePair... headers)
			throws IOException, InterruptedException, URISyntaxException {

		if (!url.startsWith("/")) {
			url = "/" + url;
		}

		var client = getHttpClient(connection);
		var builder = HttpRequest.newBuilder(new URI(connection.getApiUri() + url));
		for (NameValuePair nvp : headers)
			builder.header(nvp.getName(), nvp.getValue());
		var request = builder.build();

		if(log.isDebugEnabled())
			log.debug("Executing request " + request.toString());

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
				cookieStore.add(new URI(connection.getApiUri()), cookie);
			} catch (URISyntaxException e) {
				throw new IllegalStateException("Failed to add device identifier cookie.");
			}
		}

		return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).
				cookieHandler(cookieHandler).sslContext(certManager.getSSLContext()).sslParameters(certManager.getSSLParameters())
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
		try {
			/* JAD (VPN v3) server */
			mapper.readTree(doGet(connection, "/vpn-stat")).get("messages");
		}
		catch(IOException | InterruptedException | URISyntaxException ioe) {
			legacyRegister(connection);
			return;
		}
		
		try {
			var device = mapper.readValue(
					doPost(connection, "/vpn-device/" + connection.getClient(), 
							new NameValuePair("scope", "acquireVpn"),
							new NameValuePair("state", connection.getClient())
					), DeviceCode.class);
			var interval = device.interval == 0 ? 5 : device.interval;
			authenticator.prompt(device);
			
			var expire = System.currentTimeMillis() + ( device.expires_in * 1000 );
			log.info("Awaiting authorization for device {} [{}]", device.device_code, connection.getUserPublicKey());
			while(System.currentTimeMillis() < expire) {

				var response = mapper.readValue(doPost(connection, "/oauth2/token?", 
						new NameValuePair("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
						new NameValuePair("device_code", device.device_code)
				), BearerToken.class);
				
				if(response.error == null) {
					/* Have a token that is allowed the required scope, now we
					 * can actually get the VPN configuration
					 */
					var configPayload = mapper.readValue(doPost(connection, "acquire-vpn", 
						new NameValuePair[] {
							new NameValuePair("Authentication", response.token_type + " " + response.access_token)
						}, 
						new NameValuePair("os", Util.getOS().toUpperCase()),
						new NameValuePair("pubkey", connection.getUserPublicKey()),
						new NameValuePair("instance", connection.getInstance()),
						new NameValuePair("deviceId",  authenticator.getUUID()),
						new NameValuePair("name", Util.getDeviceName())), 
							ConfigurationPayload.class);
					
					log.info(String.format("Device UUID registered. %s",  authenticator.getUUID()));
					if(log.isDebugEnabled()) {
						log.debug(configPayload.content);
					}
					configure(connection, configPayload.content,
							configPayload.username);
					
					return;
					
				}
				else if(response.error.equals("authorization_denied") || response.error.equals("expired_token")) {
					break;
				}
				else if(response.error.equals("authorization_pending")) {
					Thread.sleep(1000 * interval);
				}
				else if(response.error.equals("slow_down")) {
					interval += 5;
				}
			}

			throw new IOException("Authorization was denied or timed-out.");
		}
		catch(InterruptedException nsae) {
			throw new IllegalStateException(nsae);
		}
	}

	protected void legacyRegister(VPNConnection connection) throws IOException, URISyntaxException,
			UnsupportedEncodingException, JsonParseException, JsonMappingException, JsonProcessingException {
		JsonSession session;
		try {
			session = legacyAuth(connection);
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
		log.info("Configuration for {} [{}]", usernameHint, config.getUserPublicKey());
		
		if(log.isDebugEnabled())
			log.debug(configIniFile);
		
		config.setUsernameHint(usernameHint);
		String error = config.parse(configIniFile);
		if (StringUtils.isNotBlank(error))
			throw new IOException(error);

		log.info("Saving new configuration for {} []", config.getDisplayName(), config.getUserPublicKey());
		config.save();
		authenticator.authorized();

		log.info("Signal authorized {}", config.getDisplayName());
		config.authorized();
	}
	
	protected JsonSession legacyAuth(VPNConnection connection) throws IOException, URISyntaxException, InterruptedException {

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
		return doPost(connection, url, new NameValuePair[0], postVariables);
	}

	protected String doPost(VPNConnection connection, String url, NameValuePair[] headers, NameValuePair... postVariables)
			throws URISyntaxException, IOException, InterruptedException {

		if (!url.startsWith("/")) {
			url = "/" + url;
		}

		var client = getHttpClient(connection);
//		
//		XXXX TODO this needs to be the base API URI, which may now change because
//		XXXX the path contains the client type
		
		var bldr = HttpRequest.newBuilder(new URI(connection.getApiUri() + url));
		for(var hdr : headers) {
			bldr.header(hdr.getName(), hdr.getValue());
		}
		var request = bldr
				.header("Content-Type", "application/x-www-form-urlencoded").POST(ofNameValuePairs(postVariables))
				.build();

		if(log.isDebugEnabled())
			log.debug("Executing request " + request.toString());

		var response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			var ctype = response.headers().firstValue("Content-Type");
			if(ctype.isPresent() && ctype.get().equals("application/json")) {
				var err = mapper.readValue(response.body(), ErrorResponse.class);
				if(err.error_description == null)
					throw new IOException(err.error);
				else
					throw new IOException(err.error_description);
			}
			else {
				throw new IOException(response.body());
			}
		}
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
	
	public final static class ErrorResponse {
		public String error;
		public String error_description;
	}
	
	public final static class ConfigurationPayload {
		public String content;
		public String username;
	}
	
	public final static class BearerToken {
		public String error;
		public String error_description;
		public String access_token;
		public long expires_in;
		public String nonce;
		public String token_type = "Bearer";
		public String refresh_token;
	}
	
	public final static class DeviceCode {
		public String device_code;
		public long expires_in;
		public String user_code;
		public String verification_uri;
		public String verification_uri_complete;
		public int interval;
		
	}
}
