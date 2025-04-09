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

import com.vladsch.boxed.json.BoxedJsValue;

public class JfxConsoleApiArgs {
    private final Object[] myArgs;
    private final String myLogType;
    private final BoxedJsValue[] myJsonParams;
    private long myTimestamp;
    private String myPausedParam;

    public JfxConsoleApiArgs(final Object[] args, final String logType, final long timestamp) {
        myArgs = args;
        myLogType = logType;
        myJsonParams = new BoxedJsValue[args.length];
        myTimestamp = timestamp;
        myPausedParam = null;
    }

    public String getPausedParam() {
        return myPausedParam;
    }

    public void setPausedParam(final String pausedParam) {
        myPausedParam = pausedParam;
    }

    public void setParamJson(int paramIndex, BoxedJsValue paramJson) {
        myJsonParams[paramIndex] = paramJson;
    }

    public void clearAll() {
        int iMax = myArgs.length;
        for (int i = 0; i < iMax; i++) {
            myArgs[i] = null;
            myJsonParams[i] = null;
        }
    }

    public long getTimestamp() {
        return myTimestamp;
    }

    public Object[] getArgs() {
        return myArgs;
    }

    public String getLogType() {
        return myLogType;
    }

    public BoxedJsValue[] getJsonParams() {
        return myJsonParams;
    }
}
