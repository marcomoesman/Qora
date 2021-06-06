package database;

import java.util.HashMap;
import java.util.Map;

import org.mapdb.DB;

import qora.account.Account;

public class ReferenceMap extends DbMap<String, byte[]> {
	
	private final Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();

	public ReferenceMap(final QoraDb databaseSet, final DB database) {
		super(databaseSet, database);
	}

	public ReferenceMap(final ReferenceMap parent) {
		super(parent);
	}

	protected void createIndexes(final DB database) {
	}

	@Override
	protected Map<String, byte[]> getMap(final DB database) {
		return database.getTreeMap("references");
	}

	@Override
	protected Map<String, byte[]> getMemoryMap() {
		return new HashMap<String, byte[]>();
	}

	@Override
	protected byte[] getDefaultValue() {
		return new byte[0];
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	public byte[] get(final Account account) {
		return get(account.getAddress());
	}

	public void set(final Account account, final byte[] reference) {
		set(account.getAddress(), reference);
	}

	public void delete(final Account account) {
		delete(account.getAddress());
	}
}
