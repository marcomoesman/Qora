package database;

import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import org.mapdb.Bind;
import org.mapdb.DB;
import org.mapdb.Fun;
import org.mapdb.HTreeMap;

import qora.account.Account;
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

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void createIndexes(DB database) {
		aliasIndex = database.createHashMap("account_info_alias_index").makeOrGet();

		Bind.secondaryKey((HTreeMap) this.map, aliasIndex, new Fun.Function2<String, String, String>() {
			@Override
			public String run(String address, String accountInfo) {
				// Extract alias from info, which is mandatory
				JSONObject json = (JSONObject) JSONValue.parse(accountInfo);
				return (String) json.get(AccountInfoUtils.ALIAS_KEY);
			}
		});
	}

	@Override
	protected Map<String, String> getMap(DB database) {
		// OPEN MAP
		return database.createHashMap("account_info").makeOrGet();
	}

	@Override
	protected Map<String, String> getMemoryMap() {
		return new HashMap<String, String>();
	}

	@Override
	protected String getDefaultValue() {
		return null;
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	public String get(Account account) {
		return this.get(account.getAddress());
	}

	public String getAccountByAlias(String alias) {
		return aliasIndex.get(alias);
	}

	public void set(Account account, String accountInfo) {
		this.set(account.getAddress(), accountInfo);
	}
	
	public void delete(Account account) {
		this.delete(account.getAddress());
	}
}