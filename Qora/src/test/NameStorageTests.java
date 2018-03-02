package test;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import ntp.NTP;

import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;

import qora.account.PrivateKeyAccount;
import qora.crypto.Base58;
import qora.naming.Name;
import qora.transaction.ArbitraryTransaction;
import qora.transaction.ArbitraryTransactionV1;
import qora.transaction.GenesisTransaction;
import qora.transaction.RegisterNameTransaction;
import qora.transaction.Transaction;
import utils.Pair;
import utils.Qorakeys;
import utils.StorageUtils;
import database.DBSet;

@SuppressWarnings("unchecked")
public class NameStorageTests {

	private DBSet databaseSet;
	private PrivateKeyAccount sender;
	private PrivateKeyAccount buyer;

	@Before
	public void setup() {
		// Create in-memory DB
		databaseSet = DBSet.createEmptyDatabaseSet();

		// Create test sender account
		sender = TestUtils.createTestAccount();

		// Create test buyer account
		buyer = TestUtils.createTestAccount();

		// Process genesis transaction to make sure sender has funds
		Transaction transaction = new GenesisTransaction(sender, BigDecimal.valueOf(1_000_000L).setScale(8), NTP.getTime());
		transaction.process(databaseSet);

		// Process genesis transaction to make sure buyer has funds
		transaction = new GenesisTransaction(buyer, BigDecimal.valueOf(1_000_000L).setScale(8), NTP.getTime());
		transaction.process(databaseSet);

		// Use sender to register the name "drizzt" with some value
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "drizzt", "this is the value");

		// Generate signature for transaction
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);

		// Create name registration transaction
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet),
				signature);

		// Check name registation transaction is valid
		assertEquals("name registration transaction should be valid", Transaction.VALIDATE_OK, nameRegistration.isValid(databaseSet));

		// Process transaction
		nameRegistration.process(databaseSet);
	}

	private ArbitraryTransaction buildAT(PrivateKeyAccount sender, List<Pair<String, String>> addCompleteKeys, List<String> removeCompleteKeys,
			List<Pair<String, String>> addListKeys, List<Pair<String, String>> removeListKeys, List<Pair<String, String>> addWithoutSeperator,
			List<Pair<String, String>> addPatch) {
		long timestamp = NTP.getTime();

		JSONObject storageJsonObject = StorageUtils.getStorageJsonObject(addCompleteKeys, removeCompleteKeys, addListKeys, removeListKeys, addWithoutSeperator,
				addPatch);
		storageJsonObject.put("name", "drizzt");
		byte[] data = storageJsonObject.toString().getBytes();

		// Encapsulate payload in AT
		byte[] signature = ArbitraryTransactionV1.generateSignature(databaseSet, sender, 10, data, BigDecimal.valueOf(1).setScale(8), timestamp);
		ArbitraryTransaction arbitraryTransaction = new ArbitraryTransactionV1(sender, 10, data, BigDecimal.ONE.setScale(8), timestamp,
				sender.getLastReference(databaseSet), signature);

		return arbitraryTransaction;
	}

	@Test
	public void testNameStorageNotChangedIfNotOwner() throws Exception {
		// We have nothing in name storage for drizzt here.
		assertNull("there should be no initial value for profileenable key",
				databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILEENABLE.toString()));
		assertNull("there should be no initial stored data", databaseSet.getNameStorageMap().get("drizzt"));

		// CREATE NAME STORAGE DATA

		// Create AT payload enabling profile
		// "{"name":"drizzt",{"addcomplete":{"profileenable":"yes"}}}"
		ArbitraryTransaction arbitraryTransaction = buildAT(sender,
				Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILEENABLE.toString(), "yes")), null, null, null, null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage was updated
		assertEquals("drizzt's profileenable flag should be \"yes\"", "yes",
				databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILEENABLE.toString()));

		// ATTEMPT TO CHANGE NAME STORAGE DATA USING NON-OWNER ACCOUNT

		// Create fresh account
		PrivateKeyAccount badSender = TestUtils.createTestAccount();

		// Create AT payload removing profileenable data
		arbitraryTransaction = buildAT(badSender, null, Arrays.asList(Qorakeys.PROFILEENABLE.toString()), null, null, null, null);

		// Process AT in attempt to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage hasn't been affected
		assertEquals("drizzt's profileenable flag should still be \"yes\"", "yes",
				databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILEENABLE.toString()));

	}

	@Test
	public void testAddRemoveComplete() throws Exception {
		// We have nothing in name storage for drizzt here.
		assertNull("there should be no initial value for profileenable key",
				databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILEENABLE.toString()));
		assertNull("there should be no initial stored data", databaseSet.getNameStorageMap().get("drizzt"));

		// CREATE NAME STORAGE DATA

		// Create AT payload enabling profile
		// "{"name":"drizzt",{"addcomplete":{"profileenable":"yes"}}}"
		ArbitraryTransaction arbitraryTransaction = buildAT(sender,
				Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILEENABLE.toString(), "yes")), null, null, null, null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage was updated
		assertEquals("drizzt's profileenable flag should be \"yes\"", "yes",
				databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILEENABLE.toString()));

		// TEST CHANGING EXISTING KEY'S VALUE

		// Create AT payload changing profileenable value
		arbitraryTransaction = buildAT(sender, Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILEENABLE.toString(), "anothervalue")), null,
				null, null, null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage was updated
		assertEquals("drizzt's profileenable flags should now be \"anothervalue\"", "anothervalue",
				databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILEENABLE.toString()));

		// TEST REMOVING EXISTING KEY

		// Create AT payload removing profileenable key & value
		arbitraryTransaction = buildAT(sender, null, Arrays.asList(Qorakeys.PROFILEENABLE.toString()), null, null, null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// We have nothing in name storage for drizzt here.
		assertNull("there should be no value for profileenable key", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILEENABLE.toString()));
	}

	@Test
	public void testAddRemoveListKeys() throws Exception {
		// We have nothing in name storage for drizzt here.
		assertNull(databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILELIKEPOSTS.toString()));
		assertNull(databaseSet.getNameStorageMap().get("drizzt"));

		// TEST REMOVING ENTRY FROM EMPTY LIST

		// Remove "haloman" - not in list - no list
		ArbitraryTransaction arbitraryTransaction = buildAT(sender, null, null, null,
				Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILELIKEPOSTS.toString(), "haloman")), null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage was updated
		assertNull(databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILELIKEPOSTS.toString()));

		// TEST ADDING FIRST ENTRY TO LIST

		// Create AT payload adding skerberus as single-entry in profilelikeposts list
		// "{"name":"drizzt",{"addlist":{"profilelikeposts":"skerberus"}}}"
		arbitraryTransaction = buildAT(sender, null, null,
				Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILELIKEPOSTS.toString(), "skerberus")), null, null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage was updated
		assertEquals("skerberus", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILELIKEPOSTS.toString()));

		// TEST ADDING ANOTHER ENTRY TO LIST

		// Add vrontis to list
		arbitraryTransaction = buildAT(sender, null, null, Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILELIKEPOSTS.toString(), "vrontis")),
				null, null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage was updated
		assertEquals("skerberus;vrontis", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILELIKEPOSTS.toString()));

		// ADD SOME MORE ENTRIES TO LIST TO HELP REMOVAL TESTING

		List<String> moreEntries = Arrays.asList("vbcs", "quid");
		for (String entry : moreEntries) {
			// Add entry to list
			arbitraryTransaction = buildAT(sender, null, null, Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILELIKEPOSTS.toString(), entry)),
					null, null, null);

			// Process transaction to update name storage
			arbitraryTransaction.process(databaseSet);
		}

		// Check name storage was updated
		assertEquals("skerberus;vrontis;vbcs;quid", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILELIKEPOSTS.toString()));

		// TEST REMOVING MIDDLE ENTRY FROM LIST

		// Remove vbcs
		arbitraryTransaction = buildAT(sender, null, null, null,
				Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILELIKEPOSTS.toString(), "vbcs")), null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage was updated
		assertEquals("skerberus;vrontis;quid", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILELIKEPOSTS.toString()));

		// TEST REMOVING ENTRY THAT IS NOT IN LIST

		// Remove "haloman" - not in list
		arbitraryTransaction = buildAT(sender, null, null, null,
				Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILELIKEPOSTS.toString(), "haloman")), null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage was updated
		assertEquals("skerberus;vrontis;quid", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILELIKEPOSTS.toString()));

		// TEST REMOVING LAST ENTRY FROM LIST

		// Remove last entry from list
		arbitraryTransaction = buildAT(sender, null, null, null,
				Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILELIKEPOSTS.toString(), "quid")), null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage was updated
		assertEquals("skerberus;vrontis", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILELIKEPOSTS.toString()));

		// TEST REMOVING FIRST ENTRY FROM LIST

		// Remove first entry from list
		arbitraryTransaction = buildAT(sender, null, null, null,
				Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILELIKEPOSTS.toString(), "skerberus")), null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage was updated
		assertEquals("vrontis", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILELIKEPOSTS.toString()));

		// TEST REMOVING ONLY ENTRY FROM LIST

		// Remove final entry from list
		arbitraryTransaction = buildAT(sender, null, null, null,
				Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILELIKEPOSTS.toString(), "vrontis")), null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage was updated
		assertNull(databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILELIKEPOSTS.toString()));

		// TEST ADDING MORE THAN ONE ENTRY

		// Add pre-separated entries to list
		arbitraryTransaction = buildAT(sender, null, null, Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILELIKEPOSTS.toString(), "a;b;c")),
				null, null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage was updated
		assertEquals("a;b;c", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILELIKEPOSTS.toString()));

		// TEST REMOVING MORE THAN ONE ENTRY FROM LIST, INCLUDING TRYING ENTRIES THAT DON'T EXIST IN LIST

		// Remove pre-separated entries from list
		arbitraryTransaction = buildAT(sender, null, null, null,
				Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILELIKEPOSTS.toString(), "a;not-there;c")), null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage was updated
		assertEquals("b", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILELIKEPOSTS.toString()));

		// TEST SEPARATOR-ONLY ADD

		// Add pre-separated entries to list
		arbitraryTransaction = buildAT(sender, null, null, Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILELIKEPOSTS.toString(), ";")), null,
				null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage was updated
		assertEquals("b", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILELIKEPOSTS.toString()));

		// TEST SEPARATOR-ONLY REMOVE

		// Add pre-separated entries to list
		arbitraryTransaction = buildAT(sender, null, null, null, Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILELIKEPOSTS.toString(), ";")),
				null, null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Check name storage was updated
		assertEquals("b", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILELIKEPOSTS.toString()));
	}

	@Test
	public void testAddWithoutSeperatorAndCheckBasicOrphaning() throws Exception {
		// TEST CREATING INITIAL DATA

		// Create initial website as "first"
		ArbitraryTransaction arbitraryTransaction = buildAT(sender, null, null, null, null,
				Collections.singletonList(new Pair<String, String>(Qorakeys.WEBSITE.toString(), "first")), null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Add transaction to transaction map so it can fetched during orphaning
		databaseSet.getTransactionMap().add(arbitraryTransaction);

		// Check name storage was updated
		assertEquals("first", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.WEBSITE.toString()));

		// TEST APPENDING TO DATA
		ArbitraryTransaction arbitraryTransaction2 = buildAT(sender, null, null, null, null,
				Collections.singletonList(new Pair<String, String>(Qorakeys.WEBSITE.toString(), " second")), null);

		// Process transaction to update name storage
		arbitraryTransaction2.process(databaseSet);

		// Add transaction to transaction map so it can fetched during orphaning
		databaseSet.getTransactionMap().add(arbitraryTransaction2);

		// Check name storage was updated
		assertEquals("first second", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.WEBSITE.toString()));

		// TEST ORPHANING

		// Orphan first transaction
		arbitraryTransaction.orphan(databaseSet);

		// Check name storage was updated
		assertEquals(" second", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.WEBSITE.toString()));

		// Orphan second transaction
		arbitraryTransaction2.orphan(databaseSet);

		// Check name storage was updated
		assertNull(databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.WEBSITE.toString()));
	}

	private List<Transaction> setupComplexOrphanNameStorageTests(String someRandomKey) throws Exception {
		List<Transaction> transactions = new ArrayList<Transaction>();

		// Create 1st complex transaction
		ArbitraryTransaction arbitraryTransaction = buildAT(sender,
				Collections.singletonList(new Pair<String, String>(Qorakeys.PROFILEENABLE.toString(), "yes")), null,
				Collections.singletonList(new Pair<String, String>(someRandomKey, "skerberus")), null,
				Collections.singletonList(new Pair<String, String>(Qorakeys.WEBSITE.toString(), "first")), null);

		// Process transaction to update name storage
		arbitraryTransaction.process(databaseSet);

		// Add transaction to transaction map so it can fetched during orphaning
		databaseSet.getTransactionMap().add(arbitraryTransaction);
		transactions.add(arbitraryTransaction);

		// Check name storage was updated
		// profileenable: yes
		// website: first
		// randomlinkingExample: skerberus
		assertEquals("yes", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILEENABLE.toString()));
		assertEquals("first", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.WEBSITE.toString()));
		assertEquals("skerberus", databaseSet.getNameStorageMap().getOpt("drizzt", someRandomKey));

		// 2nd complex transaction
		ArbitraryTransaction arbitraryTransaction2 = buildAT(sender, null, null, Collections.singletonList(new Pair<String, String>(someRandomKey, "vrontis")),
				null, Collections.singletonList(new Pair<String, String>(Qorakeys.WEBSITE.toString(), "second")), null);

		// Process transaction to update name storage
		arbitraryTransaction2.process(databaseSet);

		// Add transaction to transaction map so it can fetched during orphaning
		databaseSet.getTransactionMap().add(arbitraryTransaction2);
		transactions.add(arbitraryTransaction2);

		// Check name storage was updated
		// profileenable: yes
		// website: firstsecond
		// randomlinkingExample: skerberus;vrontis
		assertEquals("yes", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILEENABLE.toString()));
		assertEquals("firstsecond", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.WEBSITE.toString()));
		assertEquals("skerberus;vrontis", databaseSet.getNameStorageMap().getOpt("drizzt", someRandomKey));

		// 3rd complex transaction
		ArbitraryTransaction arbitraryTransaction3 = buildAT(sender, null, null, Collections.singletonList(new Pair<String, String>("asdf", "asdf")), null,
				Collections.singletonList(new Pair<String, String>(Qorakeys.WEBSITE.toString(), "third")), null);

		// Process transaction to update name storage
		arbitraryTransaction3.process(databaseSet);

		// Add transaction to transaction map so it can fetched during orphaning
		databaseSet.getTransactionMap().add(arbitraryTransaction3);
		transactions.add(arbitraryTransaction3);

		// Check name storage was updated
		// profileenable: yes
		// website: firstsecondthird
		// randomlinkingExample: skerberus;vrontis
		// asdf: asdf
		assertEquals("yes", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILEENABLE.toString()));
		assertEquals("firstsecondthird", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.WEBSITE.toString()));
		assertEquals("skerberus;vrontis", databaseSet.getNameStorageMap().getOpt("drizzt", someRandomKey));
		assertEquals("asdf", databaseSet.getNameStorageMap().getOpt("drizzt", "asdf"));

		return transactions;
	}

	@Test
	public void testComplexOrphaning1() throws Exception {
		String someRandomKey = "randomlinkingExample";

		List<Transaction> transactions = this.setupComplexOrphanNameStorageTests(someRandomKey);

		// TEST ORPHANING 2ND TRANSACTION

		// Orphan 2nd transaction
		transactions.get(1).orphan(databaseSet);

		// Check name storage was updated
		// profileenable: yes
		// website: firstthird
		// randomlinkingExample: skerberus
		// asdf: asdf
		assertEquals("yes", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILEENABLE.toString()));
		assertEquals("firstthird", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.WEBSITE.toString()));
		assertEquals("skerberus", databaseSet.getNameStorageMap().getOpt("drizzt", someRandomKey));
		assertEquals("asdf", databaseSet.getNameStorageMap().getOpt("drizzt", "asdf"));
	}

	@Test
	public void testComplexOrphaning2() throws Exception {
		String someRandomKey = "randomlinkingExample";

		List<Transaction> transactions = this.setupComplexOrphanNameStorageTests(someRandomKey);

		// TEST ORPHANING 1ST TRANSACTION

		// Orphan 1st transaction
		transactions.get(0).orphan(databaseSet);

		// Check name storage was updated
		// (no profileenable entry)
		// website: secondthird
		// randomlinkingExample: vrontis
		// asdf: asdf
		assertNull(databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILEENABLE.toString()));
		assertEquals("secondthird", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.WEBSITE.toString()));
		assertEquals("vrontis", databaseSet.getNameStorageMap().getOpt("drizzt", someRandomKey));
		assertEquals("asdf", databaseSet.getNameStorageMap().getOpt("drizzt", "asdf"));

		// TEST ORPHANING 2ND TRANSACTION

		// Orphan 2nd transaction
		transactions.get(1).orphan(databaseSet);

		// Check name storage was updated
		// (no profileenable entry)
		// website: third
		// (no randomlinkingExample entry)
		// asdf: asdf
		assertNull(databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILEENABLE.toString()));
		assertEquals("third", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.WEBSITE.toString()));
		assertNull(databaseSet.getNameStorageMap().getOpt("drizzt", someRandomKey));
		assertEquals("asdf", databaseSet.getNameStorageMap().getOpt("drizzt", "asdf"));
	}

	@Test
	public void testComplexOrphaning3() throws Exception {
		String someRandomKey = "randomlinkingExample";

		List<Transaction> transactions = this.setupComplexOrphanNameStorageTests(someRandomKey);

		// TEST ORPHANING 3RD TRANSACTION

		// Orphan 3rd transaction
		transactions.get(2).orphan(databaseSet);

		// Check name storage was updated
		// profileenable: yes
		// website: firstsecond
		// randomlinkingExample: skerberus;vrontis
		// (no asdf entry)
		assertEquals("yes", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILEENABLE.toString()));
		assertEquals("firstsecond", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.WEBSITE.toString()));
		assertEquals("skerberus;vrontis", databaseSet.getNameStorageMap().getOpt("drizzt", someRandomKey));
		assertNull(databaseSet.getNameStorageMap().getOpt("drizzt", "asdf"));

		// TEST ORPHANING 2ND TRANSACTION

		// Orphan 2nd transaction
		transactions.get(1).orphan(databaseSet);

		// Check name storage was updated
		// profileenable: yes
		// website: first
		// randomlinkingExample: skerberus
		// (no asdf entry)
		assertEquals("yes", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.PROFILEENABLE.toString()));
		assertEquals("first", databaseSet.getNameStorageMap().getOpt("drizzt", Qorakeys.WEBSITE.toString()));
		assertEquals("skerberus", databaseSet.getNameStorageMap().getOpt("drizzt", someRandomKey));
		assertNull(databaseSet.getNameStorageMap().getOpt("drizzt", "asdf"));
	}

	@Test
	public void testPatch() throws Exception {
		// Use sender to register the name "2ndtest"
		long timestamp = NTP.getTime();
		Name name = new Name(sender, "2ndtest", "{\"defaultKey\":\"\"}");

		// Generate signature for transaction
		byte[] signature = RegisterNameTransaction.generateSignature(databaseSet, sender, name, BigDecimal.valueOf(1).setScale(8), timestamp);

		// Create name registration transaction
		Transaction nameRegistration = new RegisterNameTransaction(sender, name, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet),
				signature);

		// Check name registation transaction is valid
		assertEquals("name registration transaction should be valid", Transaction.VALIDATE_OK, nameRegistration.isValid(databaseSet));

		// Process transaction
		nameRegistration.process(databaseSet);


		// Create AT with initial data

		final String initialData = "?gz!H4sIAAAAAAAAAKtWSkxJSc7PLchJLUlVslKqjlEqSS0uiVGyilF6tXLr6827ni5perR7RmJxSlpMTB66EBFKqKZrAISUapV0lPISc0EhY5SXAgoZpVoAgS1blzMBAAA=";

		// Encapsulate payload in AT
		byte[] initialSignature = ArbitraryTransactionV1.generateSignature(databaseSet, sender, 10, initialData.getBytes(), BigDecimal.valueOf(1).setScale(8), timestamp);
		ArbitraryTransaction initialAT = new ArbitraryTransactionV1(sender, 10, initialData.getBytes(), BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), initialSignature);

		initialAT.process(databaseSet);
		databaseSet.getTransactionMap().add(initialAT);


		// Create AT with patch

		final String patch58 = "3JnyJhPwbTsHrh7zMXxhc6S7kAE2c8HZE9CtwE5yFxPdUHHy2vnKGwcRkqJ7Cppas9B8q6" +
				"VDcV5TwY4j21QVukUdEf8ynsFS6gCNDZoFHGm6t7C8WWB7HiuYetTRoTHoa62QQQQDfiLt" +
				"5X7HGjKmrU7wmQXrieAZpz6Dd2ZgFUTLoAbXXkwmRWC1h3WaDBPX36nRmhhE";
		// Data: {"patch":"{\"test\":\"--- source\\n+++ dest\\n@@ -5,0 +5,1 @@\\n+ꩵ볺夂⻘asdf300\\n@@ -12,1 +13,0 @@\\n-ꩵ볺夂⻘asdf\"}","name":"2ndtest"}

		final byte[] patchData = Base58.decode(patch58);

		byte[] patchSignature = ArbitraryTransactionV1.generateSignature(databaseSet, sender, 10, patchData, BigDecimal.valueOf(1).setScale(8), timestamp);
		ArbitraryTransaction patchAT = new ArbitraryTransactionV1(sender, 10, patchData, BigDecimal.ONE.setScale(8), timestamp, sender.getLastReference(databaseSet), patchSignature);

		patchAT.process(databaseSet);
		databaseSet.getTransactionMap().add(patchAT);


		// Double check patch was applied successfully

		final String expectedPostPatch = "ꩵ볺夂⻘asdf\n" +
				"ꩵ볺夂⻘asdfꩵ볺夂⻘asdf\n" +
				"ꩵ볺夂⻘asdf\n" +
				"ꩵ볺夂⻘asdfꩵ볺夂⻘asdf\n" +
				"ꩵ볺夂⻘asdf\n" +
				"ꩵ볺夂⻘asdf300\n" +
				"ꩵ볺夂⻘asdf\n" +
				"ꩵ볺夂⻘asdf\n" +
				"ꩵ볺夂⻘asdf\n" +
				"ꩵ볺夂⻘asdf\n" +
				"ꩵ볺夂⻘asdf\n" +
				"ꩵ볺夂⻘asdf";
		final String actualPostPatch = databaseSet.getNameStorageMap().getOpt("2ndtest", "test");

		assertEquals("Patch wasn't applied/stored correctly", expectedPostPatch, actualPostPatch);
	}
}
