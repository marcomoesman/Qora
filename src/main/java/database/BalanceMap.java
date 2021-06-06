package database;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.mapdb.BTreeKeySerializer;
import org.mapdb.BTreeMap;
import org.mapdb.Bind;
import org.mapdb.DB;
import org.mapdb.Fun;
import org.mapdb.Fun.Tuple2;
import org.mapdb.Fun.Tuple3;

import qora.account.Account;
import utils.ObserverMessage;

public final class BalanceMap extends DbMap<Tuple2<String, Long>, BigDecimal> {

	public static final long QORA_KEY = 0L;

	private final Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();

	@SuppressWarnings("rawtypes")
	private BTreeMap assetKeyMap;

	public BalanceMap(final QoraDb databaseSet, final DB database) {
		super(databaseSet, database);

		this.observableData.put(DbMap.NOTIFY_ADD, ObserverMessage.ADD_BALANCE_TYPE);
		this.observableData.put(DbMap.NOTIFY_REMOVE, ObserverMessage.REMOVE_BALANCE_TYPE);
	}

	public BalanceMap(final BalanceMap parent) {
		super(parent);
	}

	protected void createIndexes(final DB database) {
	}

	@SuppressWarnings({ "unchecked" })
	@Override
	protected Map<Tuple2<String, Long>, BigDecimal> getMap(final DB database) {
		// Open map
		final BTreeMap<Tuple2<String, Long>, BigDecimal> map = database.createTreeMap("balances")
				.keySerializer(BTreeKeySerializer.TUPLE2).counterEnable().makeOrGet();

		// Have/want key
		this.assetKeyMap = database.createTreeMap("balances_key_asset").comparator(Fun.COMPARATOR).counterEnable()
				.makeOrGet();

		// Bind asset key
		Bind.secondaryKey(map, this.assetKeyMap,
				new Fun.Function2<Tuple3<Long, BigDecimal, String>, Tuple2<String, Long>, BigDecimal>() {
					@Override
					public Tuple3<Long, BigDecimal, String> run(final Tuple2<String, Long> key,
							final BigDecimal value) {
						return new Tuple3<Long, BigDecimal, String>(key.b, value.negate(), key.a);
					}
				});
		return map;
	}

	@Override
	protected Map<Tuple2<String, Long>, BigDecimal> getMemoryMap() {
		return new TreeMap<Tuple2<String, Long>, BigDecimal>(Fun.TUPLE2_COMPARATOR);
	}

	@Override
	protected BigDecimal getDefaultValue() {
		return BigDecimal.ZERO.setScale(8);
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	public void set(final String address, final BigDecimal value) {
		this.set(address, QORA_KEY, value);
	}

	public void set(final String address, final long key, final BigDecimal value) {
		this.set(new Tuple2<String, Long>(address, key), value);
	}

	public BigDecimal get(final String address) {
		return this.get(address, QORA_KEY);
	}

	public BigDecimal get(final String address, final long key) {
		return this.get(new Tuple2<String, Long>(address, key));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public SortableList<Tuple2<String, Long>, BigDecimal> getBalancesSortableList(final long key) {
		// Filter all keys
		final Collection<Tuple2<String, Long>> keys = ((BTreeMap<Tuple3, Tuple2<String, Long>>) this.assetKeyMap)
				.subMap(Fun.t3(key, null, null), Fun.t3(key, Fun.HI(), Fun.HI())).values();
		return new SortableList<Tuple2<String, Long>, BigDecimal>(this, keys);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public SortableList<Tuple2<String, Long>, BigDecimal> getBalancesSortableList(final Account account) {
		final BTreeMap map = (BTreeMap) this.map;

		// Filter all keys
		final Collection keys = ((BTreeMap<Tuple2, BigDecimal>) map)
				.subMap(Fun.t2(account.getAddress(), null), Fun.t2(account.getAddress(), Fun.HI())).keySet();
		return new SortableList<Tuple2<String, Long>, BigDecimal>(this, keys);
	}
}
