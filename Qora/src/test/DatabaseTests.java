package test;

import static org.junit.Assert.*;

import java.math.BigDecimal;

import org.junit.Test;

import database.DBSet;

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
}
