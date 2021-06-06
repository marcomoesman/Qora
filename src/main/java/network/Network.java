package network;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import controller.Controller;
import lang.Lang;
import network.message.FindMyselfMessage;
import network.message.Message;
import network.message.MessageFactory;
import settings.Settings;
import utils.ObserverMessage;

public final class Network extends Observable implements ConnectionCallback {

	private static final Logger LOGGER = LogManager.getLogger(Network.class);
	
	public static final int MAINNET_PORT = 9084;
	public static final int TESTNET_PORT = 4809;

	private static final int MAX_HANDLED_MESSAGES_SIZE = 10000;

	private ConnectionCreator creator;
	private ConnectionAcceptor acceptor;

	private final List<Peer> connectedPeers;
	private final SortedSet<String> handledMessages;

	private boolean running;

	public Network() {
		this.connectedPeers = new ArrayList<Peer>();
		this.handledMessages = Collections.synchronizedSortedSet(new TreeSet<String>());
		this.running = true;

		start();
	}

	private void start() {
		// Start connection creator
		this.creator = new ConnectionCreator(this);
		this.creator.start();

		// Start connection acceptor
		this.acceptor = new ConnectionAcceptor(this);
		this.acceptor.start();
	}

	@Override
	public void onConnect(final Peer peer) {
		LOGGER.debug(Lang.getInstance().translate("Connected to peer") + ": " + peer.getAddress());

		// Add to connected peers
		synchronized (this.connectedPeers) {
			this.connectedPeers.add(peer);
		}

		// Add to whitelist
		PeerManager.getInstance().addPeer(peer);

		// Pass to controller
		Controller.getInstance().onConnect(peer);

		// Notify observers
		setChanged();
		notifyObservers(new ObserverMessage(ObserverMessage.ADD_PEER_TYPE, peer));

		setChanged();
		notifyObservers(new ObserverMessage(ObserverMessage.LIST_PEER_TYPE, this.connectedPeers));
	}

	@Override
	public void onDisconnect(final Peer peer) {
		LOGGER.info(Lang.getInstance().translate("Peer") + " " + peer.getAddress() + " " + Lang.getInstance().translate("disconnected"));

		// Remove from connected peers
		synchronized (this.connectedPeers) {
			this.connectedPeers.remove(peer);
		}

		// Pass to controller
		Controller.getInstance().onDisconnect(peer);

		// Close connection
		peer.close();

		// Notify observers
		setChanged();
		notifyObservers(new ObserverMessage(ObserverMessage.REMOVE_PEER_TYPE, peer));

		setChanged();
		notifyObservers(new ObserverMessage(ObserverMessage.LIST_PEER_TYPE, this.connectedPeers));
	}

	@Override
	public void onError(final Peer peer, final String error) {
		LOGGER.warn(Lang.getInstance().translate("Connection error: ") + peer.getAddress() + " : " + error);

		// Remove from connected peers
		synchronized (this.connectedPeers) {
			this.connectedPeers.remove(peer);
		}

		// Pass to controller
		Controller.getInstance().onDisconnect(peer);

		// Close connection
		peer.close();

		// Notify observers
		setChanged();
		notifyObservers(new ObserverMessage(ObserverMessage.REMOVE_PEER_TYPE, peer));

		setChanged();
		notifyObservers(new ObserverMessage(ObserverMessage.LIST_PEER_TYPE, this.connectedPeers));
	}

	@Override
	public boolean isConnectedTo(final InetAddress address) {
		try {
			synchronized (this.connectedPeers) {
				// Loop through connected peer
				for (final Peer connectedPeer : this.connectedPeers) {
					// Compare address
					if (address.equals(connectedPeer.getAddress())) {
						return true;
					}
				}
			}
		} catch (Exception e) {
			// Ignore
		}
		return false;
	}

	@Override
	public boolean isConnectedTo(final Peer peer) {
		return isConnectedTo(peer.getAddress());
	}

	@Override
	public List<Peer> getActiveConnections() {
		return this.connectedPeers;
	}

	private void addHandledMessage(final byte[] hash) {
		try {
			synchronized (this.handledMessages) {
				// Check if list is full
				if (this.handledMessages.size() > MAX_HANDLED_MESSAGES_SIZE) {
					this.handledMessages.remove(this.handledMessages.first());
				}
				this.handledMessages.add(new String(hash));
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	@Override
	public void onMessage(final Message message) {
		// Check if we are still running
		if (!this.running) {
			return;
		}

		// Only handle block and transaction messages once
		if (message.getType() == Message.TRANSACTION_TYPE || message.getType() == Message.BLOCK_TYPE) {
			synchronized (this.handledMessages) {
				// Check if not handled already
				if (this.handledMessages.contains(new String(message.getHash()))) {
					return;
				}

				// Add to handles messages
				addHandledMessage(message.getHash());
			}
		}

		switch (message.getType()) {
		case Message.PING_TYPE:
			// Create ping response
			final Message response = MessageFactory.getInstance().createPingMessage();

			// Set Id
			response.setId(message.getId());

			// Return to sender
			message.getSender().sendMessage(response);
			break;

		// GETPEERS
		case Message.GET_PEERS_TYPE:
			// Create peer response
			final Message answer = MessageFactory.getInstance().createPeersMessage(PeerManager.getInstance().getBestPeers());
			
			// Set Id
			answer.setId(message.getId());

			// Return to sender
			message.getSender().sendMessage(answer);
			break;

		case Message.FIND_MYSELF_TYPE:
			final FindMyselfMessage findMyselfMessage = (FindMyselfMessage) message;
			if (Arrays.equals(findMyselfMessage.getFoundMyselfID(), Controller.getInstance().getFoundMyselfID())) {
				LOGGER.info(Lang.getInstance().translate("Connected to self. Disconnecting."));
				message.getSender().close();
			}
			break;
		// Send to controller
		default:
			Controller.getInstance().onMessage(message);
			break;
		}
	}

	public void broadcast(final Message message, final List<Peer> exclude) {
		LOGGER.trace(Lang.getInstance().translate("Broadcasting") + " message type " + message.getType());

		try {
			synchronized (this.connectedPeers) {
				for (final Peer peer : this.connectedPeers) {
					// Check exclusion list
					if (exclude.contains(peer))
						continue;

					peer.sendMessage(message);
				}
			}
		} catch (Exception e) {
			// Iterator fast-fail due to change in connectedPeers
		}

		LOGGER.trace(Lang.getInstance().translate("Broadcasting end") + " message type " + message.getType());
	}

	@Override
	public void addObserver(Observer o) {
		super.addObserver(o);

		// SEND CONNECTEDPEERS ON REGISTER
		o.update(this, new ObserverMessage(ObserverMessage.LIST_PEER_TYPE, this.connectedPeers));
	}

	public static boolean isPortAvailable(int port) {
		try {
			ServerSocket socket = new ServerSocket(port);
			socket.close();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public void stop() {
		this.running = false;

		this.acceptor.halt();
		this.creator.halt();

		while (this.connectedPeers.size() > 0) {
			try {
				this.connectedPeers.get(0).close();
			} catch (Exception e) {
				LOGGER.debug(e.getMessage(), e);
			}
		}
	}

	public static boolean isHostLocalAddress(InetAddress address) {
		// easy address checks first
		if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
			return true;
		}

		return Settings.getInstance().isLocalAddress(address);
	}

}
