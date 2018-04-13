package test;

import static org.junit.Assert.*;

import java.math.BigDecimal;

import ntp.NTP;

import org.junit.Before;
import org.junit.Test;

import com.google.common.primitives.Bytes;

import qora.block.Block;
import qora.crypto.Crypto;
import qora.transaction.PaymentTransaction;
import qora.transaction.Transaction;
import qora.transaction.TransactionFactory;
import database.DBSet;

public class TransactionAPITests extends TestUtils {
	@Before
	public void setup() {
		super.setup();
		transaction.process(databaseSet);
	}

	@Test
	public void createThenSignPaymentTransaction() {
		// Generate new block (timestamp will be far in past, nearer genesis timestamp)
		Block newBlock = blockGenerator.generateNextBlock(databaseSet, generator, genesisBlock);

		assertTrue("a valid block timestamp should be in the past", newBlock.getTimestamp() <= NTP.getTime());

		DBSet fork = databaseSet.fork();

		// Transaction timestamp needs to be before block timestamp
		long timestamp = newBlock.getTimestamp() - 10;

		// Create unsigned transaction
		byte[] signature = new byte[0]; // empty but not null
		Transaction unsignedTransaction = new PaymentTransaction(generator, recipient, BigDecimal.valueOf(1).setScale(8), BigDecimal.valueOf(1).setScale(8), timestamp,
				generator.getLastReference(fork), signature);
		
		// Sign this transaction 'externally'
		byte[] data = unsignedTransaction.toBytes(); // Includes 4-byte transaction type at start
		signature = Crypto.getInstance().sign(generator, data);
		data = Bytes.concat(data, signature);
		
		// Attempt to recreate transaction using (now) signed transaction in byte[]
		Transaction signedTransaction;
		try {
			signedTransaction = TransactionFactory.getInstance().parse(data);
		} catch (Exception e) {
			fail("Couldn't parse signed transaction");
			return;
		}
		
		// Check transaction is correct type
		assertTrue("Transaction should be payment transaction", signedTransaction instanceof PaymentTransaction);

		// Check transaction is actually valid
		assertEquals("Payment transaction should be valid", Transaction.VALIDATE_OK, signedTransaction.isValid(fork));

		// Process on fork
		signedTransaction.process(fork);
	}
}
