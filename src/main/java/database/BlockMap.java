package database;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeMap;

import org.mapdb.Atomic.Var;
import org.mapdb.BTreeKeySerializer;
import org.mapdb.BTreeMap;
import org.mapdb.Bind;
import org.mapdb.DB;
import org.mapdb.Fun;
import org.mapdb.Fun.Tuple2;
import org.mapdb.Fun.Tuple2Comparator;

import com.google.common.primitives.UnsignedBytes;

import database.serializer.BlockSerializer;
import qora.block.Block;
import utils.Converter;
import utils.ObserverMessage;
import utils.ReverseComparator;

public final class BlockMap extends DbMap<byte[], Block> {
	
	public static final int HEIGHT_INDEX = 1;

	private final Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();

	private Var<byte[]> lastBlockVar;
	private byte[] lastBlockSignature;

	private Var<Boolean> processingVar;
	private Boolean processing;

	private BTreeMap<Tuple2<String, String>, byte[]> generatorMap;

	public BlockMap(final QoraDb databaseSet, final DB database) {
		super(databaseSet, database);

		this.observableData.put(DbMap.NOTIFY_ADD, ObserverMessage.ADD_BLOCK_TYPE);
		this.observableData.put(DbMap.NOTIFY_REMOVE, ObserverMessage.REMOVE_BLOCK_TYPE);
		this.observableData.put(DbMap.NOTIFY_LIST, ObserverMessage.LIST_BLOCK_TYPE);

		// Last block
		/*
		 * If lastBlock doesn't exist, explicitly set to 0-length byte[] otherwise
		 * getAtomicVar() will return "" which is the wrong type
		 */
		if (!database.exists("lastBlock")) {
			database.createAtomicVar("lastBlock", new byte[0], null);
		}

		this.lastBlockVar = database.getAtomicVar("lastBlock");
		this.lastBlockSignature = this.lastBlockVar.get();

		// 0-length byte[] effectively means no signature
		if (this.lastBlockSignature.length == 0) {
			this.lastBlockSignature = null;
		}

		// Processing state
		if (!database.exists("processingBlock")) {
			database.createAtomicVar("processingBlock", false, null);
		}
		this.processingVar = database.getAtomicVar("processingBlock");
		this.processing = this.processingVar.get();
	}

	public BlockMap(final BlockMap parent) {
		super(parent);

		this.lastBlockSignature = parent.getLastBlockSignature();
		this.processing = parent.isProcessing();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void createIndexes(final DB database) {
		// Height index
		final Tuple2Comparator<Integer, byte[]> comparator = new Fun.Tuple2Comparator<Integer, byte[]>(Fun.COMPARATOR,
				UnsignedBytes.lexicographicalComparator());
		final NavigableSet<Tuple2<Integer, byte[]>> heightIndex = database.createTreeSet("blocks_index_height")
				.comparator(comparator).makeOrGet();

		final NavigableSet<Tuple2<Integer, byte[]>> descendingHeightIndex = database
				.createTreeSet("blocks_index_height_descending").comparator(new ReverseComparator(comparator))
				.makeOrGet();

		createIndex(HEIGHT_INDEX, heightIndex, descendingHeightIndex, new Fun.Function2<Integer, byte[], Block>() {
			@Override
			public Integer run(final byte[] key, final Block value) {
				return value.getHeight();
			}
		});

		this.generatorMap = database.createTreeMap("generators_index").makeOrGet();

		Bind.secondaryKey((BTreeMap) this.map, generatorMap,
				new Fun.Function2<Tuple2<String, String>, byte[], Block>() {
					@Override
					public Tuple2<String, String> run(final byte[] b, final Block block) {
						return new Tuple2<String, String>(block.getGenerator().getAddress(),
								Converter.toHex(block.getSignature()));
					}
				});
	}

	@Override
	protected Map<byte[], Block> getMap(final DB database) {
		return database.createTreeMap("blocks").keySerializer(BTreeKeySerializer.BASIC)
				.comparator(UnsignedBytes.lexicographicalComparator()).valueSerializer(new BlockSerializer())
				.valuesOutsideNodesEnable().counterEnable().makeOrGet();
	}

	@Override
	protected Map<byte[], Block> getMemoryMap() {
		return new TreeMap<byte[], Block>(UnsignedBytes.lexicographicalComparator());
	}

	@Override
	protected Block getDefaultValue() {
		return null;
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	public void setLastBlock(final Block block) {
		if (this.lastBlockVar != null) {
			this.lastBlockVar.set(block.getSignature());
		}
		this.lastBlockSignature = block.getSignature();
	}

	public Block getLastBlock() {
		return get(this.getLastBlockSignature());
	}

	public byte[] getLastBlockSignature() {
		return this.lastBlockSignature;
	}

	public boolean isProcessing() {
		if (this.processing != null) {
			return this.processing.booleanValue();
		}
		return false;
	}

	public void setProcessing(final boolean processing) {
		if (this.processingVar != null) {
			this.processingVar.set(processing);
		}
		this.processing = processing;
	}

	public void add(final Block block) {
		set(block.getSignature(), block);
	}

	public void delete(final Block block) {
		delete(block.getSignature());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Collection<byte[]> getGeneratorBlocks(final String address) {
		final Collection<byte[]> blocks = ((BTreeMap) (this.generatorMap))
				.subMap(Fun.t2(address, null), Fun.t2(address, Fun.HI())).values();
		return blocks;
	}

}
