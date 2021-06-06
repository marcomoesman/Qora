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

public final class ConnectionAcceptor extends Thread {

	private static final Logger LOGGER = LogManager.getLogger(ConnectionAcceptor.class);
	
	private final ConnectionCallback callback;

	private ServerSocket socket;
	private boolean running;

	public ConnectionAcceptor(final ConnectionCallback callback) {
		super("Connection Acceptor");
		this.callback = callback;
	}

	@Override
	public void run() {
		this.running = true;

		while (this.running) {
			try {
				// Create server socket
				if (this.socket == null) {
					this.socket = new ServerSocket(Controller.getInstance().getNetworkPort());
				}

				// Check if we have reached connections limit
				if (this.callback.getActiveConnections().size() >= Settings.getInstance().getMaxConnections()) {
					// Close server socket if open
					if (!this.socket.isClosed()) {
						this.socket.close();
					}

					// Sleep for five seconds
					Thread.sleep(5 * 1000);
				} else {
					// Reopen socket
					if (this.socket.isClosed()) {
						this.socket = new ServerSocket(Controller.getInstance().getNetworkPort());
					}

					// Accept connections
					final Socket connectionSocket = socket.accept();
					final InetAddress connectionAddress = connectionSocket.getInetAddress();

					// If we're shutting down then discard new connection and exit
					if (!this.running) {
						break;
					}

					// Check if peer is localhost
					if (Network.isHostLocalAddress(connectionAddress)) {
						LOGGER.debug("Connection rejected from localhost " + connectionAddress);
						connectionSocket.close();
						continue;
					}

					// Check if peer is blacklisted
					if (PeerManager.getInstance().isBlacklisted(connectionAddress)) {
						LOGGER.debug("Connection rejected from blacklisted " + connectionAddress);
						connectionSocket.close();
						continue;
					}

					// Create peer
					LOGGER.debug("Connection accepted from " + connectionAddress);
					new Peer(callback, connectionSocket);
				}
			} catch (SocketException e) {
				if (this.running) {
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
		this.running = false;

		if (this.socket != null && !this.socket.isClosed()) {
			try {
				this.socket.close();
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
	}
}
