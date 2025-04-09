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

open module com.jadaptive.nodal.vpn.client.desktop {
    exports com.logonbox.vpn.client.desktop;
    requires transitive com.jadaptive.nodal.vpn.client.gui.jfx;
    requires transitive com.jadaptive.nodal.vpn.client.dbus.app;
    requires transitive com.jadaptive.nodal.vpn.client.dbus.client;
    requires transitive com.sshtools.jajafx;
    requires com.install4j.runtime;
    requires org.scenicview.scenicview;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires com.jadaptive.nodal.vpn.client.logging;
    requires com.jadaptive.nodal.vpn.client.app;
    requires com.goxr3plus.fxborderlessscene;
    requires com.jthemedetector;
    requires java.desktop;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
} 