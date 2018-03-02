package database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mapdb.DB;
import org.mapdb.DB.BTreeMapMaker;

import utils.ByteArrayUtils;

public class HashtagPostMap extends DBListValueMap<String, byte[], List<byte[]>> {
	private Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();
	
	public HashtagPostMap(DBSet databaseSet, DB database) {
		super(databaseSet, database);
	}
	
	public HashtagPostMap(DBMap<String, List<byte[]>> parent) {
		super(parent);
	}

	@Override
	protected Map<String, List<byte[]>> getMap(DB database) {
		// Open map
		BTreeMapMaker createTreeMap = database.createTreeMap("HashtagPostMap");
		return createTreeMap.makeOrGet();
	}

	@Override
	protected Map<String, List<byte[]>> getMemoryMap() {
		return new HashMap<>();
	}

	@Override
	protected List<byte[]> getDefaultValue() {
		return null;
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	@Override
	protected void createIndexes(DB database) {
	}

	@Override
	protected List<byte[]> newListValue() {
		return new ArrayList<byte[]>();
	}

	public void add(String hashtag, byte[] signature) {
		// No difference between lower and uppercase here
		hashtag = hashtag.toLowerCase();

		// Add signature to list if list doesn't already contain it
		this.listAdd(hashtag, signature, (list, sig) -> !ByteArrayUtils.contains(list, sig), (list, sig) -> list.add(sig));
	}

	public void remove(String hashtag, byte[] signature) {
		// No difference between lower and uppercase here
		hashtag = hashtag.toLowerCase();

		// Always remove signature from list
		this.listRemove(hashtag, signature, (list, sig) -> true, (list, sig) -> ByteArrayUtils.remove(list, sig));
	}
}