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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.logonbox.vpn.client.common.Utils;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class Binding2 {
	final static Logger LOG = LoggerFactory.getLogger(Binding2.class);

	private String js;
	private Browser browser;
	private List<BrowserFunction> functions = new ArrayList<>();
	private BoundObjects2 bindings;
	private String name;

	Binding2(Object object) {
		this(null, null, object == null ? null : object.getClass(), object, null);
	}

	Binding2(String name, Browser browser, Class<?> type, Object object, BoundObjects2 bindings) {
		this.browser = browser;
		this.bindings = bindings;
		this.name = name;
		js = renderValue(type, object);
	}

	@SuppressWarnings("unchecked")
	protected String renderValue(Class<?> type, Object object) {
		if (object == null) {
			return "null";
		} else if (type.isArray()) {
			Object[] arr = (Object[]) object;
			var l = new ArrayList<String>();
			for (var obj : arr) {
				l.add(renderValue(obj.getClass(), obj));
			}
			return "[" + String.join(",", l) + "]";
		} else if (Collection.class.isAssignableFrom(type)) {
			var l = new ArrayList<String>();
			for (var obj : (Collection<Object>) object) {
				l.add(renderValue(obj.getClass(), obj));
			}
			return "[" + String.join(",", l) + "]";
		} else if (type.equals(String.class)) {
			return "'" + Utils.escapeEcmaScript((String) object) + "'";
		} else if (Number.class.isAssignableFrom(type) || Boolean.class.isAssignableFrom(type)) {
			return String.valueOf(object);
		} else {
			return renderObject(type, object);
		}
	}

	public String getName() {
		return name;
	}

	public void dispose() {
		for (var f : functions)
			f.dispose();
	}

	public String toJs() {
		return js;
	}

	public String toBindingJs() {
		var buf = new StringBuilder();
		buf.append("window.");
		buf.append(getName());
		buf.append(" = ");
		buf.append(toJs());
		;
		buf.append(";");
		return buf.toString();
	}

	static String toFqn(Object obj) {
		return toFqn(obj.getClass(), obj);
	}

	static String toFqn(Class<?> type, Object obj) {
		return type.getName().replace(".", "_") + "_" + String.valueOf(obj.hashCode()).replace("-", "_");
	}

	static String toFqn(Class<?> type, Object obj, String name) {
		return toFqn(type, obj) + "_" + name;
	}

	boolean isExclude(Method method) {
		if (Modifier.isStatic(method.getModifiers()))
			return true;
		if (method.getName().equals("toString") || method.getName().equals("equals")
				|| method.getName().equals("notifyAll") || method.getName().equals("notify")
				|| method.getName().equals("wait") || method.getName().equals("hashCode"))
			return true;
		return false;
	}

	String renderObject(Class<?> type, Object object) {
		StringBuilder buf = new StringBuilder();
		buf.append("{");
		int mc = 0;
		var l = new HashSet<String>();
		for (var m : type.getMethods()) {
			if (l.contains(m.getName()))
				continue;
			l.add(m.getName());
			if (isExclude(m))
				continue;

			var n = toFqn(type, object, m.getName());

			if (bindings != null) {
				var f = new ProxiedFunction(browser, n, object, m, this);
				functions.add(f);
			}

			if (mc > 0)
				buf.append(", ");
			buf.append(m.getName());
			buf.append(":");
			buf.append("function(");
			int c = 0;
			for (var prm : m.getParameters()) {
				if (c > 0)
					buf.append(", ");
				buf.append(prm.getName());
				c++;
			}
			buf.append(") {");
			if (!m.getReturnType().equals(Void.TYPE)) {
				buf.append("return eval(");
			}
			buf.append(n);
			buf.append("(");
			c = 0;
			for (var prm : m.getParameters()) {
				if (c > 0)
					buf.append(", ");
				if (JsonElement.class.isAssignableFrom(prm.getType())) {
					buf.append("JSON.stringify(");
					buf.append(prm.getName());
					buf.append(")");
				} else {
					buf.append(prm.getName());
				}
				c++;
			}
			buf.append(")");
			if (!m.getReturnType().equals(Void.TYPE)) {
				buf.append(")");
			}
			buf.append(";");
			buf.append("}");
			mc++;
		}
		buf.append("}");
		return buf.toString();
	}

	static class ProxiedFunction extends BrowserFunction {

		private final Method method;
		private final Object object;
		private final Binding2 binding;

		public ProxiedFunction(Browser browser, String name, Object object, Method method, Binding2 binding) {
			super(browser, name);
			this.method = method;
			this.object = object;
			this.binding = binding;
		}

		@Override
		public Object function(Object[] arguments) {
			try {
				var l = new ArrayList<Object>();
				for (int i = 0; i < arguments.length; i++) {
					var o = arguments[i];
					var param = method.getParameters()[i];
					Class<?> ptype = param.getType();
					if((ptype == int.class || ptype.equals(Integer.class)) && o instanceof Number) {
						l.add(((Number)o).intValue());
					}
					else if((ptype == float.class || ptype.equals(Float.class)) && o instanceof Number) {
						l.add(((Number)o).floatValue());
					}
					else if((ptype == double.class || ptype.equals(Double.class)) && o instanceof Number) {
						l.add(((Number)o).doubleValue());
					}
					else if(( ptype.equals(Long.class) || ptype.equals(long.class) ) && o instanceof Number) {
						l.add(((Number)o).longValue());
					}
					else if((ptype ==short.class || ptype.equals(Short.class)) && o instanceof Number) {
						l.add(((Number)o).shortValue());
					}
					else if (ptype.equals(JsonObject.class)) {
						l.add(JsonParser.parseString((String) o).getAsJsonObject());
					} else
						l.add(o);
				}
				return checkObject(method.invoke(object, l.toArray(new Object[0])));
			} catch (Exception e) {
				LOG.error("Failed to execute Java method " + method.getName() + "' from Javascript.", e);
				throw new IllegalStateException("Failed to execute Java method '" + method.getName() + "' from Javascript.", e);
			}
		}

		@SuppressWarnings("unchecked")
		String checkObject(Object res) {
			if (res == null) {
				return null;
			} else if (res.getClass().isArray()) {
				Object[] arr = (Object[]) res;
				var l = new ArrayList<String>();
				for (var obj : arr) {
					l.add(checkObject(obj));
				}
				return "[" + String.join(",", l) + "]";
			} else if (Collection.class.isAssignableFrom(res.getClass())) {
				var l = new ArrayList<String>();
				for (var obj : (Collection<Object>) res) {
					l.add(checkObject(obj));
				}
				return "[" + String.join(",", l) + "]";
			} else if (res.getClass().equals(String.class)) {
				return "'" + Utils.escapeEcmaScript((String) res) + "'";
			} else if (Number.class.isAssignableFrom(res.getClass())
					|| Boolean.class.isAssignableFrom(res.getClass())) {
				return String.valueOf(res);
			} else {
				if (binding.bindings.containsObject(res)) {
					return binding.bindings.getVar(res);
				} else {
					var name = toFqn(res.getClass(), res);
					var tmpBind = new Binding2(res);
					binding.browser.getDisplay().asyncExec(() -> {
						binding.bindings.bind(res.getClass(), name, res);
					});
					return tmpBind.toJs();
				}
			}
		}
	}
}
