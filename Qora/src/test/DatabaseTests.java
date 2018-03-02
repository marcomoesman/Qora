package test;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import database.DBSet;
import qora.web.OrphanNameStorageHelperMap;
import utils.ByteArrayUtils;

public class DatabaseTests {

	@Test
	public void databaseFork() {
		// Create in-memory DB
		DBSet databaseSet = DBSet.createEmptyDatabaseSet();

		// Create fork
		DBSet fork = databaseSet.fork();

		// TEST CHANGE TO MAIN DB INHERITED BY FORK (when no forked value exists)
		
		// Set balance in main DB
		databaseSet.getBalanceMap().set("test", BigDecimal.ONE);

		// Check balance in main DB
		assertEquals(BigDecimal.ONE, databaseSet.getBalanceMap().get("test"));

		// Check balance in fork
		assertEquals(BigDecimal.ONE, fork.getBalanceMap().get("test"));

		// TEST CHANGE IN FORK DOESN'T BACK-PROPAGATE TO MAIN DB
		
		// Set balance in fork
		fork.getBalanceMap().set("test", BigDecimal.TEN);

		// Check balance in main DB
		assertEquals(BigDecimal.ONE, databaseSet.getBalanceMap().get("test"));

		// Check balance in fork
		assertEquals(BigDecimal.TEN, fork.getBalanceMap().get("test"));

		// TEST NESTED FORKS
		
		// Create 2nd fork of 1st fork
		DBSet fork2 = fork.fork();

		// Set balance in 2nd fork
		fork2.getBalanceMap().set("test", BigDecimal.ZERO);

		// Check balance in main DB
		assertEquals(BigDecimal.ONE, databaseSet.getBalanceMap().get("test"));

		// Check balance in 1st fork
		assertEquals(BigDecimal.TEN, fork.getBalanceMap().get("test"));

		// Check balance in 2nd fork
		assertEquals(BigDecimal.ZERO, fork2.getBalanceMap().get("test"));
		
		// TEST CHANGE TO MAIN DB DOESN'T PROPAGATE TO ALL FORKS
		
		final BigDecimal someValue = BigDecimal.valueOf(2L);
		
		// Set balance in main DB
		databaseSet.getBalanceMap().set("test", someValue);

		// Check balance in main DB
		assertEquals(someValue, databaseSet.getBalanceMap().get("test"));

		// Check balance in 1st fork
		assertEquals(BigDecimal.TEN, fork.getBalanceMap().get("test"));

		// Check balance in 2nd fork
		assertEquals(BigDecimal.ZERO, fork2.getBalanceMap().get("test"));
	}

	@Test
	public void ListValueForklessTests() {
		// Set up
		String name = "test";
		byte[] testSignature = new byte[] { 123 };

		// Create in-memory DB
		DBSet databaseSet = DBSet.createEmptyDatabaseSet();
		OrphanNameStorageHelperMap map = databaseSet.getOrphanNameStorageHelperMap();

		// Initial value should be null
		assertNull(map.get(name));

		// Add value
		map.add(name, testSignature);

		// Check value added correctly
		List<byte[]> signatures = map.get(name);
		assertNotNull(signatures);
		assertTrue(ByteArrayUtils.contains(signatures, testSignature));

		// Remove value
		map.remove(name, testSignature);

		// Check value removed correctly
		signatures = map.get(name);
		assertFalse(ByteArrayUtils.contains(signatures, testSignature));
	}

	@Test
	public void ListValueForkEmptyParentTest() {
		// Set up
		String name = "test";
		byte[] testSignature = new byte[] { 123 };

		// Create in-memory DB
		DBSet databaseSet = DBSet.createEmptyDatabaseSet();
		OrphanNameStorageHelperMap mainMap = databaseSet.getOrphanNameStorageHelperMap();

		// Create fork
		DBSet fork = databaseSet.fork();
		OrphanNameStorageHelperMap forkMap = fork.getOrphanNameStorageHelperMap();

		// We start with no parent value...
		assertNull(mainMap.get(name));

		// Check fork is null
		assertNull(forkMap.get(name));

		// Add value to fork
		forkMap.add(name, testSignature);

		// Check value added correctly
		List<byte[]> forkSigs = forkMap.get(name);
		assertNotNull(forkSigs);
		assertTrue(ByteArrayUtils.contains(forkSigs, testSignature));

		// Check parent still null
		assertNull(mainMap.get(name));

		// Remove value from fork
		forkMap.remove(name, testSignature);

		// Check value removed correctly
		forkSigs = forkMap.get(name);
		assertFalse(ByteArrayUtils.contains(forkSigs, testSignature));

		// Check parent still null
		assertNull(mainMap.get(name));
	}

	@Test
	public void ListValueForkEmptyParentRecreateTest() {
		// Set up
		String name = "test";
		byte[] testSignature = new byte[] { 123 };

		// Create in-memory DB
		DBSet databaseSet = DBSet.createEmptyDatabaseSet();
		OrphanNameStorageHelperMap mainMap = databaseSet.getOrphanNameStorageHelperMap();

		// Create fork
		DBSet fork = databaseSet.fork();
		OrphanNameStorageHelperMap forkMap = fork.getOrphanNameStorageHelperMap();

		// We start with no parent value...
		assertNull(mainMap.get(name));

		// Check fork is null
		assertNull(forkMap.get(name));

		// Add value to fork
		forkMap.add(name, testSignature);

		// Check value added correctly
		List<byte[]> forkSigs = forkMap.get(name);
		assertNotNull(forkSigs);
		assertTrue(ByteArrayUtils.contains(forkSigs, testSignature));

		// Check parent still null
		assertNull(mainMap.get(name));

		// Remove value from fork
		forkMap.remove(name, testSignature);

		// Check value removed correctly
		forkSigs = forkMap.get(name);
		assertFalse(ByteArrayUtils.contains(forkSigs, testSignature));

		// Check parent still null
		assertNull(mainMap.get(name));

		// Now actually delete entire list from fork (should create entry for key in "deleted" list)
		forkMap.delete(name);

		// Check list is now null
		assertNull(forkMap.get(name));

		// Re-add value to fork
		forkMap.add(name, testSignature);

		// Check value added correctly
		forkSigs = forkMap.get(name);
		assertNotNull(forkSigs);
		assertEquals(1, forkSigs.size()); // Should only be one entry
		assertTrue(ByteArrayUtils.contains(forkSigs, testSignature));

		// Check parent still null
		assertNull(mainMap.get(name));
	}

	@Test
	public void ListValueForkNonEmptyParentTest() {
		// Set up
		String name = "test";
		byte[] testSignature = new byte[] { 123 };
		byte[] anotherSignature = new byte[] { -123 };

		// Create in-memory DB
		DBSet databaseSet = DBSet.createEmptyDatabaseSet();
		OrphanNameStorageHelperMap mainMap = databaseSet.getOrphanNameStorageHelperMap();

		// We start by creating parent value

		// Add value to parent
		mainMap.add(name, testSignature);

		// Check value added correctly
		List<byte[]> mainSigs = mainMap.get(name);
		assertNotNull(mainSigs);
		assertTrue(ByteArrayUtils.contains(mainSigs, testSignature));

		// Create new fork
		DBSet fork = databaseSet.fork();
		OrphanNameStorageHelperMap forkMap = fork.getOrphanNameStorageHelperMap();

		// Check fork agrees with parent
		List<byte[]> forkSigs = forkMap.get(name);
		assertEquals(mainSigs.size(), forkSigs.size());
		assertEquals(1, forkSigs.size()); // Should only be one entry
		assertTrue(ByteArrayUtils.contains(mainSigs, testSignature));
		assertTrue(ByteArrayUtils.contains(forkSigs, testSignature));
		assertTrue(Arrays.equals(mainSigs.get(0), forkSigs.get(0)));

		// Add value to fork
		forkMap.add(name, anotherSignature);

		// Check value added correctly
		forkSigs = forkMap.get(name);
		assertTrue(ByteArrayUtils.contains(forkSigs, anotherSignature));
		assertEquals(2, forkSigs.size());

		// Check parent is different
		mainSigs = mainMap.get(name);
		assertNotEquals(mainSigs.size(), forkSigs.size());
		assertFalse(ByteArrayUtils.contains(mainSigs, anotherSignature));

		// Remove value from fork
		forkMap.remove(name, anotherSignature);

		// Check value removed correctly
		forkSigs = forkMap.get(name);
		assertFalse(ByteArrayUtils.contains(forkSigs, anotherSignature));
		assertEquals(1, forkSigs.size());

		// Check parent *contents* match (may not be same List<> reference)
		mainSigs = mainMap.get(name);
		assertEquals(mainSigs.size(), forkSigs.size());
		assertEquals(1, forkSigs.size()); // Should only be one entry
		assertTrue(ByteArrayUtils.contains(mainSigs, testSignature));
		assertTrue(ByteArrayUtils.contains(forkSigs, testSignature));
		assertTrue(Arrays.equals(mainSigs.get(0), forkSigs.get(0)));
	}

	@Test
	public void ListValueForkNonEmptyParentRecreateTest() {
		// Set up
		String name = "test";
		byte[] testSignature = new byte[] { 123 };
		byte[] anotherSignature = new byte[] { -123 };

		// Create in-memory DB
		DBSet databaseSet = DBSet.createEmptyDatabaseSet();
		OrphanNameStorageHelperMap mainMap = databaseSet.getOrphanNameStorageHelperMap();

		// We start by creating parent value

		// Add value to parent
		mainMap.add(name, testSignature);

		// Check value added correctly
		List<byte[]> mainSigs = mainMap.get(name);
		assertNotNull(mainSigs);
		assertTrue(ByteArrayUtils.contains(mainSigs, testSignature));

		// Create new fork
		DBSet fork = databaseSet.fork();
		OrphanNameStorageHelperMap forkMap = fork.getOrphanNameStorageHelperMap();

		// Check fork agrees with parent
		List<byte[]> forkSigs = forkMap.get(name);
		assertEquals(mainSigs.size(), forkSigs.size());
		assertEquals(1, forkSigs.size()); // Should only be one entry
		assertTrue(ByteArrayUtils.contains(mainSigs, testSignature));
		assertTrue(ByteArrayUtils.contains(forkSigs, testSignature));
		assertTrue(Arrays.equals(mainSigs.get(0), forkSigs.get(0)));

		// Add value to fork
		forkMap.add(name, anotherSignature);

		// Check value added correctly
		forkSigs = forkMap.get(name);
		assertTrue(ByteArrayUtils.contains(forkSigs, anotherSignature));
		assertEquals(2, forkSigs.size());

		// Check parent is different
		mainSigs = mainMap.get(name);
		assertNotEquals(mainSigs.size(), forkSigs.size());
		assertFalse(ByteArrayUtils.contains(mainSigs, anotherSignature));

		// Remove value from fork
		forkMap.remove(name, anotherSignature);

		// Check value removed correctly
		forkSigs = forkMap.get(name);
		assertFalse(ByteArrayUtils.contains(forkSigs, anotherSignature));
		assertEquals(1, forkSigs.size());

		// Check parent *contents* match (may not be same List<> reference)
		mainSigs = mainMap.get(name);
		assertEquals(mainSigs.size(), forkSigs.size());
		assertEquals(1, forkSigs.size()); // Should only be one entry
		assertTrue(ByteArrayUtils.contains(mainSigs, testSignature));
		assertTrue(ByteArrayUtils.contains(forkSigs, testSignature));
		assertTrue(Arrays.equals(mainSigs.get(0), forkSigs.get(0)));

		// Now actually delete entire list from fork (should create entry for key in "deleted" list)
		forkMap.delete(name);

		// Check list is now null
		assertNull(forkMap.get(name));

		// Re-add other value to fork
		forkMap.add(name, anotherSignature);

		// Check value added correctly
		forkSigs = forkMap.get(name);
		assertNotNull(forkSigs);
		assertEquals(1, forkSigs.size()); // Should only be one entry
		assertTrue(ByteArrayUtils.contains(forkSigs, anotherSignature));
	}
}
