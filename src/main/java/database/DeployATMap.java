package database;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.mapdb.BTreeKeySerializer;
import org.mapdb.DB;

import com.google.common.primitives.UnsignedBytes;

import qora.transaction.Transaction;

public class DeployATMap extends DbMap<byte[], Long> {
	private Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();

	public DeployATMap(QoraDb databaseSet, DB database) {
		super(databaseSet, database);
	}

	public DeployATMap(DeployATMap parent) {
		super(parent);
	}

	protected void createIndexes(DB database) {
	}

	@Override
	protected Map<byte[], Long> getMap(DB database) {
		// OPEN MAP
		return database.createTreeMap("DeployATOrphanData").keySerializer(BTreeKeySerializer.BASIC)
				.comparator(UnsignedBytes.lexicographicalComparator()).makeOrGet();
	}

	@Override
	protected Map<byte[], Long> getMemoryMap() {
		return new TreeMap<byte[], Long>(UnsignedBytes.lexicographicalComparator());
	}

	@Override
	protected Long getDefaultValue() {
		return -1l;
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	public Long get(Transaction transaction) {
		return this.get(transaction.getSignature());
	}

	public void set(Transaction transaction, Long key) {
		this.set(transaction.getSignature(), key);
	}

	public void delete(Transaction transaction) {
		this.delete(transaction.getSignature());
	}
}
