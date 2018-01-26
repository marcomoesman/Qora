package test;

import static org.junit.Assert.*;

import java.math.BigDecimal;

import ntp.NTP;

import org.junit.Test;

import qora.BlockGenerator;
import qora.account.Account;
import qora.account.PrivateKeyAccount;
import qora.block.Block;
import qora.block.GenesisBlock;
import qora.crypto.Crypto;
import qora.transaction.GenesisTransaction;
import qora.transaction.PaymentTransaction;
import qora.transaction.Transaction;
import database.DBSet;

public class GeneratorTests {

	@Test
	public void generateNewBlock() {
		// Create empty in-memory database
		DBSet databaseSet = DBSet.createEmptyDatabaseSet();

		// Create genesis block and add to blockchain
		GenesisBlock genesisBlock = new GenesisBlock();
		genesisBlock.process(databaseSet);

		// CREATE KNOWN ACCOUNT
		PrivateKeyAccount generator = TestUtils.createTestAccount();

		// Process genesis transaction to make sure generator has funds
		Transaction transaction = new GenesisTransaction(generator, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);

		// Generate 2,000 blocks!
		BlockGenerator blockGenerator = new BlockGenerator();
		Block lastBlock = genesisBlock;
		for (int i = 0; i < 2000; ++i) {
			// Generate next block
			Block newBlock = blockGenerator.generateNextBlock(databaseSet, generator, lastBlock);

			// Add transactions signature
			byte[] transactionsSignature = Crypto.getInstance().sign(generator, newBlock.getGeneratorSignature());
			newBlock.setTransactionsSignature(transactionsSignature);

			// Check block signatures are valid
			assertTrue("block should have valid signatures", newBlock.isSignatureValid());

			// Check block itself is valid
			assertTrue("block should be valid", newBlock.isValid(databaseSet));

			// Process new block - add to blockchain
			newBlock.process(databaseSet);

			// Last block is the new block
			lastBlock = newBlock;
		}
	}

	@Test
	public void addTransactions() {
		// Create empty in-memory database
		DBSet databaseSet = DBSet.createEmptyDatabaseSet();

		// Create genesis block and add to blockchain
		GenesisBlock genesisBlock = new GenesisBlock();
		genesisBlock.process(databaseSet);

		// Create test account
		PrivateKeyAccount generator = TestUtils.createTestAccount();

		// Process genesis transaction to make sure generator has funds
		Transaction transaction = new GenesisTransaction(generator, BigDecimal.valueOf(1000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);

		// Generate new block (timestamp will be far in past, nearer genesis timestamp)
		BlockGenerator blockGenerator = new BlockGenerator();
		Block newBlock = blockGenerator.generateNextBlock(databaseSet, generator, genesisBlock);

		assertTrue("a valid block timestamp should be in the past", newBlock.getTimestamp() <= NTP.getTime());

		// Add 10 unconfirmed, valid transactions
		Account recipient = TestUtils.createTestAccount();
		DBSet fork = databaseSet.fork();

		for (int i = 0; i < 10; ++i) {
			// Transaction timestamp needs to be before block timestamp
			long timestamp = newBlock.getTimestamp() - 10 + i;

			// Generate transaction signature
			byte[] signature = PaymentTransaction.generateSignature(fork, generator, recipient, BigDecimal.valueOf(1).setScale(8),
					BigDecimal.valueOf(1).setScale(8), timestamp);

			// Create valid transaction
			Transaction payment = new PaymentTransaction(generator, recipient, BigDecimal.valueOf(1).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp,
					generator.getLastReference(fork), signature);

			// Check transaction is actually valid
			assertEquals("Payment transaction should be valid", Transaction.VALIDATE_OK, payment.isValid(fork));

			// Process on fork
			payment.process(fork);

			// Add to unconfirmed transactions
			blockGenerator.addUnconfirmedTransaction(databaseSet, payment, false);
		}

		// Add all the unconfirmed transactions to new block
		blockGenerator.addUnconfirmedTransactions(databaseSet, newBlock);

		// Check that all 10 transaction made it into the new block
		assertEquals("new block should have 10 transactions", 10, newBlock.getTransactionCount());

		// Check new block is still valid
		assertTrue("new block with valid transactions should be valid", newBlock.isValid(databaseSet));
	}

	@Test
	public void addManyTransactions() {
		// Create empty in-memory database
		DBSet databaseSet = DBSet.createEmptyDatabaseSet();

		// Create genesis block and add to blockchain
		GenesisBlock genesisBlock = new GenesisBlock();
		genesisBlock.process(databaseSet);

		// Create test account
		PrivateKeyAccount generator = TestUtils.createTestAccount();

		// Process genesis transaction to make sure generator has funds
		Transaction transaction = new GenesisTransaction(generator, BigDecimal.valueOf(100000).setScale(8), NTP.getTime());
		transaction.process(databaseSet);

		// Generate new block (timestamp will be far in past, nearer genesis timestamp)
		BlockGenerator blockGenerator = new BlockGenerator();
		Block newBlock = blockGenerator.generateNextBlock(databaseSet, generator, genesisBlock);

		// Add 10,000 unconfirmed, valid transactions (too many to fit into one block)
		Account recipient = TestUtils.createTestAccount();
		DBSet fork = databaseSet.fork();

		for (int i = 0; i < 10000; ++i) {
			// Transaction timestamp needs to be before block timestamp
			long timestamp = newBlock.getTimestamp() - 10000 + i;

			// Generate transaction signature
			byte[] signature = PaymentTransaction.generateSignature(fork, generator, recipient, BigDecimal.valueOf(1).setScale(8),
					BigDecimal.valueOf(1).setScale(8), timestamp);

			// Create valid transaction
			Transaction payment = new PaymentTransaction(generator, recipient, BigDecimal.valueOf(1).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp,
					generator.getLastReference(fork), signature);

			// Check transaction is actually valid
			assertEquals("Payment transaction should be valid", Transaction.VALIDATE_OK, payment.isValid(fork));

			// Process on fork
			payment.process(fork);

			// Add to unconfirmed transactions
			blockGenerator.addUnconfirmedTransaction(databaseSet, payment, false);
		}

		// Add all the unconfirmed transactions to new block
		blockGenerator.addUnconfirmedTransactions(databaseSet, newBlock);

		// Check that at least one transaction made it into the new block
		assertNotEquals("new block should have at least one transaction", 0, newBlock.getTransactionCount());

		// Check that not all transactions made it into the new block
		assertNotEquals("new block should not contain all 10,000 transactions", 10000, newBlock.getTransactionCount());

		// Check new block is still valid
		assertTrue("new block with valid transactions should be valid", newBlock.isValid(databaseSet));
	}

	@Test
	public void addManyTransactionsWithDifferentFees() {
		// Create empty in-memory database
		DBSet databaseSet = DBSet.createEmptyDatabaseSet();

		// Create genesis block and add to blockchain
		GenesisBlock genesisBlock = new GenesisBlock();
		genesisBlock.process(databaseSet);

		// Create test account
		PrivateKeyAccount generator = TestUtils.createTestAccount();

		// Process genesis transaction to make sure generator has funds (1000 transactions of up to 1 amount + 1..100 fee)
		Transaction transaction = new GenesisTransaction(generator, BigDecimal.valueOf(1_000_000L).setScale(8), NTP.getTime());
		transaction.process(databaseSet);

		// Generate new block (timestamp will be far in past, nearer genesis timestamp)
		BlockGenerator blockGenerator = new BlockGenerator();
		Block newBlock = blockGenerator.generateNextBlock(databaseSet, generator, genesisBlock);

		// Add 1,000 unconfirmed, valid transactions with random fees
		Account recipient = TestUtils.createTestAccount();
		DBSet fork = databaseSet.fork();

		for (int i = 0; i < 1000; ++i) {
			// Transaction timestamp needs to be before block timestamp
			long timestamp = newBlock.getTimestamp() - 1000 + i;

			// Random fee
			BigDecimal fee = BigDecimal.valueOf(1).setScale(8).add(BigDecimal.valueOf((long)(Math.random() * 100)).setScale(8)); 

			// Generate transaction signature
			byte[] signature = PaymentTransaction.generateSignature(fork, generator, recipient, BigDecimal.valueOf(1).setScale(8), fee, timestamp);

			// Create valid transaction
			Transaction payment = new PaymentTransaction(generator, recipient, BigDecimal.valueOf(1).setScale(8), fee, timestamp,
					generator.getLastReference(fork), signature);

			// Check transaction is actually valid
			assertEquals("Payment transaction should be valid", Transaction.VALIDATE_OK, payment.isValid(fork));

			// Process on fork
			payment.process(fork);

			// Add to unconfirmed transactions
			blockGenerator.addUnconfirmedTransaction(databaseSet, payment, false);
		}

		// Add all the unconfirmed transactions to new block
		blockGenerator.addUnconfirmedTransactions(databaseSet, newBlock);

		// Check that at least one transaction made it into the new block
		assertNotEquals("new block should have at least one transaction", 0, newBlock.getTransactionCount());

		// Check that all transactions made it into the new block
		assertEquals("new block should contain all 1,000 transactions", 1000, newBlock.getTransactionCount());

		// Check new block is still valid
		assertTrue("new block with valid transactions should be valid", newBlock.isValid(databaseSet));
	}

	// TODO CALCULATETRANSACTIONSIGNATURE
}
