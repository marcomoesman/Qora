package test;

import static org.junit.Assert.*;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ntp.NTP;

import database.DBSet;
import qora.BlockGenerator;
import qora.Synchronizer;
import qora.account.PrivateKeyAccount;
import qora.block.Block;
import qora.block.GenesisBlock;
import qora.crypto.Crypto;
import qora.transaction.GenesisTransaction;
import qora.transaction.Transaction;

public class SynchronizerTests {

	@Test
	public void synchronizeNoCommonBlock() {
		// Create in-memory DB
		DBSet databaseSet = DBSet.createEmptyDatabaseSet();

		// Process genesis block and add to blockchain
		GenesisBlock genesisBlock = new GenesisBlock();
		genesisBlock.process(databaseSet);

		// GENERATE FIRST 5 BLOCKS USING ACCOUNT A
		
		// Create test accountA
		PrivateKeyAccount accountA = TestUtils.createTestAccount();

		// Process genesis transaction to make sure accountA has funds
		Transaction transaction = new GenesisTransaction(accountA, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);

		// Generate first 5 blocks
		BlockGenerator blockGenerator = new BlockGenerator();
		Block lastBlock = genesisBlock;
		List<Block> firstBlocks = new ArrayList<Block>();
		for (int i = 0; i < 5; ++i) {
			// Generate next block
			Block newBlock = blockGenerator.generateNextBlock(databaseSet, accountA, lastBlock);

			// Add transactions signature
			byte[] transactionsSignature = Crypto.getInstance().sign(accountA, newBlock.getGeneratorSignature());
			newBlock.setTransactionsSignature(transactionsSignature);

			// Check block is still valid
			assertTrue("block should have valid signatures", newBlock.isSignatureValid());
			assertTrue("block should be valid", newBlock.isValid(databaseSet));
			
			// Process new block and add to blockchain
			newBlock.process(databaseSet);

			// Add to list of first blocks
			firstBlocks.add(newBlock);

			// Last block is the new block
			lastBlock = newBlock;
		}
		
		// GENERATE 5 MORE BLOCKS USING ACCOUNT B (ON FORK)

		// Create test accountB
		PrivateKeyAccount accountB = TestUtils.createTestAccount();

		// Process genesis transaction to make sure accountB has funds
		transaction = new GenesisTransaction(accountB, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);

		// Fork database
		DBSet fork = databaseSet.fork();

		// Generate next 5 blocks (on fork)
		List<Block> newBlocks = new ArrayList<Block>();
		for (int i = 0; i < 5; ++i) {
			// Generate next block
			Block newBlock = blockGenerator.generateNextBlock(fork, accountB, lastBlock);

			// Add transactions signature
			byte[] transactionsSignature = Crypto.getInstance().sign(accountB, newBlock.getGeneratorSignature());
			newBlock.setTransactionsSignature(transactionsSignature);

			// Check block is still valid
			assertTrue("block should have valid signatures", newBlock.isSignatureValid());
			assertTrue("block should be valid", newBlock.isValid(fork));

			// Process next block (on fork) and add to fork's blockchain
			newBlock.process(fork);

			// Add to list of new blocks
			newBlocks.add(newBlock);

			// Last block is the new block
			lastBlock = newBlock;
		}

		// ATTEMPT TO SYNCHRONIZE DB WITH BLOCKS FROM FORK MADE USING ACCOUNTB
		
		// Create synchronizer
		Synchronizer synchronizer = new Synchronizer();

		try {
			// NB: Just to be explicit about no common block
			final Block lastCommonBlock = null;
			
			// first newBlock's block's reference should be last firstBlock's block's signature
			assertTrue("first newBlock's reference should be last firstBlock's block signature", Arrays.equals(newBlocks.get(0).getReference(), firstBlocks.get(4).getSignature()));
			
			synchronizer.synchronize(databaseSet, lastCommonBlock, newBlocks);

			// Check last 5 blocks are the ones from newBlocks simply appended to blockchain
			lastBlock = databaseSet.getBlockMap().getLastBlock();
			for (int i = 4; i >= 0; --i) {
				// Check last block is the correct block from newBlocks
				assertTrue("lastBlock's signature should be newBlocks[" + i + "]'s signature", Arrays.equals(newBlocks.get(i).getSignature(), lastBlock.getSignature()));
				lastBlock = lastBlock.getParent(databaseSet);
			}

			// Check next 5 blocks are from firstBlocks - the first blocks in blockchain
			for (int i = 4; i >= 0; i--) {
				// Check last block is the correct block from firstBlocks
				assertTrue("lastBlock's signature should be firstBlocks[" + i + "]'s signature", Arrays.equals(firstBlocks.get(i).getSignature(), lastBlock.getSignature()));
				lastBlock = lastBlock.getParent(databaseSet);
			}

			// Check last remaining block is genesis block
			assertTrue("last remaining block's signature should be genesis block's signature", Arrays.equals(lastBlock.getSignature(), genesisBlock.getSignature()));

			// Check blockchain height
			assertEquals("last block's height incorrect", 11, databaseSet.getBlockMap().getLastBlock().getHeight(databaseSet));
		} catch (Exception e) {
			fail("Exception during synchronize");
		}
	}

	@Test
	public void synchronizeCommonBlock() {
		// Create in-memory DB
		DBSet databaseSet1 = DBSet.createEmptyDatabaseSet();
		DBSet databaseSet2 = databaseSet1.fork();

		// Process genesis block on both chains
		GenesisBlock genesisBlock = new GenesisBlock();
		genesisBlock.process(databaseSet1);
		genesisBlock.process(databaseSet2);

		// Create test accountA
		PrivateKeyAccount accountA = TestUtils.createTestAccount();

		// Process genesis transaction to make sure accountA has funds
		Transaction transaction = new GenesisTransaction(accountA, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet1);
		transaction.process(databaseSet2);

		// Create test accountB
		PrivateKeyAccount accountB = TestUtils.createTestAccount();

		// Process genesis transaction to make sure accountB has funds
		transaction = new GenesisTransaction(accountB, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet1);
		transaction.process(databaseSet2);

		// GENERATE FIRST 5 BLOCKS ON CHAIN 1 USING ACCOUNT A
		
		BlockGenerator blockGenerator = new BlockGenerator();
		Block lastBlock = genesisBlock;
		List<Block> firstBlocks = new ArrayList<Block>();
		for (int i = 0; i < 5; ++i) {
			// Generate next block
			Block newBlock = blockGenerator.generateNextBlock(databaseSet1, accountA, lastBlock);

			// Add transactions signature
			byte[] transactionsSignature = Crypto.getInstance().sign(accountA, newBlock.getGeneratorSignature());
			newBlock.setTransactionsSignature(transactionsSignature);

			// Check block is still valid
			assertTrue("block should have valid signatures", newBlock.isSignatureValid());
			assertTrue("block should be valid", newBlock.isValid(databaseSet1));
			
			// Process new block and add to blockchain1
			newBlock.process(databaseSet1);

			// Add to list of first blocks
			firstBlocks.add(newBlock);

			// Last block is the new block
			lastBlock = newBlock;
		}

		// GENERATE FIRST 10 BLOCKS ON CHAIN 2 USING ACCOUNT B
		
		lastBlock = genesisBlock;
		List<Block> newBlocks = new ArrayList<Block>();
		for (int i = 0; i < 10; ++i) {
			// Generate next block
			Block newBlock = blockGenerator.generateNextBlock(databaseSet2, accountB, lastBlock);

			// Add transactions signature
			byte[] transactionsSignature = Crypto.getInstance().sign(accountB, newBlock.getGeneratorSignature());
			newBlock.setTransactionsSignature(transactionsSignature);

			// Check block is still valid
			assertTrue("block should have valid signatures", newBlock.isSignatureValid());
			assertTrue("block should be valid", newBlock.isValid(databaseSet2));

			// Process next block and add to blockchain2
			newBlock.process(databaseSet2);

			// Add to list of new blocks
			newBlocks.add(newBlock);

			// Last block is the new block
			lastBlock = newBlock;
		}
		
		// ATTEMPT TO SYNCHRONIZE DB WITH BLOCKS FROM CHAIN 2 (as it's longer)

		Synchronizer synchronizer = new Synchronizer();

		try {
			// NB: Just to be explicit about last common block being genesis block
			final Block lastCommonBlock = genesisBlock;

			synchronizer.synchronize(databaseSet1, lastCommonBlock, newBlocks);

			// Check last 10 blocks on chain 1 are from chain 2 attached after genesis block 
			lastBlock = databaseSet1.getBlockMap().getLastBlock();
			for (int i = 9; i >= 0; --i) {
				// Check last block is the correct block from newBlocks
				assertTrue("lastBlock's signature should be newBlocks[" + i + "]'s signature", Arrays.equals(newBlocks.get(i).getSignature(), lastBlock.getSignature()));

				lastBlock = lastBlock.getParent(databaseSet1);
			}

			// Check last remaining block is genesis block
			assertTrue("last remaining block's signature should be genesis block's signature", Arrays.equals(lastBlock.getSignature(), genesisBlock.getSignature()));

			// Check blockchain height
			assertEquals("last block's height incorrect", 11, databaseSet1.getBlockMap().getLastBlock().getHeight(databaseSet1));
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception during synchronize");
		}
	}
}
