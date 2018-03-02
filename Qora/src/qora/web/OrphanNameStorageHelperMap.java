package qora.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mapdb.DB;

import utils.ByteArrayUtils;
import database.DBListValueMap;
import database.DBMap;
import database.DBSet;

public class OrphanNameStorageHelperMap extends DBListValueMap<String, byte[], List<byte[]>> {

	private Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();

	public OrphanNameStorageHelperMap(DBSet databaseSet, DB database) {
		super(databaseSet, database);
	}

	public OrphanNameStorageHelperMap(DBMap<String, List<byte[]>> parent) {
		super(parent);
	}

	@Override
	protected Map<String, List<byte[]>> getMap(DB database) {
		return database.createTreeMap("OrphanNameStorageHelperMap").makeOrGet();
	}

	@Override
	protected Map<String, List<byte[]>> getMemoryMap() {
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
	protected List<byte[]> getDefaultValue() {
		return null;
	}

	@Override
	protected List<byte[]> newListValue() {
		return new ArrayList<byte[]>();
	}

	public void add(String name, byte[] signatureOfTx) {
		// Add signature to list if list doesn't already contain it
		this.listAdd(name, signatureOfTx, (list, sig) -> !ByteArrayUtils.contains(list, sig), (list, sig) -> list.add(sig));
	}

	public void remove(String name, byte[] signatureOfTx) {
		// Always remove signature from list
		this.listRemove(name, signatureOfTx, (list, sig) -> true, (list, sig) -> ByteArrayUtils.remove(list, sig));
	}
}