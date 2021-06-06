package gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Image;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JLabel;

import lang.Lang;

public class SplashFrame {
	private static SplashFrame instance;
	private JDialog splashDialog;
	private JLabel splashProgressLabel;

	public static SplashFrame getInstance() {
		if (instance == null)
			instance = new SplashFrame();
		
		return instance;
	}
	
	private SplashFrame() {
		if (Gui.isGuiStarted()) {
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
		if (Gui.isGuiStarted()) {
			this.splashProgressLabel.setText(Lang.getInstance().translate(s) + "...");
			this.splashDialog.pack();
			this.splashDialog.repaint();
		}
	}

	public void setVisible(boolean b) {
		if (Gui.isGuiStarted())
			this.splashDialog.setVisible(b);
	}
	
	public void dispose() {
		if (Gui.isGuiStarted())
			this.splashDialog.dispose();
	}
}
