package database;

import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import org.mapdb.Bind;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Fun;
import org.mapdb.HTreeMap;

import utils.AccountInfoUtils;

// Key: Account address, e.g. QgcphUTiVHHfHg8e1LVgg5jujVES7ZDUTr
// Value: Account info, e.g. "{"alias":"Mr. Smee"}"
public class AccountInfoMap extends DBMap<String, String> {
	private Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();

	// Key: Account alias, e.g. "Mr. Smee", Value: account address, e.g. QgcphUTiVHHfHg8e1LVgg5jujVES7ZDUTr
	private Map<String, String> aliasIndex;

	public AccountInfoMap(DBSet databaseSet, DB database) {
		super(databaseSet, database);
	}

	public AccountInfoMap(AccountInfoMap parent) {
		super(parent);
	}

	protected void createIndexes(DB database) {}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Map<String, String> openMap(DB database) {
		Map<String, String> map = database.createHashMap("account_info").makeOrGet();

		aliasIndex = database.createHashMap("account_info_alias_index").makeOrGet();

		Bind.secondaryKey((HTreeMap) map, aliasIndex, new Fun.Function2<String, String, String>() {
			@Override
			public String run(String address, String accountInfo) {
				// Extract alias from info, which is mandatory
				JSONObject json = (JSONObject) JSONValue.parse(accountInfo);
				String alias = (String) json.get(AccountInfoUtils.ALIAS_KEY);
				// Convert to lowercase as we need case-insensitive comparison
				return alias.toLowerCase();
			}
		});

		return map;
	}

	@Override
	protected Map<String, String> getMap(DB database) {
		return this.openMap(database);
	}

	@Override
	protected Map<String, String> getMemoryMap() {
		DB database = DBMaker.newMemoryDB().make();

		return this.getMap(database);
	}

	@Override
	protected String getDefaultValue() {
		return null;
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	public String getAddressByAlias(String alias) {
		return aliasIndex.get(alias.toLowerCase());
	}

}