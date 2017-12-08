package network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import network.message.Message;
import network.message.MessageFactory;
import settings.Settings;

/**
 * Pinger is a Thread that periodically pings a Peer to maintain/check
 * connectivity.
 */
public class Pinger extends Thread {

	private static final Logger LOGGER = LogManager.getLogger(Pinger.class);
	private Peer peer;
	/**
	 * Most recent ping round-trip time in milliseconds, or Long.MAX_VALUE if no
	 * ping yet.
	 */
	private long ping;

	/**
	 * Simple Pinger constructor
	 * <p>
	 * Will start Pinger thread.
	 * @param peer
	 * @see #run()
	 */
	public Pinger(Peer peer) {
		this.peer = peer;
		this.ping = Long.MAX_VALUE;

		this.start();
	}

	/**
	 * Get last ping's round-trip time.
	 * 
	 * @return ping's RTT in milliseconds or Long.MAX_VALUE if no ping yet.
	 */
	public long getPing() {
		return this.ping;
	}

	/**
	 * Repeatedly ping peer using interval from settings.
	 * <p>
	 * Will exit if interrupted, typically by <code>stopPing()</code>
	 * 
	 * @see #stopPing()
	 * @see Peer#onPingSuccess()
	 * @see Peer#onPingFailure()
	 */
	@Override
	public void run() {
		Thread.currentThread().setName("Pinger " + this.peer.getAddress());

		while (true) {
			// Send ping message to peer
			long start = System.currentTimeMillis();
			Message pingMessage = MessageFactory.getInstance().createPingMessage();
			// NB: Peer.getResponse returns null if no response within timeout
			// or interrupt occurs
			Message response = this.peer.getResponse(pingMessage);

			// Check for valid ping response
			if (response == null || response.getType() != Message.PING_TYPE) {
				// Notify Peer that ping has failed.
				// NB: currently Peer.onPingFailure() may call Pinger.stopPing()
				// (see below)
				LOGGER.debug("Ping failure with " + this.peer.getAddress());
				this.peer.onPingFailure();
				return;
			}

			// Calculate ping's round-trip time and notify peer
			this.ping = System.currentTimeMillis() - start;
			this.peer.onPingSuccess();

			// Sleep until we need to send next ping
			try {
				Thread.sleep(Settings.getInstance().getPingInterval());
			} catch (InterruptedException e) {
				// If interrupted, usually by stopPing(), we need to exit thread
				return;
			}
		}
	}

	/**
	 * Stop pinging peer.
	 * <p>
	 * Usually called by Peer.close()
	 * 
	 * @see Peer#close()
	 */
	public void stopPing() {
		if (this.isAlive()) {
			this.interrupt();

			try {
				this.join();
			} catch (InterruptedException e) {
				// We've probably reached here from run() above calling
				// Peer.onPingFailure() so when we return run() above will
				// terminate
			}
		}
	}
}
