package database;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import org.mapdb.BTreeKeySerializer;
import org.mapdb.BTreeMap;
import org.mapdb.Bind;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Fun;
import org.mapdb.Fun.Tuple2;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import database.serializer.TransactionSerializer;
import qora.account.Account;
import qora.transaction.ArbitraryTransaction;
import qora.transaction.GenesisTransaction;
import qora.transaction.Transaction;
import utils.BlExpUnit;

public final class TransactionFinalMap extends DbMap<Tuple2<Integer, Integer>, Transaction> {
	
	private final Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();

	@SuppressWarnings("rawtypes")
	private NavigableSet senderKey;
	@SuppressWarnings("rawtypes")
	private NavigableSet recipientKey;
	@SuppressWarnings("rawtypes")
	private NavigableSet typeKey;

	public TransactionFinalMap(final QoraDb databaseSet, final DB database) {
		super(databaseSet, database);
	}

	public TransactionFinalMap(final TransactionFinalMap parent) {
		super(parent);
	}

	protected void createIndexes(final DB database) {
	}

	@SuppressWarnings("unchecked")
	private Map<Tuple2<Integer, Integer>, Transaction> openMap(final DB database) {
		final BTreeMap<Tuple2<Integer, Integer>, Transaction> map = database.createTreeMap("height_seq_transactions")
				.keySerializer(BTreeKeySerializer.TUPLE2).valueSerializer(new TransactionSerializer()).makeOrGet();

		this.senderKey = database.createTreeSet("sender_txs").comparator(Fun.COMPARATOR).makeOrGet();

		Bind.secondaryKey(map, this.senderKey, new Fun.Function2<String, Tuple2<Integer, Integer>, Transaction>() {
			@Override
			public String run(Tuple2<Integer, Integer> key, Transaction val) {
				if (val instanceof GenesisTransaction) {
					return "genesis";
				}
				return val.getCreator().getAddress();
			}
		});

		this.recipientKey = database.createTreeSet("recipient_txs").comparator(Fun.COMPARATOR).makeOrGet();

		Bind.secondaryKeys(map, this.recipientKey,
				new Fun.Function2<String[], Tuple2<Integer, Integer>, Transaction>() {
					@Override
					public String[] run(final Tuple2<Integer, Integer> key, final Transaction val) {
						final List<String> recps = new ArrayList<String>();
						for (final Account acc : val.getRecipientAccounts()) {
							recps.add(acc.getAddress());
						}
						String[] ret = new String[recps.size()];
						ret = recps.toArray(ret);
						return ret;
					}
				});

		this.typeKey = database.createTreeSet("address_type_txs").comparator(Fun.COMPARATOR).makeOrGet();

		Bind.secondaryKeys(map, this.typeKey,
				new Fun.Function2<Tuple2<String, Integer>[], Tuple2<Integer, Integer>, Transaction>() {
					@Override
					public Tuple2<String, Integer>[] run(final Tuple2<Integer, Integer> key, final Transaction val) {
						final List<Tuple2<String, Integer>> recps = new ArrayList<Tuple2<String, Integer>>();
						final Integer type = val.getType();
						for (final Account acc : val.getInvolvedAccounts()) {
							recps.add(new Tuple2<String, Integer>(acc.getAddress(), type));

						}
						
						Tuple2<String, Integer>[] ret = (Tuple2<String, Integer>[]) Array.newInstance(Fun.Tuple2.class,
								recps.size());
						ret = recps.toArray(ret);
						return ret;
					}
				});
		return map;
	}

	@Override
	protected Map<Tuple2<Integer, Integer>, Transaction> getMap(final DB database) {
		return openMap(database);
	}

	@Override
	protected Map<Tuple2<Integer, Integer>, Transaction> getMemoryMap() {
		final DB database = DBMaker.newMemoryDB().make();
		return getMap(database);
	}

	@Override
	protected Transaction getDefaultValue() {
		return null;
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void delete(final Integer height) {
		final BTreeMap map = (BTreeMap) this.map;
		// Get all transactions belonging to address
		Collection<Tuple2> keys = ((BTreeMap<Tuple2, Transaction>) map)
				.subMap(Fun.t2(height, null), Fun.t2(height, Fun.HI())).keySet();

		// Delete transactions
		for (final Tuple2<Integer, Integer> key : keys) {
			delete(key);
		}
	}

	public void delete(final Integer height, final Integer seq) {
		delete(new Tuple2<Integer, Integer>(height, seq));
	}

	public boolean add(final Integer height, final Integer seq, final Transaction transaction) {
		return set(new Tuple2<Integer, Integer>(height, seq), transaction);
	}

	public Transaction getTransaction(final Integer height, final Integer seq) {
		final Transaction tx = this.get(new Tuple2<Integer, Integer>(height, seq));
		if (this.parent != null) {
			if (tx == null) {
				return this.parent.get(new Tuple2<Integer, Integer>(height, seq));
			}
		}
		return tx;
	}

	public List<Transaction> getTransactionsByRecipient(final String address) {
		return getTransactionsByRecipient(address, 0);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<Transaction> getTransactionsByRecipient(final String address, final int limit) {
		final Iterable keys = Fun.filter(this.recipientKey, address);
		final Iterator iter = keys.iterator();
		final List<Transaction> txs = new ArrayList<>();
		int counter = 0;
		while (iter.hasNext() && (limit == 0 || counter < limit)) {
			txs.add(this.map.get(iter.next()));
			counter++;
		}
		return txs;
	}

	public List<Transaction> getTransactionsBySender(final String address) {
		return getTransactionsBySender(address, 0);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<Transaction> getTransactionsBySender(final String address, final int limit) {
		final Iterable keys = Fun.filter(this.senderKey, address);
		final Iterator iter = keys.iterator();

		final List<Transaction> txs = new ArrayList<>();
		int counter = 0;
		while (iter.hasNext() && (limit == 0 || counter < limit)) {
			txs.add(this.map.get(iter.next()));
			counter++;
		}
		return txs;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<Transaction> getTransactionsByTypeAndAddress(final String address, final Integer type, final int limit) {
		final Iterable keys = Fun.filter(this.typeKey, new Tuple2<String, Integer>(address, type));
		final Iterator iter = keys.iterator();

		final List<Transaction> txs = new ArrayList<>();
		int counter = 0;
		while (iter.hasNext() && (limit == 0 || counter < limit)) {
			txs.add(this.map.get(iter.next()));
			counter++;
		}

		return txs;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Set<BlExpUnit> getBlExpTransactionsByAddress(final String address) {
		final Iterable senderKeys = Fun.filter(this.senderKey, address);
		final Iterable recipientKeys = Fun.filter(this.recipientKey, address);

		final Set<Tuple2<Integer, Integer>> treeKeys = new TreeSet<>();
		treeKeys.addAll(Sets.newTreeSet(senderKeys));
		treeKeys.addAll(Sets.newTreeSet(recipientKeys));

		final Iterator iter = treeKeys.iterator();
		final Set<BlExpUnit> txs = new TreeSet<>();
		while (iter.hasNext()) {
			final Tuple2<Integer, Integer> request = (Tuple2<Integer, Integer>) iter.next();
			txs.add(new BlExpUnit(request.a, request.b, this.map.get(request)));
		}
		return txs;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<Transaction> getTransactionsByAddress(final String address) {
		final Iterable senderKeys = Fun.filter(this.senderKey, address);
		final Iterable recipientKeys = Fun.filter(this.recipientKey, address);

		final Set<Tuple2<Integer, Integer>> treeKeys = new TreeSet<>();
		treeKeys.addAll(Sets.newTreeSet(senderKeys));
		treeKeys.addAll(Sets.newTreeSet(recipientKeys));

		final Iterator iter = treeKeys.iterator();
		final List<Transaction> txs = new ArrayList<>();
		while (iter.hasNext()) {
			txs.add(this.map.get(iter.next()));
		}
		return txs;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public int getTransactionsByAddressCount(final String address) {
		final Iterable senderKeys = Fun.filter(this.senderKey, address);
		final Iterable recipientKeys = Fun.filter(this.recipientKey, address);

		final Set<Tuple2<Integer, Integer>> treeKeys = new TreeSet<>();
		treeKeys.addAll(Sets.newTreeSet(senderKeys));
		treeKeys.addAll(Sets.newTreeSet(recipientKeys));
		return treeKeys.size();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Tuple2<Integer, Integer> getTransactionsAfterTimestamp(final int startHeight, int numOfTx, final String address) {
		final Iterable keys = Fun.filter(this.recipientKey, address);
		final Iterator iter = keys.iterator();
		int prevKey = startHeight;
		while (iter.hasNext()) {
			final Tuple2<Integer, Integer> key = (Tuple2<Integer, Integer>) iter.next();
			if (key.a >= startHeight) {
				if (key.a != prevKey) {
					numOfTx = 0;
				}
				
				prevKey = key.a;
				if (key.b > numOfTx) {
					return key;
				}
			}
		}
		return null;
	}

	public DbMap<Tuple2<Integer, Integer>, Transaction> getParent() {
		return this.parent;
	}

	@SuppressWarnings("rawtypes")
	public List<Transaction> findTransactions(final String address, final String sender, final String recipient, final int minHeight,
			final int maxHeight, final int type, final int service, final boolean desc, final int offset, final int limit) {
		final Iterable keys = findTransactionsKeys(address, sender, recipient, minHeight, maxHeight, type, service, desc,
				offset, limit);
		final Iterator iter = keys.iterator();
		final List<Transaction> txs = new ArrayList<>();
		while (iter.hasNext()) {
			txs.add(this.map.get(iter.next()));
		}
		return txs;
	}

	@SuppressWarnings("rawtypes")
	public int findTransactionsCount(final String address, final String sender, final String recipient, final int minHeight,
			final int maxHeight, final int type, final int service, final boolean desc, final int offset, final int limit) {
		final Iterable keys = findTransactionsKeys(address, sender, recipient, minHeight, maxHeight, type, service, desc,
				offset, limit);
		return Iterables.size(keys);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Iterable findTransactionsKeys(String address, String sender, String recipient, final int minHeight,
			final int maxHeight, int type, final int service, boolean desc, int offset, int limit) {
		Iterable senderKeys = null;
		Iterable recipientKeys = null;
		Set<Tuple2<Integer, Integer>> treeKeys = new TreeSet<>();

		if (address != null) {
			sender = address;
			recipient = address;
		}

		if (sender == null && recipient == null) {
			return treeKeys;
		}

		if (sender != null) {
			if (type > 0) {
				senderKeys = Fun.filter(this.typeKey, new Tuple2<String, Integer>(sender, type));
			} else {
				senderKeys = Fun.filter(this.senderKey, sender);
			}
		}

		if (recipient != null) {
			if (type > 0) {
				recipientKeys = Fun.filter(this.typeKey, new Tuple2<String, Integer>(recipient, type));
			} else {
				recipientKeys = Fun.filter(this.recipientKey, recipient);
			}
		}

		if (address != null) {
			treeKeys.addAll(Sets.newTreeSet(senderKeys));
			treeKeys.addAll(Sets.newTreeSet(recipientKeys));
		} else if (sender != null && recipient != null) {
			treeKeys.addAll(Sets.newTreeSet(senderKeys));
			treeKeys.retainAll(Sets.newTreeSet(recipientKeys));
		} else if (sender != null) {
			treeKeys.addAll(Sets.newTreeSet(senderKeys));
		} else if (recipient != null) {
			treeKeys.addAll(Sets.newTreeSet(recipientKeys));
		}

		if (minHeight != 0 || maxHeight != 0) {
			treeKeys = Sets.filter(treeKeys, new Predicate<Tuple2<Integer, Integer>>() {
				@Override
				public boolean apply(Tuple2<Integer, Integer> key) {
					return (minHeight == 0 || key.a >= minHeight) && (maxHeight == 0 || key.a <= maxHeight);
				}
			});
		}

		if (type == Transaction.ARBITRARY_TRANSACTION && service > -1) {
			treeKeys = Sets.filter(treeKeys, new Predicate<Tuple2<Integer, Integer>>() {
				@Override
				public boolean apply(Tuple2<Integer, Integer> key) {
					ArbitraryTransaction tx = (ArbitraryTransaction) map.get(key);
					return tx.getService() == service;
				}
			});
		}

		Iterable keys;
		if (desc) {
			keys = ((TreeSet) treeKeys).descendingSet();
		} else {
			keys = treeKeys;
		}

		limit = (limit == 0) ? Iterables.size(keys) : limit;
		return Iterables.limit(Iterables.skip(keys, offset), limit);
	}

}
