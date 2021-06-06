package gui;

import java.awt.Color;
import java.awt.TrayIcon.MessageType;
import java.io.File;

import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.UIManager;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.alee.laf.WebLookAndFeel;

import controller.Controller;
import gui.create.NoWalletFrame;
import gui.create.SettingLangFrame;
import lang.Lang;
import settings.Settings;
import utils.SysTray;

public final class Gui extends JFrame {

	private static final Logger LOGGER = LogManager.getLogger(Gui.class);
	private static final long serialVersionUID = -7005165913208013085L;

	private static Gui gui;
	private static MainFrame mainFrame;

	public static Gui getInstance() throws Exception {
		if (gui == null) {
			gui = new Gui();
		}

		return gui;
	}

	private Gui() throws Exception {
		// Use web look and feel
		WebLookAndFeel.install ();
		UIManager.put("RadioButton.focus", new Color(0, 0, 0, 0));
		UIManager.put("Button.focus", new Color(0, 0, 0, 0));
		UIManager.put("TabbedPane.focus", new Color(0, 0, 0, 0));
		UIManager.put("ComboBox.focus", new Color(0, 0, 0, 0));
		UIManager.put("TextArea.font", UIManager.get("TextField.font"));
	}

	public void startupCompleted() {
		SplashFrame.getInstance().setVisible(false);
		SplashFrame.getInstance().dispose();

		if (Settings.getInstance().Dump().containsKey("lang")) {
			if (!Settings.getInstance().getLang().equals(Settings.DEFAULT_LANGUAGE)) {
				final File langFile = new File(Settings.getInstance().getLangDir(), Settings.getInstance().getLang());
				if (!langFile.isFile()) {
					new SettingLangFrame();
				}
			}
		} else {
			try {
				new SettingLangFrame();
			} catch (Exception e) {
				LOGGER.error(e);
			}
		}

		try {
			// If there's no wallet, open wallet creation frame
			if (!Controller.getInstance().doesWalletExists()) {
				new NoWalletFrame(this);
			} else {
				createSysTrayAndMainFrame();
			}
		} catch (Exception e) {
			LOGGER.error(e);
		}
	}

	private void createSysTrayAndMainFrame() {
		try {
			if (Settings.getInstance().isSysTrayEnabled()) {
				SysTray.getInstance().createTrayIcon();
			}

			// isGuiEnabled() used to determine whether this was shown
			// but really this should be something more like !startMinimized()
			mainFrame = new MainFrame();
		} catch (Exception e) {
			LOGGER.error(e);
		}
	}

	public static boolean isGuiStarted() {
		return gui != null;
	}

	public void onWalletCreated() {
		SysTray.getInstance().sendMessage(Lang.getInstance().translate("Wallet Initialized"),
				Lang.getInstance().translate("Your wallet is initialized"), MessageType.INFO);

		createSysTrayAndMainFrame();
	}

	public void bringtoFront() {
		if (mainFrame != null) {
			mainFrame.toFront();
		}
	}

	public void hideMainFrame() {
		if (mainFrame != null) {
			mainFrame.setVisible(false);
		}
	}

	public void onCancelCreateWallet() {
		Controller.getInstance().stopAll();
		System.exit(0);
	}

	public static <T extends TableModel> JTable createSortableTable(final T tableModel, final int defaultSort) {
		// Create table
		final JTable table = new JTable(tableModel);

		// Create sorter
		final TableRowSorter<T> rowSorter = new TableRowSorter<T>(tableModel);

		// Default sort descending
		rowSorter.toggleSortOrder(defaultSort);
		rowSorter.toggleSortOrder(defaultSort);

		// Add to table
		table.setRowSorter(rowSorter);
		return table;
	}

	public static <T extends TableModel> JTable createSortableTable(final T tableModel, final int defaultSort,
			final RowFilter<T, Object> rowFilter) {
		// Create table
		final JTable table = new JTable(tableModel);

		// Create sorter
		final TableRowSorter<T> rowSorter = new TableRowSorter<T>(tableModel);
		rowSorter.setRowFilter(rowFilter);

		// Default sort descending
		rowSorter.toggleSortOrder(defaultSort);
		rowSorter.toggleSortOrder(defaultSort);

		// Add to table
		table.setRowSorter(rowSorter);
		return table;
	}

}
