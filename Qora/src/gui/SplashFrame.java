package gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import lang.Lang;
import settings.Settings;

public class SplashFrame {

	protected static final Logger LOGGER = LogManager.getLogger(SplashFrame.class);
	private static SplashFrame instance;
	private JDialog splashDialog;
	private JLabel splashProgressLabel;

	@SuppressWarnings("serial")
	private class SplashPanel extends JPanel {
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

	public static SplashFrame getInstance() {
		if (instance == null)
			instance = new SplashFrame();

		return instance;
	}

	private SplashFrame() {
		if (Gui.isGuiStarted() && Settings.getInstance().isStartupSplashEnabled()) {
			this.splashDialog = new JDialog();

			List<Image> icons = new ArrayList<Image>();
			icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon16.png"));
			icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon32.png"));
			icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon64.png"));
			icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon128.png"));
			this.splashDialog.setIconImages(icons);

			this.splashDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			this.splashDialog.setTitle(Lang.getInstance().translate("Qora"));
			this.splashDialog.setContentPane(new SplashPanel());

			this.splashProgressLabel = new JLabel();
			this.splashProgressLabel.setForeground(Color.WHITE);
			this.splashDialog.getContentPane().add(this.splashProgressLabel, BorderLayout.SOUTH);

			this.splashDialog.setUndecorated(true);
			this.splashDialog.setModal(false);
			this.splashDialog.pack();
			this.splashDialog.setLocationRelativeTo(null);
			this.splashDialog.toFront();
			this.splashDialog.setVisible(true);
			this.splashDialog.repaint();

			updateProgress("Starting up");
		}
	}

	public void updateProgress(String s) {
		if (Gui.isGuiStarted() && Settings.getInstance().isStartupSplashEnabled()) {
			this.splashProgressLabel.setText(Lang.getInstance().translate(s) + "...");
			this.splashDialog.pack();
			this.splashDialog.repaint();
		}
	}

	public void setVisible(boolean b) {
		if (Gui.isGuiStarted() && Settings.getInstance().isStartupSplashEnabled())
			this.splashDialog.setVisible(b);
	}

	public void dispose() {
		if (Gui.isGuiStarted() && Settings.getInstance().isStartupSplashEnabled())
			this.splashDialog.dispose();
	}
}
