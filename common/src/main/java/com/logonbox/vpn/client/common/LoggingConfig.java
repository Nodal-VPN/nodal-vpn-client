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
package com.logonbox.vpn.client.common;

import org.slf4j.event.Level;

import java.util.Optional;

public interface LoggingConfig {
    
    public final static class Default {
        private final static LoggingConfig DEFAULT = new LoggingConfig() {

            private Level level;

            @Override
            public Level getDefaultLevel() {
                return Level.INFO;
            }

            @Override
            public void setLevel(Level level) {
                this.level = level;
            }

            @Override
            public Level getLevel() {
                return level == null ? getDefaultLevel() : level;
            }

            @Override
            public void init(Audience audience, Optional<Level> level) {
                level.ifPresent(this::setLevel);
            }
            
        };
    }
    
    public enum Audience {
        DEVELOPER, CONSOLE, USER
    }

    Level getDefaultLevel();

    void setLevel(Level level);

    Level getLevel();

    void init(Audience audience, Optional<Level> level);

    static LoggingConfig dumb() {
        return Default.DEFAULT;
    }
}
