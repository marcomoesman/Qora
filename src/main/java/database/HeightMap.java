package database;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.mapdb.BTreeKeySerializer;
import org.mapdb.BTreeMap;
import org.mapdb.Bind;
import org.mapdb.DB;
import org.mapdb.Fun;

import qora.block.Block;

import com.google.common.primitives.UnsignedBytes;

public class HeightMap extends DbMap<byte[], Integer> {
	private final Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();

	private Map<Integer, byte[]> heightIndex;

	public HeightMap(final QoraDb databaseSet, final DB database) {
		super(databaseSet, database);
	}

	public HeightMap(final HeightMap parent) {
		super(parent);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void createIndexes(final DB database) {
		this.heightIndex = database.createTreeMap("block_height_index").makeOrGet();

		Bind.secondaryKey((BTreeMap) this.map, this.heightIndex, new Fun.Function2<Integer, byte[], Integer>() {
			@Override
			public Integer run(final byte[] bytes, final Integer height) {
				return height;
			}
		});
	}

	@Override
	protected Map<byte[], Integer> getMap(final DB database) {
		return database.createTreeMap("height").keySerializer(BTreeKeySerializer.BASIC)
				.comparator(UnsignedBytes.lexicographicalComparator()).makeOrGet();
	}

	@Override
	protected Map<byte[], Integer> getMemoryMap() {
		return new TreeMap<byte[], Integer>(UnsignedBytes.lexicographicalComparator());
	}

	@Override
	protected Integer getDefaultValue() {
		return -1;
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	public int get(final Block block) {
		return get(block.getSignature());
	}

	public byte[] getBlockByHeight(final int height) {
		return this.heightIndex.get(height);
	}

	public void set(final Block block, final int height) {
		set(block.getSignature(), height);
	}
}