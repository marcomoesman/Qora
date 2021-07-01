import gui.Gui;
import gui.SplashFrame;

import java.io.IOException;
import java.util.Scanner;

import javax.swing.JOptionPane;

import lang.Lang;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import settings.Settings;
import api.ApiClient;
import controller.Controller;

public final class Start {

	private static Logger LOGGER = LogManager.getLogger(Start.class);

	public static void main(final String args[]) throws IOException {
		boolean cli = false;

		for (final String arg : args) {
			if (arg.equals("-cli")) {
				cli = true;
				System.out.println("QORA is running in CLI mode. Use \"quit\" to exit.");
			} else if (arg.startsWith("-peers=") && arg.length() > 7) {
				Settings.getInstance().setDefaultPeers(arg.substring(7).split(","));
			} else if (arg.equals("-testnet")) {
				Settings.getInstance().setGenesisStamp(System.currentTimeMillis());
			} else if (arg.startsWith("-testnet=") && arg.length() > 9) {
				try {
					long testnetstamp = Long.parseLong(arg.substring(9));

					if (testnetstamp == 0) {
						testnetstamp = System.currentTimeMillis();
					}

					Settings.getInstance().setGenesisStamp(testnetstamp);
				} catch (Exception e) {
					Settings.getInstance().setGenesisStamp(Settings.DEFAULT_MAINNET_STAMP);
				}
			}
		}

		Gui gui = null;

		// At least one of GUI or RPC must be enabled
		if (!cli && !Settings.getInstance().isGuiEnabled() && !Settings.getInstance().isRpcEnabled()) {
			System.out.println(Lang.getInstance().translate("Both GUI and RPC cannot be disabled!"));
			System.exit(0);
		}

		LOGGER.info(Lang.getInstance().translate("Starting %qora% v%version% (build date: %builddate%)")
				.replace("%version%", Controller.getInstance().getVersion()).replace("%builddate%", Controller.getInstance().getBuildDateString())
				.replace("%qora%", Lang.getInstance().translate("Qora")));

		// If GUI enabled and not CLI mode, start it now
		if (Settings.getInstance().isGuiEnabled() && !cli) {
			try {
				gui = Gui.getInstance();
				// Create splash screen
				SplashFrame.getInstance();
			} catch (Exception e) {
				LOGGER.error(Lang.getInstance().translate("GUI ERROR"), e);
				System.exit(0);
			}
		}

		try {
			// Start Controller (networking/blockchain/RPC)
			Controller.getInstance().start();
		} catch (Exception e) {
			LOGGER.error(Lang.getInstance().translate("STARTUP ERROR") + ": " + e.getMessage());
			LOGGER.error(e.getMessage(), e);

			// Possibly report via GUI
			if (Gui.isGuiStarted()) {
				JOptionPane.showMessageDialog(null, e.getMessage(), Lang.getInstance().translate("Startup Error"), JOptionPane.ERROR_MESSAGE);
			}

			// Can't continue if Controller doesn't start, so exit
			System.exit(0);
		}

		// Controller started OK, inform GUI
		if (Settings.getInstance().isGuiEnabled() && gui != null) {
			gui.startupCompleted();
		}

		if (cli) {
			final Scanner scanner = new Scanner(System.in);
			final ApiClient client = new ApiClient();

			while (true) {
				System.out.print("[COMMAND] ");
				final String command = scanner.nextLine();

				if (command.equals("quit")) {
					scanner.close();
					System.exit(0);
				}

				final String result = client.executeCommand(command);
				System.out.println("[RESULT] " + result);
			}
		}
	}
}