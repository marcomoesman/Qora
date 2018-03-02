package qora.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.mapdb.DB;
import org.mapdb.DB.BTreeMapMaker;

import database.DBMap;
import database.DBMapValueMap;
import database.DBSet;

public class NameStorageMap extends DBMapValueMap<String, String, String, Map<String, String>> {

	private Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();

	public NameStorageMap(DBSet databaseSet, DB database) {
		super(databaseSet, database);
	}

	public NameStorageMap(DBMap<String, Map<String, String>> parent) {
		super(parent);
	}

	@Override
	protected Map<String, Map<String, String>> getMap(DB database) {
		// OPEN MAP
		BTreeMapMaker createTreeMap = database.createTreeMap("NameStorageMap");
		return createTreeMap.makeOrGet();
	}

	@Override
	protected Map<String, Map<String, String>> getMemoryMap() {
		return new HashMap<String, Map<String, String>>();
	}

	@Override
	protected Map<String, String> getDefaultValue() {
		return null;
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	protected void createIndexes(DB database) {
	}

	@Override
	protected Map<String, String> newMapValue() {
		return new HashMap<String, String>();
	}

	public void add(String name, String key, String value) {
		this.mapAdd(name, key, value, (map, mapKey, mapValue) -> true, (map, mapKey, mapValue) -> map.put(mapKey, mapValue));
	}

	public void addListEntries(String name, String key, List<String> entriesToAdd) {
		Map<String, String> keyValueMap = this.getMapForAdd(name);

		String currentListAsString = keyValueMap.get(key);
		List<String> currentList = new ArrayList<String>();

		// If we have a current list (in String form) then split using ";" as delimiter
		if (currentListAsString != null)
			currentList = new ArrayList<String>(Arrays.asList(StringUtils.split(currentListAsString, ";")));

		// Add entries if they're not already in the list
		for (String entry : entriesToAdd)
			if (!currentList.contains(entry))
				currentList.add(entry);

		// Re-pack entries as a String using ";" as delimiter
		String joinedResults = StringUtils.join(currentList, ";");

		// Save into map
		keyValueMap.put(key, joinedResults);

		// Save map into DBMap
		this.set(name, keyValueMap);
	}

	public void remove(String name, String key) {
		this.mapRemove(name, key, (map, mapKey) -> true, (map, mapKey) -> map.remove(mapKey));
	}

	public void removeListEntries(String name, String key, List<String> entriesToRemove) {
		Map<String, String> keyValueMap = this.getMapForRemove(name);

		// No mapping? nothing to remove
		if (keyValueMap == null)
			return;

		String currentListAsString = keyValueMap.get(key);

		// No current list (in String form) - nothing to remove
		if (currentListAsString == null)
			return;

		// Split list using ";" as delimiter
		List<String> currentList = new ArrayList<String>(Arrays.asList(StringUtils.split(currentListAsString, ";")));

		// Remove entries from list if present
		for (String entry : entriesToRemove)
			currentList.remove(entry);

		// If list is now empty, remove the whole key-value entry from map
		if (currentList.isEmpty()) {
			keyValueMap.remove(key);
		} else {
			// Re-pack entries as a String using ";" as delimiter
			String joinedResults = StringUtils.join(currentList, ";");

			keyValueMap.put(key, joinedResults);
		}

		this.set(name, keyValueMap);
	}

	public String getOpt(String name, String key) {
		Map<String, String> keyValueMap = this.get(name);

		if (keyValueMap == null)
			return null;

		return keyValueMap.get(key);
	}
}
