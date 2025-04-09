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
package com.logonbox.vpn.client.desktop;

import com.logonbox.vpn.client.gui.jfx.Navigator;
import com.sshtools.jajafx.JajaFXAppWindow;
import com.sshtools.jajafx.TitleBar;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

public class DesktopVPNAppWindow extends JajaFXAppWindow<DesktopVPNApp> implements Navigator {

    private static final double DEFAULT_HEIGHT = 768;
    private static final double DEFAULT_WIDTH = 457;
    private FontIcon back;

    public DesktopVPNAppWindow(Stage stage, DesktopVPNApp app) {
        super(stage, app, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    @Override
    protected TitleBar createTitleBar() {
        var tbar =  super.createTitleBar();
        tbar.getStylesheets().add(DesktopVPNApp.vpnAppCss());

        back = new FontIcon();
        back.setIconSize(18);
        back.setOnMouseClicked(evt -> ((DesktopVPNApp)app).back());
        back.setIconCode(FontAwesomeSolid.ARROW_LEFT);
        back.managedProperty().bind(back.visibleProperty());
        
        tbar.addAccessories(back);
        
        return tbar;
    }

    @Override
    public void setBackVisible(boolean b) {
        back.setVisible(b);        
    }

    @Override
    public void setImage(Image image) {
        var titleImage = new ImageView(image);
        
        /* Sucks you cant do this in CSS */
        titleImage.setFitWidth(200);
        titleImage.setFitHeight(32);
        titleImage.setPreserveRatio(true);
        
        setTitleImage(titleImage);
    }

    @Override
    public Image getImage() {
        return getTitleImage() == null ? null : getTitleImage().getImage();
    }

}
