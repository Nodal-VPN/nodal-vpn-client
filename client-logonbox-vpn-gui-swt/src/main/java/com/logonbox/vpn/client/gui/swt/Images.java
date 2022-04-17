package com.logonbox.vpn.client.gui.swt;

import org.eclipse.swt.graphics.Image;

public class Images {
	public static Image scaleToHeight(Image img, int y) {
		float f = (float) y / img.getImageData().height;
		int x = (int) (f * img.getImageData().width);
		return new Image(img.getDevice(), img.getImageData().scaledTo(x, y));
	}

	public static Image scale(Image img, int x, int y) {
		return new Image(img.getDevice(), img.getImageData().scaledTo(x, y));
	}
}
