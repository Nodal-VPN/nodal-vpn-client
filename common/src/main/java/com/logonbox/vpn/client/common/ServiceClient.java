package com.logonbox.vpn.client.common;

import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.common.lbapi.InputField;
import com.logonbox.vpn.client.common.lbapi.LogonResult;
import com.logonbox.vpn.client.common.lbapi.Session;
import com.logonbox.vpn.client.common.lbapi.PeerResponse;
import com.logonbox.vpn.drivers.lib.util.OsUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
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
import java.util.stream.Stream;

import javax.net.ssl.HostnameVerifier;

import jakarta.json.Json;
import jakarta.json.JsonObject;

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

		void collect(JsonObject i18n, LogonResult result, Map<InputField, NameValuePair> results)
				throws IOException;

		void error(JsonObject i18n, LogonResult logonResult);

	}

	static Logger log = LoggerFactory.getLogger(ServiceClient.class);

	private CookieHandler cookieHandler;
	private Authenticator authenticator;
	private CookieStore cookieStore;
	private PromptingCertManager certManager;

	public ServiceClient(CookieStore cookieStore, Authenticator authenticator, PromptingCertManager certManager) {
		this.cookieStore = cookieStore;
		this.authenticator = authenticator;
		this.certManager = certManager;
	}

	protected String doGet(IVpnConnection connection, String url, NameValuePair... headers)
			throws IOException, InterruptedException, URISyntaxException {

		if (!url.startsWith("/")) {
			url = "/" + url;
		}

		var client = getHttpClient(connection);
		var builder = HttpRequest.newBuilder(new URI(connection.getUri(false) + url));
		
		Stream.of(headers).forEach(hdr -> builder.header(hdr.getName(), hdr.getValue()));
		
		var request = builder.build();

		log.info("Executing request " + request.toString());

		var response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200)
			throw new IOException("Expected status code 200 for doPost. " + response.statusCode());
		return response.body();
	}

	protected HttpClient getHttpClient(IVpnConnection connection) {
		if (cookieHandler == null) {
//			cookieHandler = CookieManager.getDefault();
			cookieHandler = new CookieManager(cookieStore, CookiePolicy.ACCEPT_ALL);
			HttpCookie cookie = new HttpCookie(VpnManager.DEVICE_IDENTIFIER, authenticator.getUUID());
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

		return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).
				cookieHandler(cookieHandler).sslContext(certManager.getSSLContext()).sslParameters(certManager.getSSLParameters())
				.connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();

	}

	protected JsonObject parseJSON(String json) {
		if (log.isDebugEnabled()) {
			log.debug(json);
		}

		try(var rdr = Json.createReader(new StringReader(json))) {
		    return rdr.readObject();
		}
	}

	public void register(IVpnConnection connection) throws IOException, URISyntaxException {
		Session session;
		try {
			session = auth(connection);
		} catch (InterruptedException e) {
			throw new IOException("Authentication interrupted.");
		}
		var deviceId = authenticator.getUUID();
		try {
			try {
				var keyParameters = new StringBuilder();
				keyParameters.append("os=" + OsUtil.getOS());
				keyParameters.append('&');
				keyParameters.append("mode=" + connection.getMode());
				keyParameters.append('&');
				keyParameters.append("deviceName=" + URLEncoder.encode(OsUtil.getOS(), "UTF-8"));
				if (Utils.isNotBlank(connection.getUserPublicKey())) {
					keyParameters.append('&');
					keyParameters.append("publicKey=" + URLEncoder.encode(connection.getUserPublicKey(), "UTF-8"));
				}
				keyParameters.append('&');
				keyParameters.append("token=" + URLEncoder.encode(session.csrfToken(), "UTF-8"));
				
				log.info(String.format("Retrieving peers for %s", deviceId));
				
				var obj = parseJSON(doGet(connection, "/api/peers/get?" + keyParameters.toString()));
				var result = PeerResponse.of(obj);

				if (result.success()) {
					log.info("Retrieved peers");
					var deviceUUID = result.deviceUUID();
					if (Utils.isNotBlank(deviceUUID) && result.resource() == null) {
						obj = parseJSON(doGet(connection, "/api/peers/register?" + keyParameters.toString()));
						result = PeerResponse.of(obj);
						if (result.success()) {
							log.info(String.format("Device UUID registered. %s", deviceUUID));
							configure(connection, result.content(),
									session.currentPrincipal().principalName());
						} else {
							throw new IOException("Failed to register. " + result.message());
						}
					} else {
						log.info("Already have UUID, passing on configuration");
						configure(connection, result.content(), session.currentPrincipal().principalName());
					}
				} else
					throw new IOException("Failed to query for existing peers. " + result.message());
			} finally {
				try {
					doGet(connection, "/api/logoff?token=" + URLEncoder.encode(session.csrfToken(), "UTF-8"));
				}
				catch(Exception e) {
					log.warn("Failed to logoff session from server. Continuing, but the session may still exist on the server until it times-out.", e);
				}
			}
		} catch (InterruptedException ie) {
			throw new IOException("Interrupted.", ie);
		}
	}

	protected void configure(IVpnConnection config, String configIniFile, String usernameHint) throws IOException {
		log.info(String.format("Configuration for %s", usernameHint));
		config.setUsernameHint(usernameHint);
		
		var error = config.parse(configIniFile);
		if (Utils.isNotBlank(error))
			throw new IOException(error);

		config.save();
		authenticator.authorized();
		config.authorized();
	}

	protected Session auth(IVpnConnection connection) throws IOException, URISyntaxException, InterruptedException {

		JsonObject i18n;
		try {
			/* 2.4 server */
			i18n = parseJSON(doGet(connection, "/api/i18n/group/_default_i18n_group"));
		} catch (IOException cpe) {
			/* 2.3 server */         
		    i18n = parseJSON(doGet(connection, "/api/i18n"));
		}

		// main.getServer().
		var obj = parseJSON(doGet(connection, "/api/logon"));

		LogonResult result;
		var results = new HashMap<InputField, NameValuePair>();
		while (true) {
			result = LogonResult.of(obj);

			if (result.success())
				throw new IllegalStateException("Didn't expect to be already logged in.");

			authenticator.collect(i18n, result, results);

			result = LogonResult.of(parseJSON(doPost(connection, "/api/logon", results.values().toArray(new NameValuePair[0]))));

			if (result.success()) {
				return result.session();
			} else {
				authenticator.error(i18n, result);
			}

			if (result.isLast())
				break;

			result = LogonResult.of(parseJSON(doGet(connection, "/api/logon")));
		}

		throw new IOException("Authentication failed.");
	}

	protected String doPost(IVpnConnection connection, String url, NameValuePair... postVariables)
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
