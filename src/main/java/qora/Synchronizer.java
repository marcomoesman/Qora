package qora;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import network.Peer;
import network.message.BlockMessage;
import network.message.Message;
import network.message.MessageFactory;
import network.message.SignaturesMessage;
import network.message.TransactionMessage;
import qora.block.Block;
import qora.crypto.Base58;
import qora.transaction.Transaction;
import at.AT;
import at.AT_API_Platform_Impl;
import at.AT_Constants;

import com.google.common.primitives.Bytes;

import database.QoraDb;

public final class Synchronizer {
	
	private static final Logger LOGGER = LogManager.getLogger(Synchronizer.class);
	
	private boolean running;

	public Synchronizer() {
		this.running = true;
	}

	/**
	 * Find last common block with peer
	 * 
	 * @param {Peer} peer
	 * @return {Block} Last common block with peer.
	 * @throws {Exception} Thrown if peer fails to respond OR has no common block.
	 * 
	 * @see Synchronizer#getBlockSignatures(byte[], Peer)
	 */
	private Block findLastCommonBlock(final Peer peer) throws Exception {
		// Start with our last known block / blockchain tip.
		Block block = QoraDb.getInstance().getBlockMap().getLastBlock();

		// Request headers until common block is found or all blocks have been checked.
		// TODO: might be good to check block heights match too?

		// Request a chunk of headers
		List<byte[]> headers = this.getBlockSignatures(block.getSignature(), peer);

		// We didn't even manage to get any response from peer!
		if (headers == null) {
			throw new Exception("No block signatures from peer");
		}

		// NB: empty headers means peer is unaware of the block signature we sent
		while (headers.size() == 0 && block.getHeight() > 1) {
			// Go back a chunk of blocks, or until we hit genesis block
			for (int i = 0; i < Blockchain.MAX_SIGNATURES && block.getHeight() > 1; ++i) {
				block = block.getParent();
			}

			// Request chunk of headers from this further back block
			headers = this.getBlockSignatures(block.getSignature(), peer);
		}

		// If headers is still empty at this point then peer has NO common block, not
		// even genesis block
		if (headers.size() == 0) {
			throw new Exception("Peer has no common block - not even genesis block");
		}

		// Work back from peer's signature list to find common block
		for (int i = headers.size() - 1; i >= 0; --i) {
			// If we have this block too then it's the common block so return it
			final Block foundBlock = QoraDb.getInstance().getBlockMap().get(headers.get(i));
			if (foundBlock != null) {
				return foundBlock;
			}
		}

		// We didn't find any common blocks from peer's signature list but at least the
		// block we requested is common, so return that.
		return block;
	}

	/**
	 * Request block from peer using block signature.
	 * 
	 * @param {byte[]} signature
	 * @param {Peer}   peer
	 * @return {BlockMessage} Block and height from peer.
	 * @throws {Exception} Thrown if peer doesn't respond OR send invalid block
	 *                     (based on received signature).
	 */
	private BlockMessage getBlock(final byte[] signature, final Peer peer) throws Exception {
		// Create message
		final Message message = MessageFactory.getInstance().createGetBlockMessage(signature);

		// Send to peer and await response
		final BlockMessage response = (BlockMessage) peer.getResponse(message);

		if (response == null) {
			throw new Exception("Peer didn't respond with block");
		}

		// Deserialize block prior to checking and returning
		final Block block = response.getBlock();

		// Check block signature
		if (!block.isSignatureValid()) {
			throw new Exception("Invalid block signature");
		}
		
		// Check if block passes checkpoints
		if (!block.passesCheckpoints()) {
			throw new Exception("Block doesn't pass checkpoints");
		}

		// Block has valid signature - return it
		return response;
	}

	/**
	 * Given list of block signatures, request corresponding blocks from peer
	 * 
	 * @param {List<byte[]>} signatures
	 * @param {Peer}         peer
	 * @return {List<BlockMessage>} Blocks and heights from peer.
	 * @throws {Exception} Thrown if peer doesn't respond.
	 */
	private List<BlockMessage> getBlocks(final List<byte[]> signatures, final Peer peer) throws Exception {
		final List<BlockMessage> blockMessages = new ArrayList<BlockMessage>();
		for (byte[] signature : signatures) {
			// Request block and add to list
			final BlockMessage blockMessage = this.getBlock(signature, peer);
			if (blockMessage == null) {
				break;
			}

			blockMessages.add(blockMessage);
		}
		return blockMessages;
	}

	/**
	 * Request at least <code>minimumAmount</code> block signatures from peer
	 * starting after <code>start</code>.
	 * <p>
	 * Can return fewer than <code>minimumAmount</code> signatures if peer doesn't
	 * have enough. Can return more then <code>minimumAmount<code> signatures as
	 * they are requested in chunks.
	 * 
	 * @param {Block} start
	 * @param {int}   minimumAmount
	 * @param {Peer}  peer
	 * @return {List<byte[]>} List of block signatures from peer.
	 * @throws {Exception} Thrown if peer fails to respond.
	 * 
	 * @see Synchronizer#getBlockSignatures(byte[], Peer)
	 */
	private List<byte[]> getBlockSignatures(final Block start, final int minimumAmount, final Peer peer) throws Exception {
		// NB: "headers" refers to block signatures
		LOGGER.trace("Requesting " + minimumAmount + " block signatures after height " + start.getHeight());

		// Request chunk of next block signatures after "start" block from peer
		final List<byte[]> headers = this.getBlockSignatures(start.getSignature(), peer);

		// We didn't even manage to get any response from peer!
		if (headers == null) {
			throw new Exception("No block signatures from peer");
		}

		// No new block signatures? Give up now
		if (headers.size() == 0) {
			return headers;
		}

		// Do we need to request more to satisfy "minimumAmount"?
		while (headers.size() < minimumAmount) {
			// Use last received signature to request more
			final byte[] lastSignature = headers.get(headers.size() - 1);
			final List<byte[]> nextHeaders = this.getBlockSignatures(lastSignature, peer);

			if (nextHeaders == null) {
				throw new Exception("No next block signatures from peer");
			}

			// There aren't any more - return what we have
			if (nextHeaders.size() == 0) {
				break;
			}
			headers.addAll(nextHeaders);
		}
		return headers;
	}

	/**
	 * Request a chunk of block signatures from peer starting after
	 * <code>header</code>.
	 * 
	 * @param {byte[]} header
	 * @param {Peer}   peer
	 * @return {List<byte[]>} List of block signatures from peer.
	 * @throws {Exception} Thrown if peer fails to respond.
	 * 
	 * @see Synchronizer#getBlockSignatures(Block, int, Peer)
	 */
	private List<byte[]> getBlockSignatures(final byte[] header, final Peer peer) throws Exception {
		// NB: "headers" refers to block signatures

		// Create message
		final Message message = MessageFactory.getInstance().createGetHeadersMessage(header);

		// Send message to peer and await response
		final SignaturesMessage response = (SignaturesMessage) peer.getResponse(message);

		if (response == null) {
			throw new Exception("Peer didn't respond with block signatures");
		}
		return response.getSignatures();
	}

	/**
	 * Repeatedly orphan blocks back to at least <code>lastCommonBlock</code>.
	 * <p>
	 * In some cases this method can orphan further back than
	 * <code>lastCommonBlock</code> to the next AT state storage block.
	 * 
	 * @param {DBSet}             dbOrFork
	 * @param {Block}             lastCommonBlock
	 * @param {List<Block>}       [orphanedBlocks] - List for storing orphaned
	 *                            Blocks.
	 * @param {List<Transaction>} [orphanedTransactions] - List for storing orphaned
	 *                            Transactions.
	 * 
	 * @see Synchronizer#synchronize(QoraDb, Block, List)
	 */
	private void orphanBackToCommonBlock(final QoraDb dbOrFork, final Block lastCommonBlock, final List<Block> orphanedBlocks,
			final List<Transaction> orphanedTransactions) {
		assert dbOrFork != null : "dbOrFork can't be null";

		// If lastCommonBlock is null then there's nothing to do
		if (lastCommonBlock == null) {
			return;
		}

		// Get AT states to rollback
		final Map<String, byte[]> states = dbOrFork.getATStateMap().getStates(lastCommonBlock.getHeight());

		// Nearest AT state storage block below last common block
		final int height = (int) (Math.floor(lastCommonBlock.getHeight() / AT_Constants.STATE_STORE_DISTANCE))
				* AT_Constants.STATE_STORE_DISTANCE;

		// Start with last known block
		Block lastBlock = dbOrFork.getBlockMap().getLastBlock();

		// Repeatedly orphan last block until we reach common block
		while (!Arrays.equals(lastBlock.getSignature(), lastCommonBlock.getSignature())) {
			// Optionally save orphaned transactions if caller has provided storage
			if (orphanedTransactions != null) {
				orphanedTransactions.addAll(lastBlock.getTransactions());
			}

			lastBlock.orphan(dbOrFork);
			lastBlock = dbOrFork.getBlockMap().getLastBlock();
		}

		while (lastBlock.getHeight() >= height && lastBlock.getHeight() > 1) {
			// Optionally save orphaned transactions if caller has provided
			// storage
			if (orphanedTransactions != null) {
				orphanedTransactions.addAll(lastBlock.getTransactions());
			}

			// Optionally save orphaned block if caller has provided storage
			if (orphanedBlocks != null) {
				orphanedBlocks.add(0, lastBlock); // prepend to preserve order of lowest-height-first
			}

			lastBlock.orphan(dbOrFork);
			lastBlock = dbOrFork.getBlockMap().getLastBlock();
		}

		for (final String id : states.keySet()) {
			byte[] address = Base58.decode(id); // 32 bytes encoded, 25 bytes
												// decoded
			address = Bytes.ensureCapacity(address, AT_Constants.AT_ID_SIZE, 0); // pad to correct length
			// XXX: getAT(byte[]) not only re-Base58-encodes address back to String but also
			// checks parent DBMap, unlike getAT(String)
			final AT at = dbOrFork.getATMap().getAT(address);
			at.setState(states.get(id));

			dbOrFork.getATMap().update(at, height);
		}

		dbOrFork.getATMap().deleteAllAfterHeight(height);
		dbOrFork.getATStateMap().deleteStatesAfter(height);
	}

	/**
	 * Process a block and add it to our blockchain if valid.
	 * <p>
	 * Synchronized so only one block is processed at a time.
	 * 
	 * @param {Block} block
	 * @return {boolean} <code>true</code> - if block is valid and added to our
	 *         blockchain;<br>
	 *         <code>false</code> - if block is invalid OR we're shutting down and
	 *         not processing any more blocks.
	 * 
	 * @see Block#process()
	 * @see Controller#start()
	 */
	public synchronized boolean process(final Block block) {
		// Are we shutting down?
		if (!this.running) {
			return false;
		}

		// Is block valid?
		if (!block.isValid()) {
			return false;
		}

		// Block good to go

		// Set/unset "processing" flag so if client crashes during process we know to
		// rollback and recover.
		// (See Controller.start() for this test).
		QoraDb.getInstance().getBlockMap().setProcessing(true);
		block.process();
		QoraDb.getInstance().getBlockMap().setProcessing(false);
		return true;
	}

	public void stop() {
		this.running = false;
	}

	/**
	 * Synchronize our blockchain using <code>newBlocks</code> from peer that start
	 * from <code>lastCommonBlock</code>.
	 * <p>
	 * Orphans our blocks back to (at least) <code>lastCommonBlock</code>, validates
	 * <code>newBlocks</code> then applies them (if valid).
	 * 
	 * @param {DBSet}       db
	 * @param {Block}       lastCommonBlock
	 * @param {List<Block>} newBlocks
	 * @return {List<Transaction>} Transactions orphaned during synchronization.
	 * @throws {Exception} <code>newBlocks</code> from peer must validate.
	 */
	public List<Transaction> synchronize(final QoraDb db, final Block lastCommonBlock, final List<BlockMessage> newBlockMessages)
			throws Exception {
		// Test-verify new blocks, starting from common block, before applying new
		// blocks.
		final QoraDb fork = db.fork();
		// Switch AT platform to fork
		AT_API_Platform_Impl.getInstance().setDBSet(fork);

		// Use forked DB to orphan blocks back to common block.
		// Note that a few extra blocks past common block might be orphaned due to how
		// ATs work.
		// We keep these extras for validation/reapplying.
		final List<Block> orphanedBlocks = new ArrayList<Block>();
		this.orphanBackToCommonBlock(fork, lastCommonBlock, orphanedBlocks, null);

		LOGGER.debug("Orphaned back to block " + fork.getBlockMap().getLastBlock().getHeight(fork));

		// Revalidate orphaned blocks
		for (final Block block : orphanedBlocks) {
			// Early bail-out if shutting down
			if (!this.running) {
				// Switch AT platform back to main DB
				AT_API_Platform_Impl.getInstance().setDBSet(db);
				return null;
			}

			// Check block is valid
			if (!block.isValid(fork)) {
				// Switch AT platform back to main DB
				AT_API_Platform_Impl.getInstance().setDBSet(db);

				// Invalid block - throw exception
				LOGGER.debug("Couldn't revalidate orphaned block "
						+ (fork.getBlockMap().getLastBlock().getHeight(fork) + 1));
				throw new Exception("Couldn't revalidate orphaned block during synchronization");
			}

			// Process and continue
			block.process(fork);
		}

		int expectedBlockHeight = lastCommonBlock.getHeight() + 1;

		// Validate new blocks
		for (final BlockMessage blockMessage : newBlockMessages) {
			// Early bail-out if shutting down
			if (!this.running) {
				// Switch AT platform back to main DB
				AT_API_Platform_Impl.getInstance().setDBSet(db);

				return null;
			}

			final Block newBlock = blockMessage.getBlock();

			// Check received block height matches our expectations
			if (blockMessage.getHeight() != expectedBlockHeight) {
				// Switch AT platform back to main DB
				AT_API_Platform_Impl.getInstance().setDBSet(db);

				throw new Exception("Peer sent out-of-order block " + blockMessage.getHeight() + ", we expected block "
						+ expectedBlockHeight);
			}

			// Check block is valid
			if (!newBlock.isValid(fork)) {
				// Switch AT platform back to main DB
				AT_API_Platform_Impl.getInstance().setDBSet(db);

				// Invalid block - throw exception
				LOGGER.debug(
						"Couldn't validate peer's block " + (fork.getBlockMap().getLastBlock().getHeight(fork) + 1));
				throw new Exception("Couldn't validate blocks sent from peer");
			}

			// Process and continue
			newBlock.process(fork);

			expectedBlockHeight++;
		}

		// Switch AT platform back from fork to main DB
		AT_API_Platform_Impl.getInstance().setDBSet(db);

		// New blocks are all valid so apply them to main DB.
		// First we need to orphan blocks back to common block.
		// We keep a list of orphaned transactions to return to caller.
		final List<Transaction> orphanedTransactions = new ArrayList<Transaction>();
		orphanedBlocks.clear();
		this.orphanBackToCommonBlock(db, lastCommonBlock, orphanedBlocks, orphanedTransactions);

		// Reapply orphaned blocks
		for (final Block block : orphanedBlocks) {
			// Early bail-out if shutting down
			if (!this.running) {
				return orphanedTransactions;
			}

			this.process(block); // Synchronized
		}

		// Apply new blocks
		for (final BlockMessage blockMessage : newBlockMessages) {
			// Early bail-out if shutting down
			if (!this.running) {
				return orphanedTransactions;
			}

			this.process(blockMessage.getBlock()); // Synchronized
		}

		// Some of these transactions might have been reapplied above.
		return orphanedTransactions;
	}

	/**
	 * Use peer as a source of blocks to update our own blockchain
	 * <p>
	 * Determines last block common to us and peer (<code>lastCommonBlock</code>).
	 * If last common block is our blockchain tip then simply applies new blocks as
	 * they arrive. Otherwise, requests a chunk of blocks from peer, rolls-back to
	 * common block, applies new blocks then notifies peer of any transactions since
	 * last common block (in case we had some peer didn't know about).
	 * 
	 * @param {Peer} peer
	 * @throws {Exception} Throws if peer sends invalid block.
	 * 
	 * @see Synchronizer#findLastCommonBlock(Peer)
	 * @see Synchronizer#getBlockSignatures(Block, int, Peer)
	 * @see BlockBuffer
	 */
	public void synchronize(final Peer peer) throws Exception {
		// Find last common block with peer
		final Block lastCommonBlock = findLastCommonBlock(peer);

		// Didn't get any response from peer
		if (lastCommonBlock == null) {
			return;
		}

		final Block lastBlock = QoraDb.getInstance().getBlockMap().getLastBlock();

		// If last common block is our blockchain tip then we can forego any orphaning
		// and simply process new blocks
		if (Arrays.equals(lastCommonBlock.getSignature(), lastBlock.getSignature())) {
			LOGGER.info("Synchronisation continuing from blockchain tip " + lastBlock.getHeight() + " using "
					+ peer.getAddress().getHostAddress());

			// Request next chunk of block signatures from peer
			final List<byte[]> signatures = this.getBlockSignatures(lastCommonBlock, Blockchain.MAX_SIGNATURES, peer);

			// We didn't get any response for signatures since lastCommonBlock
			if (signatures == null) {
				return;
			}

			if (signatures.size() == 0) {
				throw new Exception("Received no block signatures from peer");
			}

			// Create block buffer to request blocks from peer
			final BlockBuffer blockBuffer = new BlockBuffer(signatures, peer);

			int expectedBlockHeight = lastBlock.getHeight() + 1;

			// Process block-by-block as they arrive into block buffer
			for (final byte[] signature : signatures) {
				// Wait for block to arrive from peer into block buffer
				final Block block = blockBuffer.getBlock(signature);

				if (block == null) {
					throw new Exception("Timed out receiving block from peer");
				}

				// Check received block height matches our expectations
				// A height of -1 means it's a new block (which is okay)
				final int height = block.getHeight();
				if (height != -1 && height != expectedBlockHeight) {
					throw new Exception("Peer sent out-of-order block " + block.getHeight() + ", we expected block "
							+ expectedBlockHeight);
				}

				// Process block from peer
				if (!process(block)) {
					// Didn't process because we're shutting down?
					if (!this.running) {
						break;
					}

					// Peer sent us an invalid block
					LOGGER.debug("Couldn't validate peer's block " + block.getHeight());
					throw new Exception("Peer sent invalid block");
				}

				expectedBlockHeight++;
			}

			// Block buffer no longer needed: we've finished processing or we're shutting
			// down
			blockBuffer.stopThread();
		} else {
			LOGGER.info(
					"Synchronizing using peer " + peer.getAddress().getHostAddress() + " from last common block height "
							+ lastCommonBlock.getHeight() + ", our height was " + lastBlock.getHeight());

			// Request signatures from peer covering from last common block height to our
			// blockchain tip height
			final int amount = Math.min(lastBlock.getHeight() - lastCommonBlock.getHeight(), Blockchain.MAX_SIGNATURES);
			final List<byte[]> signatures = this.getBlockSignatures(lastCommonBlock, amount, peer);

			if (signatures == null) {
				return;
			}

			if (signatures.size() == 0) {
				throw new Exception("Received no block signatures from peer");
			}

			// Request all the blocks using received signatures.
			final List<BlockMessage> blockMessages = this.getBlocks(signatures, peer);

			// Synchronize our blockchain using received blocks starting from
			// lastCommonBlock
			final List<Transaction> orphanedTransactions = this.synchronize(QoraDb.getInstance(), lastCommonBlock,
					blockMessages);
			if (orphanedTransactions == null) {
				return;
			}

			// Notify peer of any orphaned transactions in case we had some they don't know
			// about
			for (final Transaction transaction : orphanedTransactions) {
				final TransactionMessage message = new TransactionMessage(transaction);
				peer.sendMessage(message);
			}
		}
	}
}
