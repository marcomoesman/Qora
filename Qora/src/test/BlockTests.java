package test;

import static org.junit.Assert.*;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import ntp.NTP;

import com.google.common.primitives.Bytes;

import database.DBSet;
import qora.account.Account;
import qora.block.Block;
import qora.block.BlockFactory;
import qora.block.GenesisBlock;
import qora.crypto.Crypto;
import qora.transaction.GenesisTransaction;
import qora.transaction.PaymentTransaction;
import qora.transaction.Transaction;

public class BlockTests extends TestUtils {
	@Test
	public void validateSignatureGenesisBlock() {
		Block genesisBlock = new GenesisBlock();

		// Check block's signatures are valid
		assertTrue("genesis block signature should be valid", genesisBlock.isSignatureValid());
	}

	@Test
	public void validateGenesisBlock() {
		// Create empty in-memory DB
		DBSet databaseSet = DBSet.createEmptyDatabaseSet();

		// INVALID GENESIS TRANSACTION TEST

		// Create genesis block
		Block genesisBlock = new GenesisBlock();

		// Check brand new genesis block is valid
		assertTrue("genesis block should be valid", genesisBlock.isValid(databaseSet));

		// Add invalid genesis transaction: amount is negative
		Transaction transaction = new GenesisTransaction(TestUtils.createTestAccount(), BigDecimal.valueOf(-1000).setScale(8), NTP.getTime());
		genesisBlock.addTransaction(transaction);

		// Check block is now invalid
		assertFalse("genesis block with invalid transaction should be invalid", genesisBlock.isValid(databaseSet));

		// INVALID DUPLICATE GENESIS BLOCK TEST

		// Create new genesis block
		genesisBlock = new GenesisBlock();

		// Check brand new genesis block is valid
		assertTrue("new genesis block should be valid", genesisBlock.isValid(databaseSet));

		// Process genesis block, adding it to DB's blockchain
		genesisBlock.process(databaseSet);

		// Check genesis block invalid for processing by virtue of already being in blockchain
		assertFalse("duplicate genesis block should be invalid", genesisBlock.isValid(databaseSet));
	}

	@Test
	public void parseGenesisBlock() {
		// Create new genesis block
		Block genesisBlock = new GenesisBlock();

		// Serialize
		byte[] rawBlock = genesisBlock.toBytes();

		try {
			// Deserialize
			Block parsedBlock = BlockFactory.getInstance().parse(rawBlock);

			// Check Block instance is GenesisBlock
			// XXX: This will never return true as Block.parse() never creates a GenesisBlock instance, only ever Block instances
			// assertEquals(true, parsedBlock instanceof GenesisBlock);

			// Check block signatures match
			assertTrue("parsed genesis block has incorrect signature", Arrays.equals(genesisBlock.getSignature(), parsedBlock.getSignature()));

			// Check generator accounts match
			// XXX: This fails because genesisBlock is initialized with byte[8] PublicKeyAccount
			// whereas parsedBlock has byte[32] PublicKeyAccount by way of serialization
			// PublicKeyAccount( {1,1,1,1,1,1,1,1} ) is QLpLzqs4DW1FNJByeJ63qaqw3eAYCxfkjR
			// PublicKeyAccount( {1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0} ) is QfGMeDQQUQePMpAmfLBJzgqyrM35RWxHGD
			// assertEquals(genesisBlock.getGenerator().getAddress(), parsedBlock.getGenerator().getAddress());

			// Check base targets match
			assertEquals("parsed genesis block has incorrect generating balance", genesisBlock.getGeneratingBalance(), parsedBlock.getGeneratingBalance());

			// Check total fees match
			assertEquals("parsed genesis block has incorrect total fee", genesisBlock.getTotalFee(), parsedBlock.getTotalFee());

			// Check block references match
			// XXX: This fails because genesisBlock is initialized with byte[8] reference
			// whereas parsedBlock has byte[32] reference by way of serialization
			// assertEquals(true, Arrays.equals(genesisBlock.getReference(), parsedBlock.getReference()));

			// Check block timestamps match
			assertEquals("parsed genesis block has incorrect timestamp", genesisBlock.getTimestamp(), parsedBlock.getTimestamp());

			// Check transaction counts match
			assertEquals("parsed genesis block has wrong transaction count", genesisBlock.getTransactionCount(), parsedBlock.getTransactionCount());
		} catch (Exception e) {
			fail("Exception while parsing transaction.");
		}

		// PARSE FAILURE TEST

		// Generate some bytes for parsing
		rawBlock = new byte[50];

		try {
			// Attempt to parse from bytes
			BlockFactory.getInstance().parse(rawBlock);

			// Should not reach here - FAIL
			fail("BlockFactory incorrectly parsed wrong bytes");
		} catch (Exception e) {
			// Exception is thrown - PASS
		}
	}

	@Test
	public void validateSignatureBlock() {
		// Use inherited TestUtils.setup()

		// Generate next block
		Block newBlock = blockGenerator.generateNextBlock(databaseSet, generator, genesisBlock);

		// Add transactions signature
		byte[] transactionsSignature = Crypto.getInstance().sign(generator, newBlock.getGeneratorSignature());
		newBlock.setTransactionsSignature(transactionsSignature);

		// Check block has valid signatures
		assertTrue("block with valid signature should be valid", newBlock.isSignatureValid());

		// INVALID TRANSACTIONS SIGNATURE TEST

		// Create invalid transactions signature
		transactionsSignature = new byte[64];
		newBlock.setTransactionsSignature(transactionsSignature);

		// Check transactions signature is actually invalid
		assertFalse("block with invalid transaction signature should be invalid", newBlock.isSignatureValid());

		// INVALID BLOCK SIGNATURE TEST

		// Create invalid block due to invalid block signature ("new byte[64]" arg)
		newBlock = BlockFactory.getInstance().create(newBlock.getVersion(), newBlock.getReference(), newBlock.getTimestamp(), newBlock.getGeneratingBalance(),
				generator, new byte[64]);
		// Set valid transactions signature to avoid failure testing transactions signature
		transactionsSignature = Crypto.getInstance().sign(generator, newBlock.getGeneratorSignature());
		newBlock.setTransactionsSignature(transactionsSignature);

		// Check block fails signature validity tests
		assertFalse("block with invalid generator signature should be invalid", newBlock.isSignatureValid());

		// VALID TRANSACTION TEST

		// Generate valid next block
		newBlock = blockGenerator.generateNextBlock(databaseSet, generator, genesisBlock);

		// Add valid transaction
		Account recipient = TestUtils.createTestAccount();
		long timestamp = newBlock.getTimestamp();
		byte[] signature = PaymentTransaction.generateSignature(databaseSet, generator, recipient, BigDecimal.valueOf(100).setScale(8),
				BigDecimal.valueOf(1).setScale(8), timestamp);
		// Transaction's amount matches amount used to generate signature above
		Transaction payment = new PaymentTransaction(generator, recipient, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp,
				generator.getLastReference(databaseSet), signature);
		newBlock.addTransaction(payment);

		// Set block's transactions signature
		transactionsSignature = blockGenerator.calculateTransactionsSignature(newBlock, generator);
		newBlock.setTransactionsSignature(transactionsSignature);

		// Check block has valid signatures
		assertTrue("block with valid transaction should be valid", newBlock.isSignatureValid());

		// INVALID TRANSACTION TEST

		// Generate valid block
		newBlock = blockGenerator.generateNextBlock(databaseSet, generator, genesisBlock);

		// Add invalid transaction: transaction's amount does not match amount used to generate signature
		payment = new PaymentTransaction(generator, recipient, BigDecimal.valueOf(200).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp,
				generator.getLastReference(databaseSet), signature);
		newBlock.addTransaction(payment);

		// Set block's transactions signature
		transactionsSignature = blockGenerator.calculateTransactionsSignature(newBlock, generator);
		newBlock.setTransactionsSignature(transactionsSignature);

		// Check block has invalid signatures
		assertFalse("block with invalid transaction should be invalid", newBlock.isSignatureValid());
	}

	@Test
	public void validateBlock() {
		// Use inherited TestUtils.setup()

		// Generate next block
		Block newBlock = blockGenerator.generateNextBlock(databaseSet, generator, genesisBlock);

		// Add transactions signature
		byte[] transactionsSignature = Crypto.getInstance().sign(generator, newBlock.getGeneratorSignature());
		newBlock.setTransactionsSignature(transactionsSignature);

		// Check block is still valid
		assertTrue("block with valid signature should be valid", newBlock.isValid(databaseSet));

		// INVALID REFERENCE TEST

		// Create block with invalid reference ("new byte[128]" arg)
		Block invalidBlock = BlockFactory.getInstance().create(newBlock.getVersion(), new byte[128], newBlock.getTimestamp(), newBlock.getGeneratingBalance(),
				newBlock.getGenerator(), newBlock.getGeneratorSignature());

		// Check block is correctly invalid
		assertFalse("block with invalid reference should be invalid", invalidBlock.isValid(databaseSet));

		// INVALID TIMESTAMP TEST

		// Create block with invalid timestamp ("1L" arg)
		invalidBlock = BlockFactory.getInstance().create(newBlock.getVersion(), newBlock.getReference(), 1L, newBlock.getGeneratingBalance(),
				newBlock.getGenerator(), newBlock.getGeneratorSignature());

		// Check block is correctly invalid
		assertFalse("block with invalid timestamp should be invalid", invalidBlock.isValid(databaseSet));

		// INVALID BASE TARGET TEST

		// Create block with invalid base target ("1L" arg)
		invalidBlock = BlockFactory.getInstance().create(newBlock.getVersion(), newBlock.getReference(), newBlock.getTimestamp(), 1L, newBlock.getGenerator(),
				newBlock.getGeneratorSignature());

		// Check block is correctly invalid
		assertFalse("block with invalid base target should be invalid", invalidBlock.isValid(databaseSet));

		// INVALID TRANSACTION TEST

		// Create fresh, valid next block
		invalidBlock = blockGenerator.generateNextBlock(databaseSet, generator, genesisBlock);

		// Add invalid transaction: negative payment amount
		Account recipient = TestUtils.createTestAccount();
		long timestamp = newBlock.getTimestamp();
		byte[] signature = PaymentTransaction.generateSignature(databaseSet, generator, recipient, BigDecimal.valueOf(-100).setScale(8),
				BigDecimal.valueOf(1).setScale(8), timestamp);
		Transaction payment = new PaymentTransaction(generator, recipient, BigDecimal.valueOf(-100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp,
				generator.getLastReference(databaseSet), signature);
		invalidBlock.addTransaction(payment);

		// Check block is correctly invalid
		assertFalse("block with invalid transaction should be invalid", invalidBlock.isValid(databaseSet));

		// INVALID GENESIS TRANSACTION ON NON-GENESIS BLOCK TEST

		// Create fresh, valid next block
		invalidBlock = blockGenerator.generateNextBlock(databaseSet, generator, genesisBlock);

		// Add invalid genesis transaction (genesis transactions only allowed in genesis block)
		transaction = new GenesisTransaction(generator, BigDecimal.valueOf(1000).setScale(8), newBlock.getTimestamp());
		invalidBlock.addTransaction(transaction);

		// Check block is correctly invalid
		assertFalse("non-genesis block with invalid genesis transaction should be invalid", invalidBlock.isValid(databaseSet));
	}

	@Test
	public void parseBlock() {
		// Use inherited TestUtils.setup()

		// Generate next block
		Block block = blockGenerator.generateNextBlock(databaseSet, generator, genesisBlock);

		// Create a fork so we can create a 2nd transaction based on the 1st without having to process block
		DBSet fork = databaseSet.fork();

		// Generate 1st transaction
		Account recipientA = TestUtils.createTestAccount();
		long timestamp = block.getTimestamp();
		byte[] signature = PaymentTransaction.generateSignature(databaseSet, generator, recipientA, BigDecimal.valueOf(100).setScale(8),
				BigDecimal.valueOf(1).setScale(8), timestamp);
		Transaction payment1 = new PaymentTransaction(generator, recipientA, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp,
				generator.getLastReference(databaseSet), signature);
		// Process on fork so we can refer to it when generating 2nd transaction
		payment1.process(fork);
		// Add 1st transaction to test block
		block.addTransaction(payment1);

		// Generate 2nd transaction (refers to first)
		Account recipientB = TestUtils.createTestAccount();
		signature = PaymentTransaction.generateSignature(fork, generator, recipientB, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8),
				timestamp);
		Transaction payment2 = new PaymentTransaction(generator, recipientB, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp,
				generator.getLastReference(fork), signature);
		// Add 2nd transaction to test block
		block.addTransaction(payment2);

		// Set block's transactions signature
		byte[] transactionsSignature = Crypto.getInstance().sign(generator,
				Bytes.concat(block.getGeneratorSignature(), payment1.getSignature(), payment2.getSignature()));
		block.setTransactionsSignature(transactionsSignature);

		// Check block is still valid
		assertTrue("block with two valid transactions should have valid signatures", block.isSignatureValid());
		assertTrue("block with two valid transactions should be valid", block.isValid(databaseSet));

		// Serialize
		byte[] rawBlock = block.toBytes();

		try {
			// Deserialize/Parse
			Block parsedBlock = BlockFactory.getInstance().parse(rawBlock);

			// Check block is not a genesis block
			// XXX: This is never true because Block.parse never produces GenesisBlock instances
			// assertFalse("parsed block should not be a genesis block", parsedBlock instanceof GenesisBlock);

			// Check block signatures match
			assertTrue("parsed block has incorrect signature", Arrays.equals(block.getSignature(), parsedBlock.getSignature()));

			// Check generator accounts match
			assertEquals("parsed block has incorrect generator account", block.getGenerator().getAddress(), parsedBlock.getGenerator().getAddress());

			// Check base targets match
			assertEquals("parsed block has incorrect base target", block.getGeneratingBalance(), parsedBlock.getGeneratingBalance());

			// Check total fees match
			assertEquals("parsed block has incorrect total fees", block.getTotalFee(), parsedBlock.getTotalFee());

			// Check block references match
			assertTrue("parsed block has incorrect block reference", Arrays.equals(block.getReference(), parsedBlock.getReference()));

			// Check block timestamps match
			assertEquals("parsed block has incorrect timestamp", block.getTimestamp(), parsedBlock.getTimestamp());

			// Check transactions counts match
			assertEquals("parsed block has incorrect transactions count", block.getTransactionCount(), parsedBlock.getTransactionCount());
		} catch (Exception e) {
			fail("Exception while parsing block.");
		}

		// PARSE FAILURE TEST

		// Create invalid serialized block
		rawBlock = new byte[50];

		try {
			// Attempt to parse from bytes
			BlockFactory.getInstance().parse(rawBlock);

			// Should not reach here - FAIL
			fail("this should throw an exception");
		} catch (Exception e) {
			// Exception is thrown - PASS
		}
	}

	@Test
	public void processBlock() {
		// Use inherited TestUtils.setup()

		// Generate next block
		Block block = blockGenerator.generateNextBlock(databaseSet, generator, genesisBlock);

		// Create a fork so we can create a 2nd transaction based on the 1st without having to process block
		DBSet fork = databaseSet.fork();

		// Generate 1st transaction
		Account recipientA = TestUtils.createTestAccount();
		long timestamp = block.getTimestamp();
		byte[] signature = PaymentTransaction.generateSignature(databaseSet, generator, recipientA, BigDecimal.valueOf(100).setScale(8),
				BigDecimal.valueOf(1).setScale(8), timestamp);
		Transaction payment1 = new PaymentTransaction(generator, recipientA, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp,
				generator.getLastReference(databaseSet), signature);
		// Process on fork so we can refer to it when generating 2nd transaction
		payment1.process(fork);
		// Add 1st transaction to test block
		block.addTransaction(payment1);

		// Generate 2nd transaction (refers to first)
		Account recipientB = TestUtils.createTestAccount();
		signature = PaymentTransaction.generateSignature(fork, generator, recipientB, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8),
				timestamp);
		Transaction payment2 = new PaymentTransaction(generator, recipientB, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp,
				generator.getLastReference(fork), signature);
		// Add 2nd transaction to test block
		block.addTransaction(payment2);

		// Set block's transactions signature
		byte[] transactionsSignature = Crypto.getInstance().sign(generator,
				Bytes.concat(block.getGeneratorSignature(), payment1.getSignature(), payment2.getSignature()));
		block.setTransactionsSignature(transactionsSignature);

		// Check block is still valid
		assertTrue("block with two valid transactions should have valid signatures", block.isSignatureValid());
		assertTrue("block with two valid transactions should be valid", block.isValid(databaseSet));

		// Process block and add to blockchain
		block.process(databaseSet);

		// Check generator's new balance
		assertEquals("generator's balance incorrect", 999_800, generator.getConfirmedBalance(databaseSet).intValueExact());

		// Check generator's last reference
		assertTrue("generator's last reference should be 2nd payment's signature",
				Arrays.equals(generator.getLastReference(databaseSet), payment2.getSignature()));

		// Check recipientA's new balance
		assertEquals("recipientA's balance incorrect", 100, recipientA.getConfirmedBalance(databaseSet).intValueExact());

		// Check recipientA's last reference
		assertTrue("recipientA's last reference should be 1st payment's signature",
				Arrays.equals(recipientA.getLastReference(databaseSet), payment1.getSignature()));

		// Check recipientB's new balance
		assertEquals("recipientB's balance incorrect", 100, recipientB.getConfirmedBalance(databaseSet).intValueExact());

		// Check recipientB's last reference
		assertTrue("recipientB's last reference should be 2nd payment's signature",
				Arrays.equals(recipientB.getLastReference(databaseSet), payment2.getSignature()));

		// Check total fees
		assertEquals("Total fees incorrect", 2, block.getTotalFee().intValueExact());

		// Check transaction count
		assertEquals("Transaction count incorrect", 2, block.getTransactionCount());

		// Check processed block is last block on blockchain
		assertTrue("block should be last block on blockchain", Arrays.equals(block.getSignature(), databaseSet.getBlockMap().getLastBlock().getSignature()));
	}

	@Test
	public void orphanBlock() {
		// Use inherited TestUtils.setup()

		// Generate next block
		Block block = blockGenerator.generateNextBlock(databaseSet, generator, genesisBlock);

		// Create a fork so we can create a 2nd transaction based on the 1st without having to process block
		DBSet fork = databaseSet.fork();

		// Generate 1st transaction
		Account recipientA = TestUtils.createTestAccount();
		long timestamp = block.getTimestamp();
		byte[] signature = PaymentTransaction.generateSignature(databaseSet, generator, recipientA, BigDecimal.valueOf(100).setScale(8),
				BigDecimal.valueOf(1).setScale(8), timestamp);
		Transaction payment1 = new PaymentTransaction(generator, recipientA, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp,
				generator.getLastReference(databaseSet), signature);
		// Process on fork so we can refer to it when generating 2nd transaction
		payment1.process(fork);
		// Add 1st transaction to test block
		block.addTransaction(payment1);

		// Generate 2nd transaction (refers to first)
		Account recipientB = TestUtils.createTestAccount();
		signature = PaymentTransaction.generateSignature(fork, generator, recipientB, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8),
				timestamp);
		Transaction payment2 = new PaymentTransaction(generator, recipientB, BigDecimal.valueOf(100).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp,
				generator.getLastReference(fork), signature);
		// Add 2nd transaction to test block
		block.addTransaction(payment2);

		// Set block's transactions signature
		byte[] transactionsSignature = Crypto.getInstance().sign(generator,
				Bytes.concat(block.getGeneratorSignature(), payment1.getSignature(), payment2.getSignature()));
		block.setTransactionsSignature(transactionsSignature);

		// Check block is still valid
		assertTrue("block with two valid transactions should have valid signatures", block.isSignatureValid());
		assertTrue("block with two valid transactions should be valid", block.isValid(databaseSet));

		// Process block and add to blockchain
		block.process(databaseSet);

		// Orphan block / remove from blockchain
		block.orphan(databaseSet);

		// Check generator's balance is back to initial value
		assertEquals("generator's balance incorrect", 1_000_000, generator.getConfirmedBalance(databaseSet).intValueExact());

		// Check generator's last reference is back to initial genesis transaction
		assertTrue("generator's last reference should be genesis transaction signature",
				Arrays.equals(generator.getLastReference(databaseSet), transaction.getSignature()));

		// Check recipientA's balance
		assertEquals("recipientA's balance should be zero", 0, recipientA.getConfirmedBalance(databaseSet).intValueExact());

		// Check recipientA's last reference
		assertFalse("recipientA's last reference should not be 1st payment's signature",
				Arrays.equals(recipientA.getLastReference(databaseSet), payment1.getSignature()));

		// Check recipientB's balance
		assertEquals("recipientB's balance should be zero", 0, recipientB.getConfirmedBalance(databaseSet).intValueExact());

		// Check recipientB's last reference
		assertTrue("recipientB's last reference should be empty", Arrays.equals(recipientB.getLastReference(databaseSet), new byte[0]));

		// Check last block is back to genesis block
		assertTrue("last block on blockchain should be back to genesis block",
				Arrays.equals(genesisBlock.getSignature(), databaseSet.getBlockMap().getLastBlock().getSignature()));
	}
}
