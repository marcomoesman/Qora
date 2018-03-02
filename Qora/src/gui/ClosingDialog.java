package gui;

import java.awt.Image;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import controller.Controller;
import lang.Lang;

public class ClosingDialog {
	private static final Logger LOGGER = LogManager.getLogger(ClosingDialog.class);
	private static ClosingDialog instance;
	private JDialog closingDialog;
	private JOptionPane optionPane;

	public static ClosingDialog getInstance() {
		if (instance == null)
			instance = new ClosingDialog();

		return instance;
	}

	class ClosingWorker extends SwingWorker<Void, Void> {
		@Override
		protected Void doInBackground() {
			Controller.getInstance().stopAll();
			return null;
		}

		@Override
		protected void done() {
			ClosingDialog.getInstance().dispose();
			System.exit(0);
		}
	}

	private ClosingDialog() {
		if (Gui.isGuiStarted()) {
			try {
				Gui.getInstance().hideMainFrame();
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}

			// Create closing dialog
			this.closingDialog = new JDialog();

			List<Image> icons = new ArrayList<Image>();
			icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon16.png"));
			icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon32.png"));
			icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon64.png"));
			icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon128.png"));
			this.closingDialog.setIconImages(icons);

			this.closingDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			this.closingDialog.setTitle(Lang.getInstance().translate("Closing..."));

			this.optionPane = new JOptionPane(Lang.getInstance().translate("Saving database. Please wait..."), JOptionPane.INFORMATION_MESSAGE,
					JOptionPane.DEFAULT_OPTION, null, new Object[] {}, null);

			this.closingDialog.setContentPane(this.optionPane);

			// this.closingDialog.setUndecorated(true);
			this.closingDialog.setModal(false);
			this.closingDialog.pack();
			this.closingDialog.setLocationRelativeTo(null);
			this.closingDialog.toFront();
			this.closingDialog.setVisible(true);
			this.closingDialog.repaint();

			new ClosingWorker().execute();
		}
	}

	public void updateProgress(String s) {
		if (Gui.isGuiStarted()) {
			this.optionPane.setMessage(Lang.getInstance().translate(s) + "...");
			this.closingDialog.revalidate();
			this.closingDialog.pack();
			this.closingDialog.repaint();
		}
	}

	public void dispose() {
		if (Gui.isGuiStarted())
			this.closingDialog.dispose();
	}
}
