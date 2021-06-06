package gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("serial")
public class SplashPanel extends JPanel {
	private static final Logger LOGGER = LogManager.getLogger(SplashPanel.class);
	private BufferedImage image;

	public SplashPanel() {
		try {
			image = ImageIO.read(new File("images/splash.png"));
			this.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
			this.setLayout(new BorderLayout());
		} catch (IOException ex) {
			LOGGER.error(ex);
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		g.drawImage(image, 0, 0, null);
	}
}
