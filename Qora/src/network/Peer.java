package network;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import lang.Lang;
import network.message.Message;
import network.message.MessageFactory;
import network.message.MessageException;
import ntp.NTP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import settings.Settings;
import controller.Controller;
import database.DBSet;

public class Peer extends Thread {

	private InetAddress address;
	private ConnectionCallback callback;
	private Socket socket;
	private OutputStream out;
	private Pinger pinger;
	private boolean white;
	private long pingCounter;
	private long connectionTime;

	private static final int INACTIVITY_TIMEOUT = 60 * 60 * 1000; // one hour
	private static final Logger LOGGER = LogManager.getLogger(Peer.class);
	private Map<Integer, BlockingQueue<Message>> messages;

	/**
	 * Construct simple, non-connected Peer
	 * 
	 * @param address
	 */
	public Peer(InetAddress address) {
		this.address = address;
	}

	/**
	 * Construct Peer based on existing connected <code>socket</code>
	 * <p>
	 * 
	 * @param callback
	 * @param socket
	 */
	public Peer(ConnectionCallback callback, Socket socket) {
		this.callback = callback;
		this.socket = socket;
		this.address = socket.getInetAddress();

		this.setup(false);
	}

	/**
	 * Set up initial peer values
	 * <p>
	 * Set up initial peer settings, e.g. socket timeout, ping thread & counter,
	 * etc.<br>
	 * Will close peer if setup fails. On success, will call
	 * <code>ConnectionCallback.onConnect</code>
	 * 
	 * @param white
	 * @see Pinger
	 * @see ConnectionCallback#onConnect(Peer)
	 */
	private void setup(boolean white) {
		try {
			this.messages = Collections.synchronizedMap(new HashMap<Integer, BlockingQueue<Message>>());
			this.white = white;
			this.pingCounter = 0;
			this.connectionTime = NTP.getTime();

			// Enable TCP keep-alive packets
			// this.socket.setKeepAlive(true);

			// Inactivity timeout - see run()
			this.socket.setSoTimeout(INACTIVITY_TIMEOUT);

			// Grab reference to output stream
			this.out = socket.getOutputStream();

			// Start main communication thread
			this.start();

			// Start Pinger (requires main communication thread)
			this.pinger = new Pinger(this);

			// Notify peer is connected
			this.callback.onConnect(this);
		} catch (Exception e) {
			// Connection setup failure NO NEED TO BLACKLIST
			LOGGER.info("Failed to connect to " + address + ": " + e.getMessage());
			LOGGER.debug(e.getMessage(), e);

			// peer no longer usable
			this.close();
		}
	}

	public InetAddress getAddress() {
		return address;
	}

	/*
	 * Ping-related
	 */

	/**
	 * Get number of times we've successfully pinged peer.
	 * 
	 * @return number of pings, 0+
	 */
	public long getPingCounter() {
		return this.pingCounter;
	}

	/**
	 * Callback, used by Pinger, on successful ping.
	 * <p>
	 * Updates ping counter and peer info in PeerMap database.
	 * 
	 * @see Pinger#run()
	 * @see PingMap
	 */
	public void onPingSuccess() {
		this.pingCounter++;

		if (!DBSet.getInstance().isStoped())
			DBSet.getInstance().getPeerMap().addPeer(this);
	}

	/**
	 * Callback, used by Pinger, on ping failure.
	 * <p>
	 * Disconnects peer using <code>ConnectionCallback.onDisconnect(Peer)</code>
	 * which is typically a <code>Network</code> object.
	 * 
	 * @see Pinger#run()
	 * @see ConnectionCallback#onDisconnect(Peer)
	 * @see Network#onDisconnect(Peer)
	 */
	public void onPingFailure() {
		// Disconnect
		this.callback.onDisconnect(this);
	}

	/**
	 * Get most recent ping round-trip time.
	 * 
	 * @return ping RTT time in milliseconds or Long.MAX_VALUE if no ping yet.
	 * @see Pinger#getPing()
	 */
	public long getPing() {
		return this.pinger.getPing();
	}

	/**
	 * Do we have a Pinger object for this peer?
	 * <p>
	 * NB: Pinger may not necessarily be running.
	 * 
	 * @return <code>true</code> if we have a Pinger object
	 * @see Pinger
	 */
	public boolean hasPinger() {
		return this.pinger != null;
	}

	/**
	 * Connect to <code>address</code> using timeout from settings.
	 * <p>
	 * On success, <code>ConnectionCallback.onConnect()</code> is called.<br>
	 * On failure, we simply return.
	 * 
	 * @param callback
	 * @see ConnectionCallback#onConnect(Peer)
	 */
	public void connect(ConnectionCallback callback) {
		// XXX we don't actually use DB so replace with cleaner "are we shutting
		// down?" test
		if (DBSet.getInstance().isStoped()) {
			return;
		}

		this.callback = callback;

		// Create new socket for connection to peer
		this.socket = new Socket();

		// Collate this.address and destination port from controller
		InetSocketAddress socketAddress = new InetSocketAddress(address, Controller.getInstance().getNetworkPort());

		// Attempt to connect, with timeout from settings
		try {
			this.socket.connect(socketAddress, Settings.getInstance().getConnectionTimeout());
		} catch (Exception e) {
			LOGGER.info(Lang.getInstance().translate("Failed to connect to ") + address + ": " + e.getMessage());
			return;
		}

		this.setup(true);
	}

	/**
	 * Main communication thread
	 * <p>
	 * Waits for incoming messages from peer, unless inactivity timeout reached.
	 * <p>
	 * If something is waiting for a message with a specific ID then they are
	 * notified so it can be processed. Otherwise the message is added to our
	 * queue, keyed by message ID.
	 * 
	 * @see #getResponse(Message)
	 * @see MessageFactory#parse(Peer, DataInputStream)
	 * @see ConnectionCallback#onMessage(Message)
	 * @see ConnectionCallback#onDisconnect(Message)
	 */
	public void run() {
		Thread.currentThread().setName("Peer " + this.address.toString());

		try {
			DataInputStream in = new DataInputStream(socket.getInputStream());

			while (true) {
				// Read only enough bytes to cover Message "magic" preamble
				byte[] messageMagic = new byte[Message.MAGIC_LENGTH];
				in.readFully(messageMagic);

				if (!Arrays.equals(messageMagic, Controller.getInstance().getMessageMagic())) {
					// Didn't receive valid Message "magic"
					this.callback.onError(this,
							Lang.getInstance().translate("received message with wrong magic") + " " + address);
					return;
				}

				// Attempt to parse incoming message - throws on failure
				Message message = MessageFactory.getInstance().parse(this, in);

				// LOGGER.debug("Received message (type " + message.getType() +
				// ") from " + this.address);

				// If there's a queue for this message ID then add message to
				// queue
				if (message.hasId() && this.messages.containsKey(message.getId())) {
					// Adding message to queue will unblock waiting caller (if
					// any)
					this.messages.get(message.getId()).add(message);
				} else {
					// Generic message callback
					this.callback.onMessage(message);
				}
			}
		} catch (InterruptedException e) {
			// peer connection being closed - simply exit
			return;
		} catch (SocketTimeoutException e) {
			LOGGER.info(Lang.getInstance().translate("Inactivity timeout with peer") + " " + address);

			// Disconnect peer
			this.callback.onDisconnect(this);
			return;
		} catch (SocketException e) {
			LOGGER.info(Lang.getInstance().translate("Socket issue with peer") + " " + address);

			// Disconnect peer
			this.callback.onDisconnect(this);
			return;
		} catch (MessageException e) {
			// Suspect peer
			this.callback.onError(this, e.getMessage());
			return;
		} catch (Exception e) {
			// not expected as above
			LOGGER.debug(e.getMessage(), e);

			// Disconnect peer
			this.callback.onDisconnect(this);
			return;
		}
	}

	/**
	 * Attempt to send Message to peer
	 * 
	 * @param message
	 * @return <code>true</code> if message successfully sent;
	 *         <code>false</code> otherwise
	 */
	public boolean sendMessage(Message message) {
		try {
			// CHECK IF SOCKET IS STILL ALIVE
			if (!this.socket.isConnected()) {
				// ERROR
				callback.onError(this, Lang.getInstance().translate("socket not still alive"));

				return false;
			}

			// SEND MESSAGE
			synchronized (this.out) {
				this.out.write(message.toBytes());
				this.out.flush();
			}

			// RETURN
			return true;
		} catch (Exception e) {
			LOGGER.debug(e.getMessage(), e);
			// ERROR
			callback.onError(this, e.getMessage());

			// RETURN
			return false;
		}
	}

	/**
	 * Send message to peer and await response.
	 * <p>
	 * Message is assigned a random ID and sent. If a response with matching ID
	 * is received then it is returned to caller.
	 * <p>
	 * If no response with matching ID within timeout, or some other
	 * error/exception occurs, then return <code>null</code>. (Assume peer will
	 * be rapidly disconnected after this).
	 * 
	 * @param message
	 * @return <code>Message</code> if valid response received;
	 *         <code>null</code> if not or error/exception occurs
	 */
	public Message getResponse(Message message) {
		// Assign random ID to this message
		int id = (int) ((Math.random() * 1000000) + 1);
		message.setId(id);

		// Put queue into map (keyed by message ID) so we can poll for a
		// response
		BlockingQueue<Message> blockingQueue = new ArrayBlockingQueue<Message>(1);
		this.messages.put(id, blockingQueue);

		// Try to send message
		if (!this.sendMessage(message)) {
			this.messages.remove(id);
			return null;
		}

		try {
			Message response = blockingQueue.poll(Settings.getInstance().getConnectionTimeout(), TimeUnit.MILLISECONDS);
			this.messages.remove(id);

			if (response == null && this.socket.isConnected())
				LOGGER.info("Timed out while waiting for response from peer " + address);

			return response;
		} catch (InterruptedException e) {
			// Our thread was interrupted. Probably in shutdown scenario.
			LOGGER.info("Interrupted while waiting for response from peer " + address);
			this.messages.remove(id);
			return null;
		}
	}

	public boolean isWhite() {
		return this.white;
	}

	public long getConnectionTime() {
		return this.connectionTime;
	}

	public boolean isBad() {
		return DBSet.getInstance().getPeerMap().isBad(this.getAddress());
	}

	/**
	 * Close connection to peer
	 * <p>
	 * Can be called during normal operation or also in case of error, shutdown,
	 * etc.
	 * 
	 * @see Pinger#stopPing()
	 */
	public void close() {
		// Stop Pinger if applicable
		if (this.pinger != null)
			this.pinger.stopPing();

		try {
			// maybe interrupt() run() thread to differentiate from peer closing
			// connection?
			/*
			 * if (this.isAlive()) { this.interrupt(); this.join(); }
			 */

			// Close socket if applicable
			if (socket != null && socket.isConnected())
				socket.close();
		} catch (IOException e) {
			LOGGER.debug("Error closing socket connection to peer " + address + ": " + e.getMessage(), e);
		}
	}
}
