package database;

import java.util.Map;

import org.mapdb.DB;

public abstract class DBMapValueMap<K, X, Y, M extends Map<X, Y>> extends DBMap<K, M> {
	public DBMapValueMap(IDB databaseSet, DB database) {
		super(databaseSet, database);
	}

	public DBMapValueMap(DBMap<K, M> parent) {
		super(parent);
	}

	protected abstract M newMapValue();

	protected interface MapAddFilter<M, X, Y> {
		boolean test(M map, X mapKey, Y mapValue);
	}

	protected interface MapAddFunction<M, X, Y> {
		void apply(M map, X mapKey, Y mapValue);
	}

	protected interface MapRemoveFilter<M, X> {
		boolean test(M map, X mapKey);
	}

	protected interface MapRemoveFunction<M, X> {
		void apply(M map, X mapKey);
	}

	/**
	 * Add <code>mapKey</code>-<code>mapValue</code> pair to map stored in DBMap under <code>dbKey</code>.
	 * <p>
	 * This method is for DBMaps where the value type is a Map and called to prevent reuse of a parent DBMap's Map in a fork.
	 * <p>
	 * For DBMaps without a parent, this is trivial: either use current map or create new one. <br>
	 * For DBMaps with a parent, we may need to duplicate the parent's map.
	 * <p>
	 * Calls DBMap.set() before returning.
	 * 
	 * @param dbKey
	 *            - key in database
	 * @param mapKey
	 *            - key in map
	 * @param mapValue
	 *            - value in map
	 * 
	 * @see getListForAdd
	 */
	protected void mapAdd(K dbKey, X mapKey, Y mapValue, MapAddFilter<M, X, Y> addFilter, MapAddFunction<M, X, Y> addFunction) {
		M map = this.getMapForAdd(dbKey);

		if (addFilter.test(map, mapKey, mapValue))
			addFunction.apply(map, mapKey, mapValue);

		// Always called so observers are notified
		this.set(dbKey, map);
	}

	/**
	 * Create/return <code>Map</code> for dbKey, prior to adding a key-value pair to map.
	 * <p>
	 * This method is for DBMaps where the value type is a Map and called to prevent reuse of a parent DBMap's Map in a fork.
	 * <p>
	 * Caller is definitely going to add a key-value pair to the Map, so ensure caller uses the correct Map reference.
	 * <p>
	 * For DBMaps without a parent, this is trivial: either return current map or create new one. <br>
	 * For DBMaps with a parent, we may need to duplicate the parent's map.
	 * <p>
	 * Caller is expected to call DBMap.set() soon after return.
	 * 
	 * @param dbKey
	 * @return map
	 * 
	 * @see mapAdd
	 */
	protected M getMapForAdd(K dbKey) {
		try {
			// Trivial case: if our DBMap contains a map for dbKey, return it
			if (this.map.containsKey(dbKey))
				return this.map.get(dbKey);

			M newMap = this.newMapValue();

			// If we previous marked entry as deleted then we're essentially recreating a new map
			if (this.deleted != null && this.deleted.contains(dbKey))
				return newMap;

			// If the parent DBMap contains a map for dbKey then we need to duplicate its entries
			if (this.parent != null) {
				M parentMap = this.parent.get(dbKey);

				if (parentMap != null)
					newMap.putAll(parentMap);
			}

			return newMap;
		} catch (Exception e) {
			// This is bad news
			throw new RuntimeException("getMapForAdd");
		}
	}

	/**
	 * Remove <code>mapKey</code> pair from map stored in DBMap under <code>dbKey</code>.
	 * <p>
	 * This method is for DBMaps where the value type is a Map and called to prevent reuse of a parent DBMap's Map in a fork.
	 * <p>
	 * For DBMaps without a parent, this is trivial: either return current map or create one. <br>
	 * For DBMaps with a parent, we may need to duplicate the parent's map.
	 * <p>
	 * Calls DBMap.set() before returning.
	 * 
	 * @param dbKey
	 * @param mapKey
	 * @param addFilter
	 *            - return true if entry should be added to list
	 * @param addFunction
	 *            - function to add entry to list
	 */
	protected void mapRemove(K dbKey, X mapKey, MapRemoveFilter<M, X> removeFilter, MapRemoveFunction<M, X> removeFunction) {
		M map = this.getMapForRemove(dbKey);

		if (map == null)
			return;

		if (removeFilter.test(map, mapKey))
			removeFunction.apply(map, mapKey);

		// Always called so observers are notified
		this.set(dbKey, map);
	}

	/**
	 * Create/return <code>Map</code> for dbKey, prior to removing a key-value pair from map.
	 * <p>
	 * This method is for DBMaps where the value type is a Map and called to prevent reuse of a parent DBMap's map in a fork.
	 * <p>
	 * Caller will remove a key-value pair from the map if not null, so ensure caller uses the correct Map reference.
	 * <p>
	 * For DBMaps without a parent, this is trivial: either return current Map or return null. <br>
	 * For DBMaps with a parent, we may need to duplicate the parent's map.
	 * <p>
	 * Caller is expected to call DBMap.set() soon after return.
	 * 
	 * @param dbKey
	 * @return map or null
	 */
	protected M getMapForRemove(K dbKey) {
		try {
			// Trivial case: if our DBMap contains a map for dbKey, return it
			if (this.map.containsKey(dbKey))
				return this.map.get(dbKey);

			// If we previously marked entry as deleted then we have no map
			if (this.deleted != null && this.deleted.contains(dbKey))
				return null;

			// If we have no parent or parent has no entry then there's no map
			if (this.parent == null || !this.parent.contains(dbKey))
				return null;

			// Parent DBMap contains a map for dbKey then we need to duplicate its entries
			M parentMap = this.parent.get(dbKey);

			M newMap = this.newMapValue();
			newMap.putAll(parentMap);

			return newMap;
		} catch (Exception e) {
			// This is bad news
			throw new RuntimeException("getMapForRemove");
		}
	}

}
