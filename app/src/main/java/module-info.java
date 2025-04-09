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

open module com.jadaptive.nodal.vpn.client.app {

    exports com.logonbox.vpn.client.app;

    requires transitive com.jadaptive.nodal.vpn.client.common;
    requires info.picocli;
    requires static com.install4j.runtime;
    requires static uk.co.bithatch.nativeimage.annotations;
    requires com.sshtools.liftlib;
    requires java.logging;
    requires com.jadaptive.nodal.vpn.client.logging;
    requires jul.to.slf4j;
    
    uses DNSProvider.Factory;
}