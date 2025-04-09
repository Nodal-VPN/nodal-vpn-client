/**
 * Copyright ©2023-2025 LogonBox Ltd
 * All changes post March 2025 Copyright © ${project.inceptionYear} JADAPTIVE Limited (support@jadaptive.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.logonbox.vpn.client.app;

import com.logonbox.vpn.client.common.LoggingConfig;
import com.logonbox.vpn.client.logging.SimpleLogger;
import com.logonbox.vpn.client.logging.SimpleLoggerConfiguration;

import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.event.Level;

import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.LogManager;

public final class SimpleLoggingConfig implements LoggingConfig {

    private final Level defaultLevel;
    private final Map<Audience, String> resources;
    private Level level;

    public SimpleLoggingConfig(Level defaultLevel, Map<Audience, String> resources) {
        this.defaultLevel = defaultLevel;
        this.resources= resources;

    }

    @Override
    public void init(Audience audience, Optional<Level> level) {
        var resource = resources.get(audience);
        if(resource == null && audience == Audience.DEVELOPER) {
            audience = Audience.CONSOLE;
            resource = resources.get(audience);
        }
        if(resource == null && audience == Audience.CONSOLE) {
            audience = Audience.USER;
            resource = resources.get(audience);
        }
        if(resource == null) {
            throw new IllegalArgumentException(MessageFormat.format("No resource path for audience {0}", audience));
        }
        
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        
        System.setProperty(SimpleLoggerConfiguration.CONFIGURATION_FILE_KEY, resource);
        level.ifPresentOrElse(this::setLevel, () -> setLevel(defaultLevel));
        
    }

    @Override
    public Level getDefaultLevel() {
        return defaultLevel;
    }

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public void setLevel(Level level) {
        this.level = level;
        System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, level.toString());
        java.util.logging.Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(toJulLevel(level));
        for (Handler h : rootLogger.getHandlers()) {
            h.setLevel(toJulLevel(level));
        }
    }

    static java.util.logging.Level toJulLevel(Level defaultLevel) {
        switch(defaultLevel) {
        case TRACE:
            return java.util.logging.Level.FINEST;
        case DEBUG:
            return java.util.logging.Level.FINE;
        case INFO:
            return java.util.logging.Level.INFO;
        case WARN:
            return java.util.logging.Level.WARNING;
        case ERROR:
            return java.util.logging.Level.SEVERE;
        default:
            return java.util.logging.Level.OFF;
        }
    }
}
