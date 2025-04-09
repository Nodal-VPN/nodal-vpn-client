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

import org.eclipse.swt.browser.Browser;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class BoundObjects2 {

	private final Map<String, Binding2> members = new LinkedHashMap<>();
	private final Map<Object, Binding2> objects = new HashMap<>();
	private final Browser browser;

	public BoundObjects2(Browser browser) {
		this.browser = browser;
	}

	public void dispose() {
		for (var b : members.values())
			b.dispose();
		members.clear();
		objects.clear();
	}

	public boolean containsObject(Object object) {
		return objects.containsKey(object);
	}
	
	public Binding2 bind(Class<?> type, Object object) {
		return bind(type, Binding.toFqn(type, object), object);
	}
	
	public Binding2 bind(Class<?> type, String name, Object object) {
		if (members.containsKey(name))
			throw new IllegalStateException(String.format("%s is already bound.", name));
		var b = new Binding2(name, browser, type, object, this);
		members.put(name, b);
		objects.put(object, b);
		return b;
	}

	public String toJs() {
		var buf = new StringBuilder();
		for (var m : members.entrySet()) {
			buf.append(m.getValue().toBindingJs());
			buf.append("\n");
		}
		return buf.toString();
	}

	public String getVar(Object object) {
		var b = objects.get(object);
		if(b == null)
			throw new IllegalStateException("Not bound.");
		return b.getName();
	}
}
