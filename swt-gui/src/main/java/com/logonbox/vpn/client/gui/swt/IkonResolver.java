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
package com.logonbox.vpn.client.gui.swt;

import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;
import org.kordamp.ikonli.AbstractIkonResolver;
import org.kordamp.ikonli.IkonHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ServiceLoader;

/**
 * @author Andres Almiray
 */
public class IkonResolver extends AbstractIkonResolver {
    private static final IkonResolver INSTANCE;

    static {
        INSTANCE = new IkonResolver();

        ServiceLoader<IkonHandler> loader = resolveServiceLoader();
        for (IkonHandler handler : loader) {
            HANDLERS.add(handler);
            try {
                handler.setFont(loadFont(handler.getFontResource().openStream(), Display.getDefault()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private IkonResolver() {

    }

    private static ServiceLoader<IkonHandler> resolveServiceLoader() {
        if (null != IkonHandler.class.getModule().getLayer()) {
            return ServiceLoader.load(IkonHandler.class.getModule().getLayer(), IkonHandler.class);
        }
        return ServiceLoader.load(IkonHandler.class);
    }

    public static IkonResolver getInstance() {
        return INSTANCE;
    }

	static FontData loadFont(InputStream in, Display swtDisplay) throws IOException {
		File realFontFile  = File.createTempFile("swt", ".ttf");
		realFontFile.deleteOnExit(); // File must continue to exist on os x
		try (var fos = new FileOutputStream(realFontFile)) {
			in.transferTo(fos);
		}
		if (!swtDisplay.loadFont(realFontFile.getPath())) {
			throw new IllegalStateException("Failed to load font " + realFontFile);
		}
		return new FontData(); // Not actually used
	}

}
