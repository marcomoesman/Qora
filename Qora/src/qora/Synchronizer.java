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

import database.DBSet;

public class Synchronizer {
	private static final Logger LOGGER = LogManager.getLogger(Synchronizer.class);
	private boolean run = true;

	public Synchronizer() {
		this.run = true;
	}

	/**
	 * Find last common block with peer
	 * 
	 * @param {Peer}
	 *            peer
	 * @return {Block} Last common block with peer.
	 * @throws {Exception}
	 *             Thrown if peer fails to respond OR has no common block.
	 * 
	 * @see Synchronizer#getBlockSignatures(byte[], Peer)
	 */
	private Block findLastCommonBlock(Peer peer) throws Exception {
		// Start with our last known block / blockchain tip.
		Block block = DBSet.getInstance().getBlockMap().getLastBlock();

		// Request headers until common block is found or all blocks have been checked.
		// TODO: might be good to check block heights match too?

		// Request a chunk of headers
		List<byte[]> headers = this.getBlockSignatures(block.getSignature(), peer);

		// We didn't even manage to get any response from peer!
		if (headers == null)
			return null;

		// NB: empty headers means peer is unaware of the block signature we sent
		while (headers.size() == 0 && block.getHeight() > 1) {
			// Go back a chunk of blocks, or until we hit genesis block
			for (int i = 0; i < BlockChain.MAX_SIGNATURES && block.getHeight() > 1; ++i)
				block = block.getParent();

			// Request chunk of headers from this further back block
			headers = this.getBlockSignatures(block.getSignature(), peer);
		}

		// If headers is still empty at this point then peer has NO common block, not even genesis block
		if (headers.size() == 0)
			throw new Exception("Peer has no common block - not even genesis block");

		// Work back from peer's signature list to find common block
		for (int i = headers.size() - 1; i >= 0; --i) {
			// If we have this block too then it's the common block so return it
			Block foundBlock = DBSet.getInstance().getBlockMap().get(headers.get(i));

			if (foundBlock != null)
				return foundBlock;
		}

		// We didn't find any common blocks from peer's signature list but at least the block we requested is common, so return that.
		return block;
	}

	/**
	 * Request block from peer using block signature.
	 * 
	 * @param {byte[]}
	 *            signature
	 * @param {Peer}
	 *            peer
	 * @return {Block} Block from peer.
	 * @throws {Exception}
	 *             Thrown if peer doesn't respond OR send invalid block (based on received signature).
	 */
	private Block getBlock(byte[] signature, Peer peer) throws Exception {
		// Create message
		Message message = MessageFactory.getInstance().createGetBlockMessage(signature);

		// Send to peer and await response
		BlockMessage response = (BlockMessage) peer.getResponse(message);

		if (response == null)
			throw new Exception("Peer didn't respond with block");

		// Deserialize block prior to checking and returning
		Block block = response.getBlock();

		// Check block signature
		if (!block.isSignatureValid())
			throw new Exception("Invalid block");

		// Block has valid signature - return it
		return block;
	}

	/**
	 * Given list of block signatures, request corresponding blocks from peer
	 * 
	 * @param {List<byte[]>}
	 *            signatures
	 * @param {Peer}
	 *            peer
	 * @return {List<Block>} Blocks from peer.
	 * @throws {Exception}
	 *             Thrown if peer doesn't respond.
	 */
	private List<Block> getBlocks(List<byte[]> signatures, Peer peer) throws Exception {
		List<Block> blocks = new ArrayList<Block>();

		for (byte[] signature : signatures) {
			// Request block and add to list
			blocks.add(this.getBlock(signature, peer));
		}

		return blocks;
	}

	/**
	 * Request at least <code>minimumAmount</code> block signatures from peer starting after <code>start</code>.
	 * <p>
	 * Can return fewer than <code>minimumAmount</code> signatures if peer doesn't have enough. Can return more then <code>minimumAmount<code> signatures as
	 * they are requested in chunks.
	 * 
	 * @param {Block}
	 *            start
	 * @param {int}
	 *            minimumAmount
	 * @param {Peer}
	 *            peer
	 * @return {List<byte[]>} List of block signatures from peer.
	 * @throws {Exception}
	 *             Thrown if peer fails to respond.
	 * 
	 * @see Synchronizer#getBlockSignatures(byte[], Peer)
	 */
	private List<byte[]> getBlockSignatures(Block start, int minimumAmount, Peer peer) throws Exception {
		// NB: "headers" refers to block signatures

		// Request chunk of next block signatures after "start" block from peer
		List<byte[]> headers = this.getBlockSignatures(start.getSignature(), peer);

		// We didn't even manage to get any response from peer!
		if (headers == null)
			return null;

		// No new block signatures? Give up now
		if (headers.size() == 0)
			return headers;

		// Do we need to request more to satisfy "minimumAmount"?
		while (headers.size() < minimumAmount) {
			// Use last received signature to request more
			byte[] lastSignature = headers.get(headers.size() - 1);
			List<byte[]> nextHeaders = this.getBlockSignatures(lastSignature, peer);

			// There aren't any more - return what we have
			if (nextHeaders.size() == 0)
				break;

			headers.addAll(nextHeaders);
		}

		return headers;
	}

	/**
	 * Request a chunk of block signatures from peer starting after <code>header</code>.
	 * 
	 * @param {byte[]}
	 *            header
	 * @param {Peer}
	 *            peer
	 * @return {List<byte[]>} List of block signatures from peer.
	 * @throws {Exception}
	 *             Thrown if peer fails to respond.
	 * 
	 * @see Synchronizer#getBlockSignatures(Block, int, Peer)
	 */
	private List<byte[]> getBlockSignatures(byte[] header, Peer peer) throws Exception {
		// NB: "headers" refers to block signatures

		// Create message
		Message message = MessageFactory.getInstance().createGetHeadersMessage(header);

		// Send message to peer and await response
		SignaturesMessage response = (SignaturesMessage) peer.getResponse(message);

		if (response == null) {
			LOGGER.info("Peer didn't respond with block signatures");
			return null;
		}

		return response.getSignatures();
	}

	/**
	 * Repeatedly orphan blocks back to at least <code>lastCommonBlock</code>.
	 * <p>
	 * In some cases this method can orphan further back than <code>lastCommonBlock</code> to the next AT state storage block.
	 * 
	 * @param {DBSet}
	 *            dbOrFork
	 * @param {Block}
	 *            lastCommonBlock
	 * @param {List<Block>}
	 *            [orphanedBlocks] - List for storing orphaned Blocks.
	 * @param {List<Transaction>}
	 *            [orphanedTransactions] - List for storing orphaned Transactions.
	 * 
	 * @see Synchronizer#synchronize(DBSet, Block, List)
	 */
	private void orphanBackToCommonBlock(DBSet dbOrFork, Block lastCommonBlock, List<Block> orphanedBlocks, List<Transaction> orphanedTransactions) {
		assert dbOrFork != null : "dbOrFork can't be null";

		// If lastCommonBlock is null then there's nothing to do
		if (lastCommonBlock == null)
			return;

		// Get AT states to rollback
		Map<String, byte[]> states = dbOrFork.getATStateMap().getStates(lastCommonBlock.getHeight());

		// Nearest AT state storage block below last common block
		int height = (int) (Math.floor(lastCommonBlock.getHeight() / AT_Constants.STATE_STORE_DISTANCE)) * AT_Constants.STATE_STORE_DISTANCE;

		// Start with last known block
		Block lastBlock = dbOrFork.getBlockMap().getLastBlock();

		// Repeatedly orphan last block until we reach common block
		while (!Arrays.equals(lastBlock.getSignature(), lastCommonBlock.getSignature())) {
			// Optionally save orphaned transactions if caller has provided storage
			if (orphanedTransactions != null)
				orphanedTransactions.addAll(lastBlock.getTransactions());

			lastBlock.orphan(dbOrFork);
			lastBlock = dbOrFork.getBlockMap().getLastBlock();
		}

		// XXX: Why 11?
		while (lastBlock.getHeight() >= height && lastBlock.getHeight() > 11) {
			// Optionally save orphaned transactions if caller has provided
			// storage
			if (orphanedTransactions != null)
				orphanedTransactions.addAll(lastBlock.getTransactions());

			// Optionally save orphaned block if caller has provided storage
			if (orphanedBlocks != null)
				orphanedBlocks.add(0, lastBlock); // prepend to preserve order of lowest-height-first

			lastBlock.orphan(dbOrFork);
			lastBlock = dbOrFork.getBlockMap().getLastBlock();
		}

		for (String id : states.keySet()) {
			byte[] address = Base58.decode(id); // 32 bytes encoded, 25 bytes
												// decoded
			address = Bytes.ensureCapacity(address, AT_Constants.AT_ID_SIZE, 0); // pad to correct length
			// XXX: getAT(byte[]) not only re-Base58-encodes address back to String but also checks parent DBMap, unlike getAT(String)
			AT at = dbOrFork.getATMap().getAT(address);

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
	 * @param {Block}
	 *            block
	 * @return {boolean} <code>true</code> - if block is valid and added to our blockchain;<br>
	 *         <code>false</code> - if block is invalid OR we're shutting down and not processing any more blocks.
	 * 
	 * @see Block#process()
	 * @see Controller#start()
	 */
	public synchronized boolean process(Block block) {
		// Are we shutting down?
		if (!this.run)
			return false;

		// Is block valid?
		if (!block.isValid())
			return false;

		// Block good to go

		// Set/unset "processing" flag so if client crashes during process we know to rollback and recover.
		// (See Controller.start() for this test).
		DBSet.getInstance().getBlockMap().setProcessing(true);
		block.process();
		DBSet.getInstance().getBlockMap().setProcessing(false);

		return true;
	}

	public void stop() {
		this.run = false;
	}

	/**
	 * Synchronize our blockchain using <code>newBlocks</code> from peer that start from <code>lastCommonBlock</code>.
	 * <p>
	 * Orphans our blocks back to (at least) <code>lastCommonBlock</code>, validates <code>newBlocks</code> then applies them (if valid).
	 * 
	 * @param {DBSet}
	 *            db
	 * @param {Block}
	 *            lastCommonBlock
	 * @param {List<Block>}
	 *            newBlocks
	 * @return {List<Transaction>} Transactions orphaned during synchronization.
	 * @throws {Exception}
	 *             <code>newBlocks</code> from peer must validate.
	 */
	public List<Transaction> synchronize(DBSet db, Block lastCommonBlock, List<Block> newBlocks) throws Exception {
		List<Transaction> orphanedTransactions = new ArrayList<Transaction>();

		// Test-verify new blocks, starting from common block, before applying new blocks.

		DBSet fork = db.fork();
		// Switch AT platform to fork
		AT_API_Platform_Impl.getInstance().setDBSet(fork);

		// Use forked DB to orphan blocks back to common block.
		// Note that a few extra blocks past common block might be orphaned due to how ATs work.
		// We keep these extras for validation/reapplying. They are prepended to newBlocks.
		this.orphanBackToCommonBlock(fork, lastCommonBlock, newBlocks, null);

		// Validate new blocks
		for (Block block : newBlocks) {
			// Early bail-out if shutting down
			if (!this.run)
				return orphanedTransactions;

			// Check block is valid
			if (!block.isValid(fork)) {
				// Switch AT platform back to main DB
				AT_API_Platform_Impl.getInstance().setDBSet(db);

				// Invalid block - throw exception
				throw new Exception("Couldn't validate blocks sent from peer");
			}

			// Process and continue
			block.process(fork);
		}

		// Switch AT platform back from fork to main DB
		AT_API_Platform_Impl.getInstance().setDBSet(db);

		// New blocks are all valid so apply them to main DB.
		// First we need to orphan blocks back to common block.
		// We keep a list of orphaned transactions to return to caller.
		this.orphanBackToCommonBlock(db, lastCommonBlock, null, orphanedTransactions);

		// Apply new blocks
		for (Block block : newBlocks) {
			// Early bail-out if shutting down
			if (!this.run)
				return orphanedTransactions;

			this.process(block); // Synchronized
		}

		// Some of these transactions might have been reapplied above.
		return orphanedTransactions;
	}

	/**
	 * Use peer as a source of blocks to update our own blockchain
	 * <p>
	 * Determines last block common to us and peer (<code>lastCommonBlock</code>). If last common block is our blockchain tip then simply applies new blocks as
	 * they arrive. Otherwise, requests a chunk of blocks from peer, rolls-back to common block, applies new blocks then notifies peer of any transactions since
	 * last common block (in case we had some peer didn't know about).
	 * 
	 * @param {Peer}
	 *            peer
	 * @throws {Exception}
	 *             Throws if peer sends invalid block.
	 * 
	 * @see Synchronizer#findLastCommonBlock(Peer)
	 * @see Synchronizer#getBlockSignatures(Block, int, Peer)
	 * @see BlockBuffer
	 */
	public void synchronize(Peer peer) throws Exception {
		LOGGER.info("Synchronizing: " + peer.getAddress().getHostAddress() + " - " + peer.getPing());

		// Find last common block with peer
		Block lastCommonBlock = this.findLastCommonBlock(peer);

		// Didn't get any response from peer
		if (lastCommonBlock == null)
			return;

		Block lastBlock = DBSet.getInstance().getBlockMap().getLastBlock();

		// If last common block is our blockchain tip then we can forego any orphaning and simply process new blocks
		if (Arrays.equals(lastCommonBlock.getSignature(), lastBlock.getSignature())) {
			// Request next chunk of block signatures from peer
			List<byte[]> signatures = this.getBlockSignatures(lastCommonBlock, BlockChain.MAX_SIGNATURES, peer);

			// We didn't get any response for signatures since lastCommonBlock
			if (signatures == null)
				return;

			// Create block buffer to request blocks from peer
			BlockBuffer blockBuffer = new BlockBuffer(signatures, peer);

			// Process block-by-block as they arrive into block buffer
			for (byte[] signature : signatures) {
				// Wait for block to arrive from peer into block buffer
				Block block;

				try {
					block = blockBuffer.getBlock(signature);
				} catch (Exception e) {
					// We failed to receive a block or a received block had an invalid signature
					LOGGER.info("Peer didn't send block or sent a block with invalid signature");
					break;
				}

				if (block == null) {
					LOGGER.info("Timed out receiving block from peer");
					break;
				}

				// Process block from peer
				if (!this.process(block)) {
					// Didn't process because we're shutting down?
					if (!this.run)
						break;

					// Peer sent us an invalid block
					throw new Exception("Peer sent invalid block");
				}
			}

			// Block buffer no longer needed: we've finished processing or we're shutting down
			blockBuffer.stopThread();
		} else {
			// Request signatures from peer covering from last common block height to our blockchain tip height
			final int amount = lastBlock.getHeight() - lastCommonBlock.getHeight();
			List<byte[]> signatures = this.getBlockSignatures(lastCommonBlock, amount, peer);

			// Request all the blocks using received signatures.
			List<Block> blocks = this.getBlocks(signatures, peer);

			// Synchronize our blockchain using received blocks starting from lastCommonBlock
			List<Transaction> orphanedTransactions = this.synchronize(DBSet.getInstance(), lastCommonBlock, blocks);

			// Notify peer of any orphaned transactions in case we had some they don't know about
			for (Transaction transaction : orphanedTransactions) {
				TransactionMessage transactionMessage = new TransactionMessage(transaction);
				peer.sendMessage(transactionMessage);
			}
		}
	}
}
