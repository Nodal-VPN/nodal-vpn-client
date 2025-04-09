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
import com.jadaptive.nodal.core.lib.DNSProvider;
import com.logonbox.vpn.client.common.Api;
import com.logonbox.vpn.client.common.PlatformUtilities;

open module com.jadaptive.nodal.vpn.client.common {

    exports com.logonbox.vpn.client.common;
    exports com.logonbox.vpn.client.common.api;
    exports com.logonbox.vpn.client.common.lbapi;

    requires transitive com.jadaptive.nodal.core.lib;
    requires transitive java.net.http;
    requires java.naming;
    requires java.prefs;
    requires java.xml;
    requires transitive com.sshtools.jini.config;
    requires transitive com.sshtools.liftlib;
    requires transitive jakarta.json;
    requires transitive com.sshtools.jaul;
    requires static transitive com.install4j.runtime;
    
    requires static uk.co.bithatch.nativeimage.annotations;
    
    uses DNSProvider.Factory;
    uses Api;
    uses PlatformUtilities;
}