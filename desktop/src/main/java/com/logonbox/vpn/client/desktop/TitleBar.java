package com.logonbox.vpn.client.desktop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;

import com.logonbox.vpn.client.gui.jfx.Navigator;
import com.logonbox.vpn.client.gui.jfx.UIContext;
import com.sshtools.liftlib.OS;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Hyperlink;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;

public class TitleBar extends AnchorPane implements Navigator {

	@FXML
	private HBox titleLeft;
	@FXML
	private HBox titleRight;
	@FXML
	private ImageView titleBarImageView;
	@FXML
	private Hyperlink back;
	@FXML
	private Hyperlink minimize;
	@FXML
	private Hyperlink close;
	
	private UIContext<?> context;

	public TitleBar(UIContext<?> context) {
		this.context = context;
		
		var loader = new FXMLLoader(getClass().getResource("TitleBar.fxml"));
		loader.setController(this);
		loader.setRoot(this);
		try {
			loader.load();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		
		back.managedProperty().bind(back.visibleProperty());
	}

	@FXML
	public void initialize() {

		/* If on Mac, swap which side minimize / close is on */
		if (OS.isMacOs()) {
			var tempL = new ArrayList<>(titleLeft.getChildren());
			var tempR = new ArrayList<>(titleRight.getChildren());
			Collections.reverse(tempL);
			Collections.reverse(tempR);
			titleLeft.getChildren().clear();
			titleLeft.getChildren().addAll(tempR);
			titleRight.getChildren().clear();
			titleRight.getChildren().addAll(tempL);
		}
		
		minimize.setVisible(!context.getAppContext().isNoMinimize() && context.isMinimizeAllowed()
				&& context.isUndecoratedWindow());
		minimize.setManaged(minimize.isVisible());
		close.setVisible(!context.getAppContext().isNoClose() && context.isUndecoratedWindow());
		close.setManaged(close.isVisible());
	}

	@FXML
	private void evtClose() {
		context.maybeExit();
	}

	@FXML
	private void evtBack() {
		context.back();
	}

	@FXML
	private void evtMinimize() {
		context.minimize();
	}

	@Override
	public void setBackVisible(boolean visible) {
		back.setVisible(visible);
	}

	@Override
	public void setImage(Image image) {
		titleBarImageView.setImage(image);
	}

	@Override
	public Image getImage() {
		return titleBarImageView.getImage();
	}
}
