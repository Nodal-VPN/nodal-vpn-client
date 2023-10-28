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
