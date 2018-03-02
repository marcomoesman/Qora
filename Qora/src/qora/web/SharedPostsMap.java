package qora.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mapdb.DB;

import com.google.common.primitives.SignedBytes;

import database.DBListValueMap;
import database.DBMap;
import database.DBSet;

public class SharedPostsMap extends DBListValueMap<byte[], String, List<String>> {

	private Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();

	public SharedPostsMap(DBSet databaseSet, DB database) {
		super(databaseSet, database);
	}

	public SharedPostsMap(DBMap<byte[], List<String>> parent) {
		super(parent);
	}

	@Override
	protected Map<byte[], List<String>> getMap(DB database) {

		return database.createTreeMap("SharedPostsMap").comparator(SignedBytes.lexicographicalComparator()).makeOrGet();

	}

	@Override
	protected Map<byte[], List<String>> getMemoryMap() {
		return new HashMap<>();
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	@Override
	protected void createIndexes(DB database) {
	}

	@Override
	protected List<String> getDefaultValue() {
		return null;
	}

	@Override
	protected List<String> newListValue() {
		return new ArrayList<String>();
	}

	public void add(byte[] postSignature, String name) {
		// Add name to list if list doesn't already contain it
		this.listAdd(postSignature, name, (list, entry) -> !list.contains(entry), (list, entry) -> list.add(entry));
	}

	public void remove(byte[] postSignature, String name) {
		// Always remove name from list
		this.listRemove(postSignature, name, (list, entry) -> true, (list, entry) -> list.remove(entry));
	}
}
