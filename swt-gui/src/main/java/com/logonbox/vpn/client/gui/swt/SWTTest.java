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

import com.equo.chromium.ChromiumBrowser;
import com.equo.chromium.swt.Browser;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class SWTTest {

    private static final String URL = "https://docs.equo.dev/main/getting-started/introduction.html";

    public static void main(String[] args) throws ClassNotFoundException {
        System.setProperty("chromium.suspend_threads", "false");
        
        if (args.length > 0 && "true".equals(args[0])) {
            System.setProperty("chromium.debug", "true");
        }

        ChromiumBrowser.earlyInit();
        Display display = Display.getDefault();
        Shell shell = new Shell(display);
        shell.setLayout(new GridLayout(1, false));

        if (args.length > 0 && "true".equals(args[0])) {
            shell.setText("Windowless");
            ChromiumBrowser browser = ChromiumBrowser.windowless(URL);
            Button button = new Button(shell, SWT.PUSH);
            button.setText("Print page paragraph");
            button.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    browser.executeJavacript(
                            "console.log(document.getElementsByClassName(\"paragraph\")[0].children[0].innerHTML)");
                }
            });
            shell.setSize(300, 100);
        } else {
            shell.setText(SWTTest.class.getSimpleName());
            Browser browser = new Browser(shell, SWT.NONE);
            browser.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            browser.setUrl(URL);
        }
        shell.open();

        while (!shell.isDisposed()) {
            if (!display.readAndDispatch())
                display.sleep();
        }
    }
}
