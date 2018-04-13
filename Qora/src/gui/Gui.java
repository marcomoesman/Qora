package gui;

import javax.swing.JFrame;
import javax.swing.UIManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gui.SplashFrame;
import settings.Settings;
import gui.SysTray;

public class Gui extends JFrame {

	private static final Logger LOGGER = LogManager.getLogger(Gui.class);
	private static final long serialVersionUID = 1L;
	private static Gui maingui;

	public static Gui getInstance() throws Exception {
		if (maingui == null)
			maingui = new Gui();

		return maingui;
	}

	private Gui() throws Exception {
		// Use system's style
		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
	}

	public void onStartupCompleted() {
		SplashFrame.getInstance().setVisible(false);
		SplashFrame.getInstance().dispose();

		try {
			if (Settings.getInstance().isSysTrayEnabled())
				SysTray.getInstance().createTrayIcon();
		} catch (Exception e) {
			LOGGER.error(e);
		}
	}

	public static boolean isGuiStarted() {
		return maingui != null;
	}

}
