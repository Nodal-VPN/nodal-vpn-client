package com.logonbox.vpn.client.common;

import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.common.lbapi.Branding;

import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.json.Json;

public class BrandingManager<CONX extends IVpnConnection, IMG> {
    private final static Logger LOG = org.slf4j.LoggerFactory.getLogger(BrandingManager.class);

    private static final int SPLASH_HEIGHT = 360;
    private static final int SPLASH_WIDTH = 480;
    
    public interface ImageHandler<IMG> {
        IMG create(int width, int height, String color);
        
        void draw(IMG bim, Path logoFile);

        void write(IMG bim, Path splashFile) throws IOException;
    }

    private final static class BrandingCacheItem {
        private Branding branding;
        private long loaded = System.currentTimeMillis();

        private BrandingCacheItem() {
            this(null);
        }

        private BrandingCacheItem(Branding branding) {
            this.branding = branding;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > loaded + TimeUnit.MINUTES.toMillis(10);
        }
    }

    private final Map<CONX, BrandingCacheItem> brandingCache = new HashMap<>();
    private final VpnManager<CONX> vpnManager;
    private final ImageHandler<IMG> images;

    public BrandingManager(VpnManager<CONX> vpnManager, ImageHandler<IMG> images) {
        this.vpnManager = vpnManager;
        this.images = images;
    }

    public Branding getBranding(CONX connection) {
        Branding branding = null;
        if (connection != null) {
            try {
                branding = getBrandingForConnection(connection);
            } catch (IOException ioe) {
                LOG.info(String.format("Skipping %s:%d because it appears offline.", connection.getHostname(),
                        connection.getPort()));
            }
        } else {
            if (branding == null) {
                for (var conx : vpnManager.getVpn().map(vpn -> vpn.getConnections()).orElse(Collections.emptyList())) {
                    try {
                        branding = getBrandingForConnection(conx);
                    } catch (IOException ioe) {
                        LOG.info(String.format("Skipping %s:%d because it appears offline.", conx.getHostname(),
                                conx.getPort()));
                    }
                    break;
                }
            }
        }
        return branding;
    }

    public Path getCustomSplashFile() {
        return getConfigDir().resolve("lbvpnc-splash.png");
    }

    public Path getConfigDir() {
        var upath = System.getProperty("logonbox.vpn.configuration");
        return upath == null ? App.CLIENT_HOME.toPath() : Paths.get(upath);
    }

    public Path getCustomLogoFile(CONX connection) {
        return getConfigDir().resolve("lpvpnclogo-" + (connection == null ? "default" : connection.getId()));
    }

    private void updateVMOptions(Path splashFile) {
        var vmOptionsFile = getConfigDir().resolve("gui.vmoptions");
        List<String> lines;
        if (Files.exists(vmOptionsFile)) {
            try (var r = Files.newBufferedReader(vmOptionsFile)) {
                lines = Utils.readLines(r);
            } catch (IOException ioe) {
                throw new IllegalStateException("Failed to read .vmoptions.", ioe);
            }
        } else {
            lines = new ArrayList<>();
        }
        for (var lineIt = lines.iterator(); lineIt.hasNext();) {
            var line = lineIt.next();
            if (line.startsWith("-splash:")) {
                lineIt.remove();
            }
        }
        if (splashFile != null && Files.exists(splashFile)) {
            lines.add(0, "-splash:" + splashFile.toAbsolutePath().toString());
        }
        if (lines.isEmpty()) {
            try {
                Files.delete(vmOptionsFile);
            } catch (IOException e) {
            }
        } else {
            try (BufferedWriter r = Files.newBufferedWriter(vmOptionsFile)) {
                Utils.writeLines(lines, System.getProperty("line.separator"), r);
            } catch (IOException ioe) {
                throw new IllegalStateException("Failed to read .vmoptions.", ioe);
            }
        }
    }

    private Branding getBrandingForConnection(CONX connection) throws UnknownHostException, IOException {
        synchronized (brandingCache) {
            var item = brandingCache.get(connection);
            if (item != null && item.isExpired()) {
                item = null;
            }
            if (item == null) {
                item = new BrandingCacheItem();
                brandingCache.put(connection, item);
                String uri = connection.getUri(false) + "/api/brand/info";

                LOG.info(String.format("Retrieving branding from %s", uri));
                var url = URI.create(uri).toURL();
                var urlConnection = url.openConnection();
                urlConnection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(6));
                urlConnection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(6));
                try (var in = urlConnection.getInputStream()) {
                    var rdr = Json.createReader(in);
                    var brandingObj = Branding.of(rdr.readObject());

                    brandingObj = brandingObj.logo("https://" + connection.getHostname() + ":" + connection.getPort()
                            + connection.getPath() + "/api/brand/logo");

                    item.branding = brandingObj;
                }
            }
            return item.branding;
        }
    }

    public void apply(CONX conx) {
        var splashFile = getCustomSplashFile();
        var branding = getBranding(conx);
        var logoFile = getCustomLogoFile(conx);
        if (branding == null) {
            LOG.info(String.format("Removing branding."));
            try {
                Files.deleteIfExists(logoFile);
            } catch (IOException e) {
            }
            try {
                Files.deleteIfExists(splashFile);
            } catch (IOException e) {
            }
            updateVMOptions(null);
        } else {
            if (LOG.isDebugEnabled())
                LOG.debug("Adding custom branding");
            var logo = branding.logo();

            /* Create branded splash */
            var bim = branding.resource() != null ? images.create(SPLASH_WIDTH, SPLASH_HEIGHT, branding.resource().background()) : null;

            /* Create logo file */
            if (Utils.isNotBlank(logo)) {
                var newLogoFile = logoFile;
                try {
                    if (!Files.exists(newLogoFile)) {
                        LOG.info(String.format("Attempting to cache logo"));
                        URL logoUrl = URI.create(logo).toURL();
                        URLConnection urlConnection = logoUrl.openConnection();
                        urlConnection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10));
                        urlConnection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(10));
                        try (InputStream urlIn = urlConnection.getInputStream()) {
                            try (OutputStream out = Files.newOutputStream(newLogoFile)) {
                                urlIn.transferTo(out);
                            }
                        }
                        LOG.info(String.format("Logo cached from %s to %s", logoUrl, newLogoFile.toUri()));
                        newLogoFile.toFile().deleteOnExit();
                    }
                    logoFile = newLogoFile;
                    branding = branding.logo(logoFile.toUri().toString());

                    /* Draw the logo on the custom splash */
                    if(bim != null) {
                        images.draw(bim, logoFile);
                    }

                } catch (Exception e) {
                    LOG.error(String.format("Failed to cache logo"), e);
                    branding = branding.logo(null);
                }
            } else {
                try {
                    Files.deleteIfExists(logoFile);
                } catch (IOException e) {
                }
            }

            /* Write the splash */
            if (bim != null) {
                try {
                    images.write(bim, splashFile);
                } catch (IOException e) {
                    LOG.error(String.format("Failed to write custom splash"), e);
                    try {
                        Files.delete(splashFile);
                    }
                    catch(IOException ioe) {
                        LOG.warn("Failed to delete.", ioe);
                    }
                }
            }

            updateVMOptions(splashFile);
        }
        
    }
}
