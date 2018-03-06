package database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mapdb.DB;

import utils.ByteArrayUtils;

// Key: Account address, e.g. QgcphUTiVHHfHg8e1LVgg5jujVES7ZDUTr
// Value: List of transactions that set account info
public class AccountInfoHelperMap extends DBListValueMap<String, byte[], List<byte[]>> {
	private Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();

	public AccountInfoHelperMap(DBSet databaseSet, DB database) {
		super(databaseSet, database);
	}

	public AccountInfoHelperMap(AccountInfoHelperMap parent) {
		super(parent);
	}

	protected void createIndexes(DB database) {
	}

	@Override
	protected Map<String, List<byte[]>> getMap(DB database) {
		return database.createHashMap("account_info_helper").makeOrGet();
	}

	@Override
	protected Map<String, List<byte[]>> getMemoryMap() {
		return new HashMap<String, List<byte[]>>();
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
	protected List<byte[]> newListValue() {
		return new ArrayList<byte[]>();
	}

	public void add(String address, byte[] signature) {
		// Add signature to list if list doesn't already contain it
		this.listAdd(address, signature, (list, sig) -> !ByteArrayUtils.contains(list, sig), (list, sig) -> list.add(sig));
	}

	public void remove(String address, byte[] signature) {
		// Always remove signature from list
		this.listRemove(address, signature, (list, sig) -> true, (list, sig) -> ByteArrayUtils.remove(list, sig));
	}
}