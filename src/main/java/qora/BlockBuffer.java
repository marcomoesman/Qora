package qora;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import qora.block.Block;
import settings.Settings;
import network.Peer;
import network.message.BlockMessage;
import network.message.Message;
import network.message.MessageFactory;

public final class BlockBuffer extends Thread {
	
	private static final int BUFFER_SIZE = 20;

	private final List<byte[]> signatures;
	private final Peer peer;
	private final Map<byte[], BlockingQueue<Block>> blocks;

	private int counter;
	private boolean error;
	private boolean running;

	public BlockBuffer(final List<byte[]> signatures, final Peer peer) {
		super("Block Buffer");
		this.signatures = signatures;
		this.peer = peer;
		this.blocks = new HashMap<byte[], BlockingQueue<Block>>();
		
		this.counter = 0;
		this.error = false;
		this.running = true;
		this.start();
	}

	public void run() {
		while (this.running) {
			for (int i = 0; i < this.signatures.size() && i < this.counter + BUFFER_SIZE; i++) {
				final byte[] signature = this.signatures.get(i);

				// Check if block already loaded
				if (!this.blocks.containsKey(signature)) {
					// Load block
					loadBlock(signature);
				}
			}

			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// Ignore
			}
		}
	}

	private void loadBlock(final byte[] signature) {
		// Create queue
		final BlockingQueue<Block> blockingQueue = new ArrayBlockingQueue<Block>(1);
		this.blocks.put(signature, blockingQueue);

		// Offload block loading to seperate thread
		new Thread("Block Loader") {
			@Override
			public void run() {
				// Create block message
				final Message message = MessageFactory.getInstance().createGetBlockMessage(signature);

				// Query response from peer
				final BlockMessage response = (BlockMessage) peer.getResponse(message);

				// Check if we have a response
				if (response == null) {
					error = true;
					return;
				}

				// Add to list
				blockingQueue.add(response.getBlock());
			}
		}.start();
	}

	public Block getBlock(byte[] signature) throws Exception {
		// Check for errors
		if (this.error) {
			throw new Exception("Block buffer error");
		}

		// Update counter
		this.counter = this.signatures.indexOf(signature);

		// Check if block already loaded
		if (!this.blocks.containsKey(signature)) {
			// Load block
			loadBlock(signature);
		}

		// Get block
		return this.blocks.get(signature).poll(Settings.getInstance().getConnectionTimeout(), TimeUnit.MILLISECONDS);
	}

	public void stopThread() {
		try {
			this.running = false;
			this.join();
		} catch (InterruptedException e) {
			// Ignore
		}
	}

}
