package gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

import gui.status.StatusPanel;
import lang.Lang;
import settings.Settings;

@SuppressWarnings("serial")
public final class MainFrame extends JFrame {

	public MainFrame() {
		// Create frame
		super(Lang.getInstance().translate("Qora"));

		if (Settings.getInstance().isTestnet()) {
			setTitle(Lang.getInstance().translate("Qora TestNet ") + Settings.getInstance().getGenesisStamp());
		}

		// Set icons
		final List<Image> icons = new ArrayList<Image>();
		icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon16.png"));
		icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon32.png"));
		icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon64.png"));
		icons.add(Toolkit.getDefaultToolkit().getImage("images/icons/icon128.png"));
		setIconImages(icons);

		// Menu
		final Menu menu = new Menu();

		// Add menu to frame
		setJMenuBar(menu);

		// General tab pane
		final GeneralTabPane generalTabPane = new GeneralTabPane();

		// Add tab pane to menu
		add(generalTabPane);

		// Add status panel to menu
		add(new StatusPanel(), BorderLayout.SOUTH);

		// Add closing listener
		addWindowListener(new WindowAdapter() {
			public void windowClosing(final WindowEvent event) {
				ClosingDialog.getInstance();
			}
		});

		// Show frame
		pack();
		setLocationRelativeTo(null);
		setSize(new Dimension(760, 680));
		setVisible(true);
	}
}
