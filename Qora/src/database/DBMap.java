package database;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mapdb.BTreeMap;
import org.mapdb.Bind;
import org.mapdb.DB;
import org.mapdb.Fun.Function2;
import org.mapdb.Fun.Tuple2;

import controller.Controller;
import database.wallet.WalletDatabase;
import utils.ObserverMessage;

public abstract class DBMap<T, U> extends Observable {

	protected static final int NOTIFY_ADD = 1;
	protected static final int NOTIFY_REMOVE = 2;
	protected static final int NOTIFY_LIST = 3;

	private static final Logger LOGGER = LogManager.getLogger(DBMap.class);
	public static final int DEFAULT_INDEX = 0;
	protected static final int DESCENDING_INDEX_OFFSET = 10000;

	protected DBMap<T, U> parent;
	protected IDB databaseSet;
	protected Map<T, U> map;
	protected List<T> deleted;
	private Map<Integer, NavigableSet<Tuple2<?, T>>> indexes;

	public DBMap(IDB databaseSet, DB database) {
		this.databaseSet = databaseSet;

		// OPEN MAP
		this.map = this.getMap(database);

		// CREATE INDEXES
		this.indexes = new HashMap<Integer, NavigableSet<Tuple2<?, T>>>();
		this.createIndexes(database);
	}

	public DBMap(DBMap<T, U> parent) {
		this.parent = parent;

		// OPEN MAP
		this.map = this.getMemoryMap();
		this.deleted = new ArrayList<T>();
	}

	protected abstract Map<T, U> getMap(DB database);

	protected abstract Map<T, U> getMemoryMap();

	protected abstract U getDefaultValue();

	protected abstract Map<Integer, Integer> getObservableData();

	protected abstract void createIndexes(DB database);

	@SuppressWarnings("unchecked")
	protected <V> void createIndex(int index, NavigableSet<?> indexSet, NavigableSet<?> descendingIndexSet, Function2<V, T, U> function) {
		Bind.secondaryKey((BTreeMap<T, U>) this.map, (NavigableSet<Tuple2<V, T>>) indexSet, function);
		this.indexes.put(index, (NavigableSet<Tuple2<?, T>>) indexSet);

		Bind.secondaryKey((BTreeMap<T, U>) this.map, (NavigableSet<Tuple2<V, T>>) descendingIndexSet, function);
		this.indexes.put(index + DESCENDING_INDEX_OFFSET, (NavigableSet<Tuple2<?, T>>) descendingIndexSet);
	}

	@SuppressWarnings("unchecked")
	protected <V> void createIndexes(int index, NavigableSet<?> indexSet, NavigableSet<?> descendingIndexSet, Function2<V[], T, U> function) {
		Bind.secondaryKeys((BTreeMap<T, U>) this.map, (NavigableSet<Tuple2<V, T>>) indexSet, function);
		this.indexes.put(index, (NavigableSet<Tuple2<?, T>>) indexSet);

		Bind.secondaryKeys((BTreeMap<T, U>) this.map, (NavigableSet<Tuple2<V, T>>) descendingIndexSet, function);
		this.indexes.put(index + DESCENDING_INDEX_OFFSET, (NavigableSet<Tuple2<?, T>>) descendingIndexSet);
	}

	public int size() {
		return this.map.size();
	}

	public U get(T key) {
		try {
			// Trivial case: if our map contains an entry for key, return it
			if (this.map.containsKey(key))
				return this.map.get(key);

			// If we've deleted the entry for key, return default value
			if (this.deleted != null && this.deleted.contains(key))
				return this.getDefaultValue();

			// We don't have any entry for key (deleted or otherwise) so defer to parent (if present)
			if (this.parent != null)
				return this.parent.get(key);

			// No entry for this key anywhere so return default value
			return this.getDefaultValue();
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);

			return this.getDefaultValue();
		}
	}

	public boolean set(T key, U value) {
		try {
			// First set new value in our map, retrieving old value (if any)
			U old = this.map.put(key, value);

			// If appropriate, remove this key from list of deleted keys
			if (this.deleted != null)
				this.deleted.remove(key);

			// If this map is backed by files, commit transaction now
			if (this.databaseSet != null) {
				// Only commit if this map is not the wallet DB and we're not synchronizing the wallet
				if (!(this.databaseSet instanceof WalletDatabase && Controller.getInstance().isProcessingWalletSynchronize()))
					this.databaseSet.commit();
			}

			// Notify observers that we've added an entry
			if (this.getObservableData().containsKey(NOTIFY_ADD)) {
				this.setChanged();

				// Observers that want to be notified of new Automated Transactions get sent a key-value tuple
				if (this.getObservableData().get(NOTIFY_ADD).equals(ObserverMessage.ADD_AT_TX_TYPE))
					this.notifyObservers(new ObserverMessage(this.getObservableData().get(NOTIFY_ADD), new Tuple2<T, U>(key, value)));
				else
					this.notifyObservers(new ObserverMessage(this.getObservableData().get(NOTIFY_ADD), value));
			}

			// Notify observers of the updated list of entries
			if (this.getObservableData().containsKey(NOTIFY_LIST)) {
				this.setChanged();
				this.notifyObservers(new ObserverMessage(this.getObservableData().get(NOTIFY_LIST), new SortableList<T, U>(this)));
			}

			// Did we actually *change* the value?
			return old != null;
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);

			// Suggest to caller that nothing changed
			return false;
		}
	}

	public void delete(T key) {
		try {
			// If we have an entry for this key then remove it (and notify observers)
			if (this.map.containsKey(key)) {
				// Remove the entry, keeping a copy of old value for observers
				U value = this.map.remove(key);

				// Notify observers that we've removed an entry
				if (this.getObservableData().containsKey(NOTIFY_REMOVE)) {
					this.setChanged();

					// Observers that want to be notified of removed Automated Transactions get sent a key-value tuple
					if (this.getObservableData().get(NOTIFY_REMOVE).equals(ObserverMessage.REMOVE_AT_TX))
						this.notifyObservers(new ObserverMessage(this.getObservableData().get(NOTIFY_REMOVE), new Tuple2<T, U>(key, value)));
					else
						this.notifyObservers(new ObserverMessage(this.getObservableData().get(NOTIFY_REMOVE), value));
				}

				// Notify observers of the updated list of entries
				// Currently disabled for as-yet unknown reasons!
				/*
				 * if (this.getObservableData().containsKey(NOTIFY_LIST)) { this.setChanged(); this.notifyObservers(new
				 * ObserverMessage(this.getObservableData().get(NOTIFY_LIST), new SortableList<T, U>(this))); }
				 */
			}

			// If we cache which entries have been deleted, add this key as deleted
			if (this.deleted != null)
				this.deleted.add(key);

			// If this map is backed by files, commit this transaction now
			if (this.databaseSet != null)
				this.databaseSet.commit();
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public boolean contains(T key) {
		// Trivial check first: does our map contains an entry for key?
		if (this.map.containsKey(key))
			return true;

		// Have we deleted the entry for the key?
		if (this.deleted != null && this.deleted.contains(key))
			return false; // deleted, so not present

		// Defer to parent map (if present)
		if (this.parent != null)
			return this.parent.contains(key);

		// Key not found
		return false;
	}

	@Override
	public void addObserver(Observer o) {
		// ADD OBSERVER
		super.addObserver(o);

		// NOTIFY LIST
		if (this.getObservableData().containsKey(NOTIFY_LIST)) {
			// CREATE LIST
			SortableList<T, U> list = new SortableList<T, U>(this);

			// UPDATE
			o.update(null, new ObserverMessage(this.getObservableData().get(NOTIFY_LIST), list));
		}
	}

	public Iterator<T> getIterator(int index, boolean descending) {
		if (index == DEFAULT_INDEX) {
			if (descending)
				return ((NavigableMap<T, U>) this.map).descendingKeySet().iterator();
			else
				return ((NavigableMap<T, U>) this.map).keySet().iterator();
		} else {
			if (descending)
				index += DESCENDING_INDEX_OFFSET;

			return new IndexIterator<T>(this.indexes.get(index));
		}
	}

	public SortableList<T, U> getList() {
		return new SortableList<T, U>(this);
	}

	public SortableList<T, U> getParentList() {
		// Do we even have a parent map?
		if (this.parent == null)
			return null;

		return new SortableList<T, U>(this.parent);
	}

	public Set<T> getKeys() {
		return this.map.keySet();
	}

	public Collection<U> getValues() {
		return this.map.values();
	}

	public void reset() {
		// RESET MAP
		this.map.clear();

		// RESET INDEXES
		for (Set<Tuple2<?, T>> set : this.indexes.values()) {
			set.clear();
		}

		// NOTIFY LIST
		if (this.getObservableData().containsKey(NOTIFY_LIST)) {
			// CREATE LIST
			SortableList<T, U> list = new SortableList<T, U>(this);

			// UPDATE
			this.setChanged();
			this.notifyObservers(new ObserverMessage(this.getObservableData().get(NOTIFY_LIST), list));
		}
	}
}
