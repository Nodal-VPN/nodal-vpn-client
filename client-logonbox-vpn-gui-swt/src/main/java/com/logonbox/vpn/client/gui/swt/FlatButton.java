package com.logonbox.vpn.client.gui.swt;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;

public class FlatButton {

	private boolean down;
	private boolean over;
	private boolean visible = true;
	private Label button;
	private List<SelectionListener> listeners = new ArrayList<>();

	public FlatButton(Composite parent, int style) {
		button = new Label(parent, style);
		button.addMouseTrackListener(new MouseTrackListener() {
			@Override
			public void mouseHover(MouseEvent e) {
				over = true;
				button.redraw();
			}

			@Override
			public void mouseExit(MouseEvent e) {
				over = false;
				button.redraw();
			}

			@Override
			public void mouseEnter(MouseEvent e) {
				over = true;
				button.redraw();
			}
		});
		button.addMouseListener(new MouseListener() {
			@Override
			public void mouseUp(MouseEvent e) {
				down = false;
				button.redraw();
				Event e2 = new Event();
				e2.button = e.button;
				e2.count = e.count;
				e2.display = e.display;
				e2.data = e.data;
				e2.stateMask = e.stateMask;
				e2.time = e.time;
				e2.widget = e.widget;
				e2.x = e.x;
				e2.y = e.y;
				var le = new SelectionEvent(e2);
				for (var l : listeners) {
					l.widgetSelected(le);
				}
			}

			@Override
			public void mouseDown(MouseEvent e) {
				down = true;
				button.redraw();
			}

			@Override
			public void mouseDoubleClick(MouseEvent arg0) {
			}
		});
		button.addPaintListener(new PaintListener() {
			@Override
			public void paintControl(PaintEvent event) {
				event.gc.setAntialias(SWT.NONE);
				event.gc.setBackground(button.getParent().getBackground());
				event.gc.setForeground(button.getParent().getBackground());
				event.gc.fillRectangle(0, 0, button.getSize().x, button.getSize().y);
				Image image = button.getImage();
				var wasAlpha = event.gc.getAlpha();
				var off = down ? 2 : 0;
				if (!over)
					event.gc.setAlpha(224);
				if (visible && image != null) {
					event.gc.drawImage(image, off + ((button.getSize().x - image.getImageData().width) / 2),
							off + ((button.getSize().y - image.getImageData().height) / 2));
				}
				if (!over)
					event.gc.setAlpha(wasAlpha);
			}
		});
	}

	public void setImage(Image image) {
		button.setImage(image);
	}

	public void addSelectionListener(SelectionListener listener) {
		listeners.add(listener);
	}

	public void setLayoutData(Object layoutData) {
		button.setLayoutData(layoutData);
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
		button.pack();
	}

}
