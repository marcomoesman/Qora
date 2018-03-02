package database;

import java.util.List;

import org.mapdb.DB;

public abstract class DBListValueMap<K, E, L extends List<E>> extends DBMap<K, L> {
	public DBListValueMap(IDB databaseSet, DB database) {
		super(databaseSet, database);
	}

	public DBListValueMap(DBMap<K, L> parent) {
		super(parent);
	}

	protected abstract L newListValue();

	protected interface ListEntryFilter<L, E> {
		boolean test(L list, E entry);
	}

	protected interface ListEntryFunction<L, E> {
		void apply(L list, E entry);
	}

	/**
	 * Add <code>entry</code> to list stored in map under <code>key</code>.
	 * <p>
	 * This method is for DBMaps where the value type is a List and called to prevent reuse of a parent map's List in a fork.
	 * <p>
	 * For maps without a parent, this is trivial: either use current list or create new one. <br>
	 * For maps with a parent, we may need to duplicate the parent's list.
	 * <p>
	 * Calls DBMap.set() before returning.
	 * 
	 * @param key
	 * @param entry
	 * @param addFilter
	 *            - return true if entry should be added to list
	 * @param addFunction
	 *            - function to add entry to list
	 * 
	 * @see getListForAdd
	 */
	protected void listAdd(K key, E entry, ListEntryFilter<L, E> addFilter, ListEntryFunction<L, E> addFunction) {
		L list = this.getListForAdd(key);

		if (addFilter.test(list, entry))
			addFunction.apply(list, entry);

		// Always called so observers are notified
		this.set(key, list);
	}

	/**
	 * Create/return <code>List</code> for key, prior to adding an entry to list.
	 * <p>
	 * This method is for DBMaps where the value type is a List and called to prevent reuse of a parent map's List in a fork.
	 * <p>
	 * Caller is definitely going to add an entry to the List-value, so ensure caller uses the correct List reference.
	 * <p>
	 * For maps without a parent, this is trivial: either return current List or create new one. <br>
	 * For maps with a parent, we may need to duplicate the parent's list.
	 * <p>
	 * Caller is expected to call DBMap.set() soon after return.
	 * 
	 * @param key
	 * @return list
	 * 
	 * @see listAdd
	 */
	protected L getListForAdd(K key) {
		try {
			// Trivial case: if our map contains a list for key, return it
			if (this.map.containsKey(key))
				return this.map.get(key);

			L newList = this.newListValue();

			// If we previous marked entry as deleted then we're essentially recreating a new list
			if (this.deleted != null && this.deleted.contains(key))
				return newList;

			// If the parent map contains a list for key then we need to duplicate its entries
			if (this.parent != null) {
				L parentList = this.parent.get(key);

				if (parentList != null)
					newList.addAll(parentList);
			}

			return newList;
		} catch (Exception e) {
			// This is bad news
			throw new RuntimeException("getListForAdd");
		}
	}

	/**
	 * Remove <code>entry</code> from list stored in map under <code>key</code>.
	 * <p>
	 * This method is for DBMaps where the value type is a List and called to prevent reuse of a parent map's List in a fork.
	 * <p>
	 * For maps without a parent, this is trivial: either return current List or do nothing. <br>
	 * For maps with a parent, we may need to duplicate the parent's list.
	 * <p>
	 * Calls DBMap.set() before returning.
	 * 
	 * @param key
	 * @param entry
	 * @param addFilter
	 *            - return true if entry should be added to list
	 * @param addFunction
	 *            - function to add entry to list
	 */
	protected void listRemove(K key, E entry, ListEntryFilter<L, E> removeFilter, ListEntryFunction<L, E> removeFunction) {
		L list = this.getListForRemove(key);

		if (list == null)
			return;

		if (removeFilter.test(list, entry))
			removeFunction.apply(list, entry);

		// Always called so observers are notified
		this.set(key, list);
	}

	/**
	 * Create/return <code>List</code> for key, prior to removing an entry from list.
	 * <p>
	 * This method is for DBMaps where the value type is a List and called to prevent reuse of a parent map's List in a fork.
	 * <p>
	 * Caller will remove an entry from the List-value if not null, so ensure caller uses the correct List reference.
	 * <p>
	 * For maps without a parent, this is trivial: either return current List or return null. <br>
	 * For maps with a parent, we may need to duplicate the parent's list.
	 * <p>
	 * Caller is expected to call DBMap.set() soon after return.
	 * 
	 * @param key
	 * @return List<E> or null
	 */
	protected L getListForRemove(K key) {
		try {
			// Trivial case: if our map contains a list for key, return it
			if (this.map.containsKey(key))
				return this.map.get(key);

			// If we previously marked entry as deleted then we have no list
			if (this.deleted != null && this.deleted.contains(key))
				return null;

			// If we have no parent or parent has no entry then there's no list
			if (this.parent == null || !this.parent.contains(key))
				return null;

			// Parent map contains a list for key then we need to duplicate its entries
			L parentList = this.parent.get(key);

			L newList = this.newListValue();
			newList.addAll(parentList);

			return newList;
		} catch (Exception e) {
			// This is bad news
			throw new RuntimeException("getListForRemove");
		}
	}

}
