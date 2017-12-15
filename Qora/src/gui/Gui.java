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

import controller.Controller;
import gui.create.NoWalletFrame;
import gui.create.SettingLangFrame;
import gui.SplashFrame;
import lang.Lang;
import settings.Settings;
import utils.SysTray;

public class Gui extends JFrame {

	static Logger LOGGER = LogManager.getLogger(Gui.class.getName());

	private static final long serialVersionUID = 1L;

	private static Gui maingui;
	private static MainFrame mainFrame;

	public static Gui getInstance() throws Exception {
		if (maingui == null)
			maingui = new Gui();

		return maingui;
	}

	private Gui() throws Exception {
		// USE SYSTEM STYLE
		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
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
				File langFile = new File(Settings.getInstance().getLangDir(), Settings.getInstance().getLang());
				
				if (!langFile.isFile())
					new SettingLangFrame();
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
			if (!Controller.getInstance().doesWalletExists())
				new NoWalletFrame(this);
			else
				createSysTrayAndMainFrame();
		} catch (Exception e) {
			LOGGER.error(e);
		}
	}

	private void createSysTrayAndMainFrame() {
		try {
			if (Settings.getInstance().isSysTrayEnabled())
				SysTray.getInstance().createTrayIcon();
	
			// isGuiEnabled() used to determine whether this was shown
			// but really this should be something more like !startMinimized()
			mainFrame = new MainFrame();
		} catch (Exception e) {
			LOGGER.error(e);
		}
	}

	public static boolean isGuiStarted() {
		return maingui != null;
	}

	public void onWalletCreated() {
		SysTray.getInstance().sendMessage(Lang.getInstance().translate("Wallet Initialized"),
				Lang.getInstance().translate("Your wallet is initialized"), MessageType.INFO);

		createSysTrayAndMainFrame();
	}

	public void bringtoFront() {
		if (mainFrame != null)
			mainFrame.toFront();
	}

	public void hideMainFrame() {
		if (mainFrame != null)
			mainFrame.setVisible(false);
	}

	public void onCancelCreateWallet() {
		Controller.getInstance().stopAll();
		System.exit(0);
	}

	public static <T extends TableModel> JTable createSortableTable(T tableModel, int defaultSort) {
		// CREATE TABLE
		JTable table = new JTable(tableModel);

		// CREATE SORTER
		TableRowSorter<T> rowSorter = new TableRowSorter<T>(tableModel);
		// drowSorter.setSortsOnUpdates(true);

		// DEFAULT SORT DESCENDING
		rowSorter.toggleSortOrder(defaultSort);
		rowSorter.toggleSortOrder(defaultSort);

		// ADD TO TABLE
		table.setRowSorter(rowSorter);

		// RETURN
		return table;
	}

	public static <T extends TableModel> JTable createSortableTable(T tableModel, int defaultSort,
			RowFilter<T, Object> rowFilter) {
		// CREATE TABLE
		JTable table = new JTable(tableModel);

		// CREATE SORTER
		TableRowSorter<T> rowSorter = new TableRowSorter<T>(tableModel);
		// rowSorter.setSortsOnUpdates(true);
		rowSorter.setRowFilter(rowFilter);

		// DEFAULT SORT DESCENDING
		rowSorter.toggleSortOrder(defaultSort);
		rowSorter.toggleSortOrder(defaultSort);

		// ADD TO TABLE
		table.setRowSorter(rowSorter);

		// RETURN
		return table;
	}

}
