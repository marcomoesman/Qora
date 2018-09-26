package network;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import lang.Lang;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import settings.Settings;
import controller.Controller;

public class ConnectionAcceptor extends Thread {

	private ConnectionCallback callback;

	private static final Logger LOGGER = LogManager.getLogger(ConnectionAcceptor.class);
	private ServerSocket socket;

	private boolean isRun;

	public ConnectionAcceptor(ConnectionCallback callback) {
		this.callback = callback;
	}

	public void run() {
		Thread.currentThread().setName("ConnAcceptor");

		this.isRun = true;

		while (isRun) {
			try {
				// START LISTENING
				if (socket == null)
					socket = new ServerSocket(Controller.getInstance().getNetworkPort());

				// CHECK IF WE HAVE MAX CONNECTIONS CONNECTIONS
				if (callback.getActiveConnections().size() >= Settings.getInstance().getMaxConnections()) {
					// IF SOCKET IS OPEN CLOSE IT
					if (!socket.isClosed())
						socket.close();

					Thread.sleep(5 * 1000);
				} else {
					// REOPEN SOCKET
					if (socket.isClosed())
						socket = new ServerSocket(Controller.getInstance().getNetworkPort());

					// ACCEPT CONNECTION
					Socket connectionSocket = socket.accept();
					InetAddress connectionAddress = connectionSocket.getInetAddress();

					// If we're shutting down then discard new connection and exit
					if (!isRun)
						break;

					// CHECK IF SOCKET IS NOT LOCALHOST || WE ARE ALREADY CONNECTED TO THAT SOCKET || BLACKLISTED
					if (Network.isHostLocalAddress(connectionAddress)) {
						// DO NOT CONNECT TO OURSELF/EXISTING CONNECTION
						LOGGER.debug("Connection rejected from local " + connectionAddress);
						connectionSocket.close();
						continue;
					}

					if (PeerManager.getInstance().isBlacklisted(connectionAddress)) {
						// DO NOT CONNECT TO BLACKLISTED PEER
						LOGGER.debug("Connection rejected from blacklisted " + connectionAddress);
						connectionSocket.close();
						continue;
					}

					// CREATE PEER
					LOGGER.debug("Connection accepted from " + connectionAddress);
					new Peer(callback, connectionSocket);
				}
			} catch (SocketException e) {
				if (this.isRun) {
					LOGGER.error(e.getMessage(), e);
					LOGGER.warn(Lang.getInstance().translate("Error accepting new connection"));
				}
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
				LOGGER.warn(Lang.getInstance().translate("Error accepting new connection"));
			}
		}
	}

	public void halt() {
		this.isRun = false;

		if (socket != null && !socket.isClosed()) {
			try {
				socket.close();
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
	}
}
