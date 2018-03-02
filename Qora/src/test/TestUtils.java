package test;

import java.math.BigDecimal;

import org.junit.Before;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import database.DBSet;
import ntp.NTP;
import qora.BlockGenerator;
import qora.account.PrivateKeyAccount;
import qora.block.GenesisBlock;
import qora.transaction.GenesisTransaction;
import qora.transaction.Transaction;

public class TestUtils {
	protected DBSet databaseSet;
	protected GenesisBlock genesisBlock;
	protected PrivateKeyAccount generator;
	protected PrivateKeyAccount recipient;
	protected Transaction transaction;
	protected BlockGenerator blockGenerator;

	@Before
	public void setup() {
		// Create empty in-memory database
		databaseSet = DBSet.createEmptyDatabaseSet();

		// Create genesis block and add to blockchain
		genesisBlock = new GenesisBlock();
		genesisBlock.process(databaseSet);

		// Create test accounts
		generator = createTestAccount();
		recipient = createTestAccount();

		// Process genesis transaction to make sure generator has funds
		transaction = new GenesisTransaction(generator, BigDecimal.valueOf(1_000_000L).setScale(8), NTP.getTime());
		transaction.process(databaseSet);

		blockGenerator = new BlockGenerator();
	}

	private static int testAccountCount = 0;

	public static PrivateKeyAccount createTestAccount() {
		byte[] seed = Ints.toByteArray(++testAccountCount);
		seed = Bytes.ensureCapacity(seed, 32, 0);
		return new PrivateKeyAccount(seed);
	}
}
