package database;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.mapdb.BTreeKeySerializer;
import org.mapdb.DB;

import com.google.common.primitives.UnsignedBytes;

import qora.block.Block;

public final class ChildMap extends DbMap<byte[], byte[]> {
	
	private final Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();
	private final BlockMap blockMap;

	public ChildMap(final QoraDb databaseSet, final DB database) {
		super(databaseSet, database);
		this.blockMap = databaseSet.getBlockMap();
	}

	public ChildMap(final BlockMap blockMap, final ChildMap parent) {
		super(parent);
		this.blockMap = blockMap;
	}

	protected void createIndexes(final DB database) {
	}

	@Override
	protected Map<byte[], byte[]> getMap(final DB database) {
		return database.createTreeMap("children").keySerializer(BTreeKeySerializer.BASIC)
				.comparator(UnsignedBytes.lexicographicalComparator()).makeOrGet();
	}

	@Override
	protected Map<byte[], byte[]> getMemoryMap() {
		return new TreeMap<byte[], byte[]>(UnsignedBytes.lexicographicalComparator());
	}

	@Override
	protected byte[] getDefaultValue() {
		return null;
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	public Block get(final Block parent) {
		if (contains(parent.getSignature())) {
			return this.blockMap.get(get(parent.getSignature()));
		}
		return null;
	}

	public void set(final Block parent, final Block child) {
		set(parent.getSignature(), child.getSignature());
	}
}
