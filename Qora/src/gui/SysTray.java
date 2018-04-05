package gui;

import java.awt.AWTException;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Observable;
import java.util.Observer;

import javax.swing.ImageIcon;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import controller.Controller;
import gui.ClosingDialog;
import settings.Settings;
import utils.ObserverMessage;

public class SysTray implements Observer {

	private static final Logger LOGGER = LogManager.getLogger(SysTray.class);
	private static SysTray systray = null;
	private TrayIcon icon = null;
	private PopupMenu popupMenu;

	public static SysTray getInstance() {
		if (systray == null)
			systray = new SysTray();

		return systray;
	}

	public SysTray() {
		Controller.getInstance().addObserver(this);
	}

	public void createTrayIcon() throws HeadlessException, MalformedURLException, AWTException, FileNotFoundException {
		if (icon != null)
			return;

		if (!SystemTray.isSupported()) {
			LOGGER.info("SystemTray is not supported");
			return;
		}

		// String toolTipText = "Qora " + Controller.getInstance().getVersion();
		popupMenu = createPopupMenu();
		
		this.icon = new TrayIcon(createImage("images/icons/icon32.png", "tray icon"), "Qora " + Controller.getInstance().getVersion(), popupMenu);
		this.icon.setImageAutoSize(true);
		SystemTray.getSystemTray().add(this.icon);
		
		this.icon.displayMessage("QORA is now running in the background", null, TrayIcon.MessageType.NONE);

		this.update(new Observable(), new ObserverMessage(ObserverMessage.NETWORK_STATUS, Controller.getInstance().getStatus()));
	}

	public void sendMessage(String caption, String text, TrayIcon.MessageType messagetype) {
		if (icon == null)
			return;

		icon.displayMessage(caption, text, messagetype);
	}

	// Obtain the image URL
	private Image createImage(String path, String description) throws MalformedURLException, FileNotFoundException {
		File file = new File(path);

		if (!file.exists())
			throw new FileNotFoundException("Iconfile not found: " + path);

		URL imageURL = file.toURI().toURL();
		return (new ImageIcon(imageURL, description)).getImage();
	}

	private PopupMenu createPopupMenu() throws HeadlessException {
		PopupMenu menu = new PopupMenu();

		MenuItem exit = new MenuItem("Exit");
		exit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ClosingDialog.getInstance();
			}
		});
		menu.add(exit);

		return menu;
	}

	public void setToolTipText(String text) {
		this.icon.setToolTip(text);
	}

	@Override
	public void update(Observable arg0, Object arg1) {
		if (this.icon == null)
			return;

		ObserverMessage message = (ObserverMessage) arg1;

		String networkStatus = "";
		String toolTipText = "Qora " + Controller.getInstance().getVersion() + "\n";
		
		if (Settings.getInstance().isTestnet())
			toolTipText += "!TESTNET!\n";

		if (Controller.getInstance().getStatus() == Controller.STATUS_NO_CONNECTIONS) {
			// TODO: Change system tray icon to "no connection" 
			networkStatus = "No connections";
		} else if (Controller.getInstance().getStatus() == Controller.STATUS_SYNCHRONIZING) {
			// TODO: Change system tray icon to "synchronizing" 
			networkStatus = "Synchronizing";
		} else if (Controller.getInstance().getStatus() == Controller.STATUS_OK) {
			networkStatus = "OK";
			
			// TODO: Could test forging status and change system tray icon to either OK or "OK + hammer"?
		}

		if (message.getType() == ObserverMessage.BLOCKCHAIN_SYNC_STATUS) {
			int currentHeight = (int) message.getValue();
			String syncPercent = "";

			if (Controller.getInstance().getStatus() == Controller.STATUS_SYNCHRONIZING)
				syncPercent = 100 * currentHeight / Controller.getInstance().getMaxPeerHeight() + "%";

			toolTipText += networkStatus + " " + syncPercent;
			toolTipText += "\nHeight: " + currentHeight + "/" + Controller.getInstance().getMaxPeerHeight();
		} else {
			toolTipText += networkStatus;
			toolTipText += "\nHeight: " + Controller.getInstance().getHeight();
		}

		// TODO: Could set system tray icon depending on wallet lock status, testnet status, etc.

		setToolTipText(toolTipText);
	}

}