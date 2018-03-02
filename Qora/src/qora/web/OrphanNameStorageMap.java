package qora.web;

import java.util.HashMap;
import java.util.Map;

import org.mapdb.DB;

import com.google.common.primitives.SignedBytes;

import database.DBMap;
import database.DBMapValueMap;
import database.DBSet;

public class OrphanNameStorageMap extends DBMapValueMap<byte[], String, String, Map<String, String>> {
	private Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();

	public OrphanNameStorageMap(DBSet databaseSet, DB database) {
		super(databaseSet, database);
	}

	public OrphanNameStorageMap(DBMap<byte[], Map<String, String>> parent) {
		super(parent);
	}

	@Override
	protected Map<byte[], Map<String, String>> getMap(DB database) {
		return database.createTreeMap("OrphanNameStorageMap").comparator(SignedBytes.lexicographicalComparator()).makeOrGet();
	}

	@Override
	protected Map<byte[], Map<String, String>> getMemoryMap() {
		return new HashMap<byte[], Map<String, String>>();
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	@Override
	protected void createIndexes(DB database) {
	}

	@Override
	protected Map<String, String> getDefaultValue() {
		return null;
	}

	@Override
	protected Map<String, String> newMapValue() {
		return new HashMap<String, String>();
	}

	public void add(byte[] txAndName, String key, String value) {
		this.mapAdd(txAndName, key, value, (map, mapKey, mapValue) -> true, (map, mapKey, mapValue) -> map.put(mapKey, mapValue));
	}

	public void remove(byte[] txAndName, String key) {
		this.mapRemove(txAndName, key, (map, mapKey) -> true, (map, mapKey) -> map.remove(mapKey));
	}
}
