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

import java.text.MessageFormat;
import java.util.Arrays;

public enum AuthMethod {
    DEVICE_TOKEN, EMBEDDED_BROWSER, HTTP_BASIC;
    
    public static AuthMethod[] supported(boolean gui) {
        if(gui) {
            return new AuthMethod[] { EMBEDDED_BROWSER, DEVICE_TOKEN };
        }
        else {
            return new AuthMethod[] { DEVICE_TOKEN, HTTP_BASIC };
        }
    }

    public static AuthMethod select(boolean gui, String[] authMethods) {
        var supportedNames = Arrays.asList(supported(gui)).stream().map(AuthMethod::name).toList();
        for(var s : authMethods) {
            if(supportedNames.contains(s)) {
                return AuthMethod.valueOf(s);
            }
        }
        throw new IllegalArgumentException(MessageFormat.format(
                "None of the supplied authentication methods ({0}) are supported ({1}).", String.join(",", Arrays.asList(authMethods)), String.join(",", supportedNames)));
    }
}
