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
package com.logonbox.vpn.client.gui.jfx.debugger;

import netscape.javascript.JSObject;

public class JfxDebugProxyJsBridgeDelegate implements JfxDebugProxyJsBridge {
    final private JfxDebugProxyJsBridge myBridge;

    public JfxDebugProxyJsBridgeDelegate(final JfxDebugProxyJsBridge bridge) {
        myBridge = bridge;
    }

    @Override public void consoleLog(final String type, final JSObject args) {myBridge.consoleLog(type, args);}

    @Override public void println(final String text) {myBridge.println(text);}

    @Override public void print(final String text) {myBridge.print(text);}

    @Override public void pageLoadComplete() {myBridge.pageLoadComplete();}

    @Override public void setEventHandledBy(final String handledBy) {myBridge.setEventHandledBy(handledBy);}

    @Override public Object getState(final String name) {return myBridge.getState(name);}

    @Override public void setState(final String name, final Object state) {myBridge.setState(name, state);}
}
