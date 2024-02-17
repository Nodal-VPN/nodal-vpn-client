package com.logonbox.vpn.client.common;

import com.logonbox.vpn.client.common.api.IVpnConnection;
import com.logonbox.vpn.client.common.lbapi.Branding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import jakarta.json.Json;

public class BrandingManager<CONX extends IVpnConnection> {
    public interface BrandDetails {
        Branding branding();
        
        Optional<Path> logo();
        
    }
    
    public interface BrandImage {
    }

    public interface ImageHandler {
        BrandImage create(int width, int height, String color);
        
        void draw(BrandImage bim, Path logoFile);

        void write(BrandImage bim, Path splashFile) throws IOException;

        static ImageHandler dumb() {
            return new ImageHandler() {
                
                @Override
                public void write(BrandImage bim, Path splashFile) throws IOException {
                }
                
                @Override
                public void draw(BrandImage bim, Path logoFile) {
                }
                
                @Override
                public BrandImage create(int width, int height, String color) {
                    return null;
                }
            };
        }
    }
    
    private final class BrandingCacheItem implements BrandDetails {
        private final Branding branding;
        private final long loaded = System.currentTimeMillis();
        private final Path logo;

        private BrandingCacheItem() {
            this(null, null);
        }

        private BrandingCacheItem(Branding branding, Path logo) {
            this.branding = branding;
            this.logo = logo;
        }

        @Override
        public Branding branding() {
            return branding;
        }

        @Override
        public Optional<Path> logo() {
            return Optional.ofNullable(logo);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > loaded + TimeUnit.MINUTES.toMillis(10);
        }
    }
    
    private final static Logger LOG = LoggerFactory.getLogger(BrandingManager.class);
    
    private static final int SPLASH_HEIGHT = 360;
    private static final int SPLASH_WIDTH = 480;

    private final Map<CONX, BrandingCacheItem> brandingCache = new HashMap<>();
    private final VpnManager<CONX> vpnManager;
    private final ImageHandler images;
    private final List<Consumer<Optional<BrandDetails>>> onBrandingChange = new ArrayList<>();
    
    private CONX connection;

    public BrandingManager(VpnManager<CONX> vpnManager, ImageHandler images) {
        this.vpnManager = vpnManager;
        this.images = images;
    }
    
    public void addBrandingChangeListener(Consumer<Optional<BrandDetails>> listener) {
        this.onBrandingChange.add(listener);
    }
    
    public void apply(CONX conx) {
        if(!Objects.equals(conx, this.connection)) {
            this.connection = conx;
            
            var splashFile = getCustomSplashFile();
            var branding = getBranding(conx);
            var logoFile = getCustomLogoFile(conx);
            if (branding == null) {
                LOG.info(String.format("Removing branding."));
                try {
                    Files.deleteIfExists(logoFile);
                } catch (IOException e) {
                }
                splashFile.ifPresent(f -> {
                    try {
                        Files.delete(f);
                    } catch (IOException e) {
                    }
                });
                
                updateVMOptions(null);
            } else {
                if (LOG.isDebugEnabled())
                    LOG.debug("Adding custom branding");
                var logo = branding.branding().logo();
    
                /* Create branded splash */
                var bim = branding.branding().resource() != null ? images.create(SPLASH_WIDTH, SPLASH_HEIGHT, branding.branding().resource().background()) : null;
    
                /* Create logo file */
                if (Utils.isNotBlank(logo)) {
                    var newLogoFile = logoFile;
                    try {
                        if (!Files.exists(newLogoFile)) {
                            LOG.info(String.format("Attempting to cache logo"));
                            if(logo.startsWith("data:")) {
                                var idx = logo.indexOf(',');
                                var sl = logo.substring(5, idx);
                                var pl = logo.substring(idx + 1);
                                var args = sl.split(";");
                                if(args.length > 1) {
                                    if(args[args.length - 1].equals("base64")) {
                                        var data = pl.substring(1);
                                        try (OutputStream out = Files.newOutputStream(newLogoFile)) {
                                            out.write(Base64.getDecoder().decode(data));
                                        }
                                        LOG.info(String.format("Logo cached from base64 data URI to %s", newLogoFile.toUri()));
                                    }
                                    else
                                        throw new IOException("Unsupported image encoding.");
                                }
                                else {
                                    throw new IOException("Invalid URI.");
                                }
                            }
                            else {
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
                            }
                            newLogoFile.toFile().deleteOnExit();
                        }
                        logoFile = newLogoFile;
                        branding = new BrandingCacheItem(branding.branding().logo(logoFile.toUri().toString()), logoFile);
    
                        /* Draw the logo on the custom splash */
                        if(bim != null) {
                            images.draw(bim, logoFile);
                        }
    
                    } catch (Exception e) {
                        LOG.error(String.format("Failed to cache logo"), e);
                        branding = new BrandingCacheItem(branding.branding().logo(null), null);
                    }
                } else {
                    try {
                        Files.deleteIfExists(logoFile);
                    } catch (IOException e) {
                    }
                }
    
                /* Write the splash */
                if (bim != null) {
                    splashFile = getCustomSplashFile();
                    splashFile.ifPresent(f -> {
                        try {
                            images.write(bim, f);
                        } catch (IOException e) {
                            LOG.error(String.format("Failed to write custom splash"), e);
                            try {
                                Files.delete(f);
                            }
                            catch(IOException ioe) {
                                LOG.warn("Failed to delete.", ioe);
                            }
                        }
                        updateVMOptions(f);
                    });
                }
                
                onBrandingChange.forEach(l -> l.accept(branding()));
            }
        }
        
    }
    
    public Optional<BrandDetails> branding() {
        return connection().map(this::getBranding);
    }
    
    public Optional<CONX> connection() {
        return Optional.ofNullable(connection);
    }

    public Optional<Path> getCustomSplashFile() {
        var path = getConfigDir().resolve("lbvpnc-splash.png");
        return Files.exists(path) ? Optional.of(path) : Optional.empty();
    }

    public void removeBrandingChangeListener(Consumer<Optional<BrandDetails>> listener) {
        this.onBrandingChange.remove(listener);
    }

    private BrandDetails getBranding(CONX connection) {
        BrandDetails branding = null;
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

    private BrandDetails getBrandingForConnection(CONX connection) throws UnknownHostException, IOException {
        synchronized (brandingCache) {
            var item = brandingCache.get(connection);
            if (item != null && item.isExpired()) {
                item = null;
            }
            if (item == null) {
                String uri = connection.getBaseUri() + "/app/api/brand/info";

                LOG.info(String.format("Retrieving branding from %s", uri));
                var url = URI.create(uri).toURL();
                var urlConnection = url.openConnection();
                urlConnection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(6));
                urlConnection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(6));
                try (var in = urlConnection.getInputStream()) {
                    var rdr = Json.createReader(in);
                    var brandingObj = Branding.of(rdr.readObject());

                    
                    if(brandingObj.logo() == null || brandingObj.logo().equals("")) {
                        brandingObj = brandingObj.logo(connection.getBaseUri() + "/app/api/brand/logo");
                    }
                    else if(!brandingObj.logo().startsWith("http://") && !brandingObj.logo().startsWith("https://") && !brandingObj.logo().startsWith("data:")) {
                        if(brandingObj.logo().startsWith("/"))
                            brandingObj = brandingObj.logo("https://" + connection.getHostname() + ":" + connection.getPort()
                                + brandingObj.logo());
                        else
                            brandingObj = brandingObj.logo("https://" + connection.getHostname() + ":" + connection.getPort()
                            + connection.getPath() + "/" + brandingObj.logo());
                    }

                    item = new BrandingCacheItem(brandingObj, getCustomLogoFile(connection));
                    brandingCache.put(connection, item);
                }
            }
            return item;
        }
    }

    private Path getConfigDir() {
        var upath = System.getProperty("logonbox.vpn.configuration");
        return upath == null ? App.CLIENT_HOME : Paths.get(upath);
    }

    private Path getCustomLogoFile(CONX connection) {
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
}
