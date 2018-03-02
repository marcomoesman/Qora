package test;

import org.junit.Assert;
import org.junit.Test;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import database.NameMap;
import database.SortableList;
import qora.account.Account;
import qora.naming.Name;

public class DatabaseIndexTests {

	@Test
	public void databaseFork() {
		// Create temporary DB file
		DB database = DBMaker.newTempFileDB().make();

		// Create name map
		NameMap nameDB = new NameMap(null, database);

		// Create test names
		Name nameA = new Name(new Account("AccountA"), "NameA", "ValueA");
		Name nameB = new Name(new Account("AccountB"), "NameB", "ValueB");

		// Add names to name map
		nameDB.set("KeyA", nameA);
		nameDB.set("KeyB", nameB);

		// Check names were added successfully
		Assert.assertEquals("ValueA", nameDB.get("KeyA").getValue());
		Assert.assertEquals("ValueB", nameDB.get("KeyB").getValue());

		// Get list of key-Name pairs
		SortableList<String, Name> list = new SortableList<String, Name>(nameDB);

		// Check various keys/names/values using default indexing
		Assert.assertEquals("KeyA", list.get(0).getA());
		Assert.assertEquals("KeyB", list.get(1).getA());
		Assert.assertEquals("ValueA", list.get(0).getB().getValue());

		// Check various keys/names/values using ??? indexing
		// XXX: NameMap has no extra indexes beyond the default supplied by DBMap
		// NOP: list.sort(NameMap.DEFAULT_INDEX);
		Assert.assertEquals("KeyA", list.get(0).getA());
		Assert.assertEquals("KeyB", list.get(1).getA());
		Assert.assertEquals("ValueA", list.get(0).getB().getValue());
	}
}
