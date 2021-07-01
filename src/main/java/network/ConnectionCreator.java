package network;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import lang.Lang;
import network.message.Message;
import network.message.MessageFactory;
import network.message.PeersMessage;
import ntp.NTP;
import settings.Settings;

public final class ConnectionCreator extends Thread {

	private static final Logger LOGGER = LogManager.getLogger(ConnectionCreator.class);
	
	private final ConnectionCallback callback;
	private final ExecutorService executor;
	
	private boolean running;

	public ConnectionCreator(final ConnectionCallback callback) {
		super("Connection Creator");
		this.callback = callback;
		this.executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 4));
	}

	@Override
	public void run() {
		this.running = true;
		while (this.running) {
			try {
				final int maxReceivePeers = Settings.getInstance().getMaxReceivePeers();

				// Check if we need new peers
				if (this.running
						&& Settings.getInstance().getMinConnections() >= this.callback.getActiveConnections().size()) {
					// Get list of known peers
					final List<Peer> knownPeers = PeerManager.getInstance().getKnownPeers();

					int knownPeersCounter = 0;

					// Iterate known peers
					for (final Peer peer : knownPeers) {
						knownPeersCounter++;

						// Check if we are still running
						if (!this.running) {
							break;
						}
						
						// Check if we have reached the connection limit
						if (this.callback.getActiveConnections().size() >= Settings.getInstance()
								.getMaxConnections()) {
							break;
						}

						// Check if peer is not blacklisted
						if (PeerManager.getInstance().isBlacklisted(peer)) {
							continue;
						}
						
						// Check peer's address is not loopback, localhost, one of ours, etc.
						if (Network.isHostLocalAddress(peer.getAddress())) {
							continue;
						}
						
						// Check if we are already connected to peer
						if (this.callback.isConnectedTo(peer.getAddress())) {
							continue;
						}

						// Attempt to connect
						LOGGER.info(Lang.getInstance().translate(
								"Connecting to known peer %peer% (%knownPeersCounter% / %allKnownPeers%) (Connections: %activeConnections%)")
								.replace("%peer%", peer.getAddress().getHostAddress())
								.replace("%knownPeersCounter%", String.valueOf(knownPeersCounter))
								.replace("%allKnownPeers%", String.valueOf(knownPeers.size())).replace(
										"%activeConnections%", String.valueOf(callback.getActiveConnections().size())));
						
						// Attempt async connection
						this.executor.execute(() -> {
							peer.connect(callback);
						});
					}
				}

				// Check if we are still running
				if (!this.running) {
					break;
				}
				
				// Check if we still need new peers
				if (Settings.getInstance().getMinConnections() >= callback.getActiveConnections().size()) {
					// Grab peers connected for at least 15 seconds, then sort by reliability
					final List<Peer> sortedConnections = this.callback.getActiveConnections().parallelStream()
							.filter(peer -> (NTP.getTime() - peer.getConnectionTime()) >= 15_000L)
							.sorted(Comparator.comparingLong(Peer::getReliability).reversed())
							.collect(Collectors.toList());
					
					if (sortedConnections.size() == 0) {
						LOGGER.warn("None of the current peers are suitable for proposing");
					}
					
					// Iterate via for (int) loop to prevent exceptions
					for (int i = 0; i < sortedConnections.size(); i++) {
						final Peer peer = sortedConnections.get(i);

						// Check if we are still running
						if (!this.running) {
							break;
						}
						
						// Check if we have reached the connection limit
						if (this.callback.getActiveConnections().size() >= Settings.getInstance()
								.getMaxConnections()) {
							break;
						}

						// Ask connected peer for new peers
						final Message message = MessageFactory.getInstance().createGetPeersMessage();
						final PeersMessage response = (PeersMessage) peer.getResponse(message);
						if (response == null) {
							continue;
						}

						int foreignPeersCounter = 0;
						// Loop through received peers
						for (final Peer newPeer : response.getPeers()) {
							// Check if we are still running
							if (!this.running) {
								break;
							}
							
							// Check if we have reached the connection limit
							if (this.callback.getActiveConnections().size() >= Settings.getInstance()
									.getMaxConnections()) {
								break;
							}
							
							// We only process a maximum number of proposed peers
							if (foreignPeersCounter >= maxReceivePeers) {
								break;
							}

							foreignPeersCounter++;

							// Check if peer is not blacklisted
							if (PeerManager.getInstance().isBlacklisted(newPeer)) {
								continue;
							}
							
							// Check peer's address is not loopback, localhost, one of ours, etc.
							if (Network.isHostLocalAddress(newPeer.getAddress())) {
								continue;
							}

							// Check if we are already connected to peer
							if (callback.isConnectedTo(newPeer)) {
								continue;
							}

							// Don't connect to "bad" peers (unless settings say otherwise)
							if (!Settings.getInstance().isTryingConnectToBadPeers() && newPeer.isBad()) {
								continue;
							}

							final int maxReceivePeersForPrint = (maxReceivePeers > response.getPeers().size())
									? response.getPeers().size()
									: maxReceivePeers;

							LOGGER.info(Lang.getInstance().translate(
									"Connecting to peer %newpeer% proposed by %peer% (%foreignPeersCounter% / %maxReceivePeersForPrint% / %allReceivePeers%) (Connections: %activeConnections%)")
									.replace("%newpeer%", newPeer.getAddress().getHostAddress())
									.replace("%peer%", peer.getAddress().getHostAddress())
									.replace("%foreignPeersCounter%", String.valueOf(foreignPeersCounter))
									.replace("%maxReceivePeersForPrint%", String.valueOf(maxReceivePeersForPrint))
									.replace("%allReceivePeers%", String.valueOf(response.getPeers().size()))
									.replace("%activeConnections%",
											String.valueOf(callback.getActiveConnections().size())));
							
							// Attempt connection
							newPeer.connect(callback);
						}
					}
				}

				// Sleep for 60 seconds
				Thread.sleep(60 * 1000);
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
				LOGGER.info(Lang.getInstance().translate("Error creating new connection"));
			}
		}
	}

	public void halt() {
		this.running = false;
	}

}
