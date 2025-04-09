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
package com.logonbox.vpn.client.tray;

import java.util.ResourceBundle;

import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Resource;

@Bundle
@Resource(siblings = true)
public interface Tray extends AutoCloseable {

	final static ResourceBundle bundle = ResourceBundle.getBundle(Tray.class.getName());
	
	boolean isActive();
	
	void reload();
	
	default boolean isConfigurable() {
		return true;
	}
	
	void loop() throws InterruptedException;
	
	void setProgress(int progress);

	void setAttention(boolean enabled, boolean critical);
}
