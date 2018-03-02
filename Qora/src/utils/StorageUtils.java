package utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.google.common.base.Charsets;

import controller.Controller;
import database.DBSet;
import qora.account.PublicKeyAccount;
import qora.naming.Name;
import qora.transaction.ArbitraryTransaction;
import qora.transaction.Transaction;
import qora.web.NameStorageMap;
import qora.web.OrphanNameStorageHelperMap;
import qora.web.OrphanNameStorageMap;

public class StorageUtils {
	private static final Logger LOGGER = LogManager.getLogger(StorageUtils.class);

	// REPLACES CURRENT VALUE
	public static final String ADD_COMPLETE_KEY = "addcomplete";
	// REMOVES CURRENT VALUE (COMPLETE KEY FROM STORAGE)
	public static final String REMOVE_COMPLETE_KEY = "removecomplete";
	// ADD VALUE TO A LIST IF NOT IN LIST SEPERATOR ";"
	public static final String ADD_LIST_KEY = "addlist";
	// REMOVE VALUE FROM LIST IF VALUE THERE SEPERATOR ";"
	public static final String REMOVE_LIST_KEY = "removelist";
	// ADD TO CURRENT VALUE WITHOUT SEPERATOR
	public static final String ADD_KEY = "add";
	// ADD PATCH TO CURRENT VALUE
	public static final String PATCH_KEY = "patch";

	/**
	 * Attempt to extract JSON object using key
	 * <p>
	 * Automatically removes String-encapsulation
	 * 
	 * @param {JSONObject}
	 *            jsonObject
	 * @param {String}
	 *            mainKey
	 * @return JSONObject or null
	 */
	private static JSONObject getDataByKey(JSONObject jsonObject, String mainKey) {
		Object jsonData = jsonObject.get(mainKey);

		if (jsonData == null)
			return null;

		// remove string encapsulation
		if (jsonData instanceof String)
			jsonData = JSONValue.parse((String) jsonData);

		// must be JSON object now
		if (!(jsonData instanceof JSONObject)) {
			LOGGER.warn("Expecting JSONObject while looking for \"" + mainKey + "\" data");
			return null;
		}

		return (JSONObject) jsonData;
	}

	/**
	 * Generate JSON object representing add/remove/patch storage actions
	 * <p>
	 * 
	 * @param addCompleteKeys
	 * @param removeCompleteKeys
	 * @param addListKeys
	 * @param removeListKeys
	 * @param addWithoutSeperator
	 * @param addPatch
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static JSONObject getStorageJsonObject(List<Pair<String, String>> addCompleteKeys, List<String> removeCompleteKeys,
			List<Pair<String, String>> addListKeys, List<Pair<String, String>> removeListKeys, List<Pair<String, String>> addWithoutSeperator,
			List<Pair<String, String>> addPatch) {
		JSONObject json = new JSONObject();

		addListPairtoJson(addCompleteKeys, json, ADD_COMPLETE_KEY);

		// removeCompleteKeys is only a list of keys so add them using "" as value
		if (removeCompleteKeys != null && removeCompleteKeys.size() > 0) {
			JSONObject jsonRemoveComplete = new JSONObject();

			for (String key : removeCompleteKeys)
				jsonRemoveComplete.put(key, "");

			json.put(REMOVE_COMPLETE_KEY, jsonRemoveComplete.toString());
		}

		addListPairtoJson(addListKeys, json, ADD_LIST_KEY);

		addListPairtoJson(removeListKeys, json, REMOVE_LIST_KEY);

		addListPairtoJson(addWithoutSeperator, json, ADD_KEY);

		addListPairtoJson(addPatch, json, PATCH_KEY);

		return json;
	}

	/**
	 * Add pairs of strings to JSON under specific key
	 * <p>
	 * Adds a list of String-pairs passed in <code>addListKeys</code> to the JSONObject <code>json</code> under the key <code>key</code>.
	 * 
	 * @param addListKeys
	 * @param json
	 * @param key
	 */
	@SuppressWarnings("unchecked")
	public static void addListPairtoJson(List<Pair<String, String>> addListKeys, JSONObject json, String key) {
		if (addListKeys != null && addListKeys.size() > 0) {
			JSONObject innerJsonObject = new JSONObject();

			for (Pair<String, String> pair : addListKeys)
				innerJsonObject.put(pair.getA(), pair.getB());

			json.put(key, innerJsonObject.toString());
		}
	}

	/**
	 * Process transaction and update name storage
	 * <p>
	 * 
	 * @param data
	 * @param signature
	 * @param creator
	 * @param db
	 * @throws Exception
	 */
	public static void processUpdate(byte[] data, byte[] signature, PublicKeyAccount creator, DBSet db) throws Exception {
		String dataAsString = new String(data, Charsets.UTF_8);

		dataAsString = GZIP.webDecompress(dataAsString);

		JSONObject jsonObject = (JSONObject) JSONValue.parse(dataAsString);

		if (jsonObject == null)
			return;

		Object jsonName = jsonObject.get("name");

		// mandatory and must be string
		if (jsonName == null || !(jsonName instanceof String))
			throw new Exception("Name-storage transaction being processed has no \"name\" in data");

		String name = (String) jsonName;

		Name nameObj = db.getNameMap().get(name);

		// if name not registered, we are keying by address which must match 'creator' address
		if (nameObj == null && !name.equals(creator.getAddress()))
			return;

		// if name registered, check owner is 'creator'
		if (nameObj != null && !nameObj.getOwner().getAddress().equals(creator.getAddress()))
			return;

		// Retrieve list of orphaned transactions for this name
		OrphanNameStorageHelperMap orphanNameStorageHelperMap = db.getOrphanNameStorageHelperMap();
		List<byte[]> list = orphanNameStorageHelperMap.get(name);

		// If this transaction is in list then it's been processed already
		if (list != null && ByteArrayUtils.contains(list, signature))
			return;

		NameStorageMap nameStorageMap = db.getNameStorageMap();
		OrphanNameStorageMap orphanNameStorageMap = db.getOrphanNameStorageMap();

		// Find all the storage keys that are affected by this transaction
		Set<String> allKeysForOrphanSaving = getAllKeysForOrphanSaving(jsonObject);

		// Save old values for keys affected by this transaction to allow possible future orphaning
		for (String keyForOrphaning : allKeysForOrphanSaving)
			orphanNameStorageMap.add(signature, keyForOrphaning, nameStorageMap.getOpt(name, keyForOrphaning));

		// Actually process storage changes
		addTxChangesToStorage(jsonObject, name, nameStorageMap, null);

		// Save this transaction in list (above)
		db.getOrphanNameStorageHelperMap().add(name, signature);
	}

	/**
	 * Apply storage actions described in JSON to name storage
	 * <p>
	 * 
	 * @param jsonObject
	 * @param name
	 * @param nameStorageMap
	 * @param onlyTheseKeysOpt
	 */
	@SuppressWarnings("unchecked")
	public static void addTxChangesToStorage(JSONObject jsonObject, String name, NameStorageMap nameStorageMap, Set<String> onlyTheseKeysOpt) {

		JSONObject addCompleteResults = getDataByKey(jsonObject, ADD_COMPLETE_KEY);
		if (addCompleteResults != null) {
			Set<String> keys = addCompleteResults.keySet();

			for (String key : keys)
				if (onlyTheseKeysOpt == null || onlyTheseKeysOpt.contains(key))
					nameStorageMap.add(name, key, "" + addCompleteResults.get(key));
		}

		JSONObject removeCompleteResults = getDataByKey(jsonObject, REMOVE_COMPLETE_KEY);
		if (removeCompleteResults != null) {
			Set<String> keys = removeCompleteResults.keySet();

			for (String key : keys)
				if (onlyTheseKeysOpt == null || onlyTheseKeysOpt.contains(key))
					nameStorageMap.remove(name, key);
		}

		JSONObject addListResults = getDataByKey(jsonObject, ADD_LIST_KEY);
		if (addListResults != null) {
			Set<String> keys = addListResults.keySet();

			for (String key : keys)
				if (onlyTheseKeysOpt == null || onlyTheseKeysOpt.contains(key)) {
					List<String> entriesToAdd = new ArrayList<>(Arrays.asList(StringUtils.split("" + addListResults.get(key), ";")));
					nameStorageMap.addListEntries(name, key, entriesToAdd);
				}
		}

		JSONObject removeListResults = getDataByKey(jsonObject, REMOVE_LIST_KEY);
		if (removeListResults != null) {
			Set<String> keys = removeListResults.keySet();

			for (String key : keys)
				if (onlyTheseKeysOpt == null || onlyTheseKeysOpt.contains(key)) {
					List<String> entriesToAdd = new ArrayList<>(Arrays.asList(StringUtils.split("" + removeListResults.get(key), ";")));
					nameStorageMap.removeListEntries(name, key, entriesToAdd);
				}
		}

		JSONObject addResults = getDataByKey(jsonObject, ADD_KEY);
		if (addResults != null) {
			Set<String> keys = addResults.keySet();

			for (String key : keys)
				if (onlyTheseKeysOpt == null || onlyTheseKeysOpt.contains(key)) {
					String oldValueOpt = nameStorageMap.getOpt(name, key);
					oldValueOpt = oldValueOpt == null ? "" : oldValueOpt;
					nameStorageMap.add(name, key, oldValueOpt + "" + addResults.get(key));
				}
		}

		JSONObject patchResults = getDataByKey(jsonObject, PATCH_KEY);
		if (patchResults != null) {
			Set<String> keys = patchResults.keySet();

			for (String key : keys)
				if (onlyTheseKeysOpt == null || onlyTheseKeysOpt.contains(key)) {
					String oldValueOpt = nameStorageMap.getOpt(name, key);

					oldValueOpt = oldValueOpt == null ? "" : oldValueOpt;
					try {
						nameStorageMap.add(name, key, DiffHelper.patch(oldValueOpt, (String) patchResults.get(key)));
					} catch (Throwable e) {
						LOGGER.warn("Invalid name storage patch for name \"" + name + "\" and key \"" + key + "\": " + e.getMessage());
					}
				}
		}
	}

	/**
	 * Scan all storage actions in JSON and find any sub-keys
	 * 
	 * @param jsonObject
	 * @return
	 */
	private static Set<String> getAllKeysForOrphanSaving(JSONObject jsonObject) {
		Set<String> results = new HashSet<>();
		getKeys(jsonObject, results, ADD_COMPLETE_KEY);
		getKeys(jsonObject, results, ADD_LIST_KEY);
		getKeys(jsonObject, results, REMOVE_COMPLETE_KEY);
		getKeys(jsonObject, results, REMOVE_LIST_KEY);
		getKeys(jsonObject, results, ADD_KEY);
		getKeys(jsonObject, results, PATCH_KEY);

		return results;
	}

	/**
	 * Find <code>mainKey</code> in JSON and add any sub-keys to <code>results</code>
	 * <p>
	 * 
	 * @param jsonObject
	 * @param results
	 * @param mainKey
	 */
	private static void getKeys(JSONObject jsonObject, Set<String> results, String mainKey) {
		JSONObject storageData = getDataByKey(jsonObject, mainKey);

		// mainKey isn't in JSON so bail out
		if (storageData == null)
			return;

		// Extract all sub-keys
		@SuppressWarnings("unchecked")
		Set<String> keys = storageData.keySet();

		// Add to results
		results.addAll(keys);
	}

	/**
	 * Unlink transaction from blockchain and undo storage changes
	 * 
	 * @param data
	 * @param signature
	 * @param db
	 */
	public static void orphanUpdate(byte[] data, byte[] signature, DBSet db) throws Exception {
		String dataAsString = new String(data, Charsets.UTF_8);

		dataAsString = GZIP.webDecompress(dataAsString);

		JSONObject jsonObject = (JSONObject) JSONValue.parse(dataAsString);

		if (jsonObject == null)
			return;

		Object jsonName = jsonObject.get("name");

		// mandatory and must be string
		if (jsonName == null || !(jsonName instanceof String))
			throw new Exception("Name-storage transaction being orphaned has no \"name\" in data");

		String name = (String) jsonName;

		// Check whether this transaction has been orphaned already
		OrphanNameStorageHelperMap orphanNameStorageHelperMap = db.getOrphanNameStorageHelperMap();
		List<byte[]> orphanableSignatures = orphanNameStorageHelperMap.get(name);

		// If this transaction isn't in list then it's been orphaned already
		if (orphanableSignatures == null || !ByteArrayUtils.contains(orphanableSignatures, signature))
			return;

		// Grab saved previous values for keys in this transaction
		Map<String, String> orphanMapForTx = db.getOrphanNameStorageMap().get(signature);

		// If absent, something must have gone wrong
		if (orphanMapForTx == null)
			throw new Exception("Can't orphan name storage transaction due to missing saved values");

		// Grab current name storage values
		NameStorageMap nameStorageMap = db.getNameStorageMap();
		Map<String, String> valueMapForName = nameStorageMap.get(name);

		// No name storage for this name? Something wrong
		if (valueMapForName == null)
			throw new Exception("Can't orphan name storage transaction due to missing current values");

		// Use saved orphan values to rollback to before this transaction
		Set<String> keySet = orphanMapForTx.keySet();
		for (String key : keySet) {
			String value = orphanMapForTx.get(key);

			if (value != null) {
				nameStorageMap.add(name, key, value);
			} else {
				nameStorageMap.remove(name, key);
			}
		}

		// Reapply following transactions (only need to do common keys), updating their saved orphan values
		int indexOf = ByteArrayUtils.indexOf(orphanableSignatures, signature);
		indexOf++;
		for (int i = indexOf; i < orphanableSignatures.size(); ++i) {
			// Get signature of following transaction (if any)
			byte[] followingSignature = orphanableSignatures.get(i);

			// Grab following transaction
			Transaction followingTransaction = Controller.getInstance().getTransaction(followingSignature, db);

			// Bad news if we can't retrieve transaction!
			if (followingTransaction == null)
				throw new Exception("Can't find following transaction during name storage orphaning");

			// Extract transaction data, decompress, etc.
			byte[] followingData = ((ArbitraryTransaction) followingTransaction).getData();
			String followingDataAsString = new String(followingData, Charsets.UTF_8);
			followingDataAsString = GZIP.webDecompress(followingDataAsString);
			JSONObject followingJsonObject = (JSONObject) JSONValue.parse(followingDataAsString);

			// Extract all sub-keys
			Set<String> allKeysForOrphanSaving = getAllKeysForOrphanSaving(followingJsonObject);

			// Only process sub-keys that following transaction has in common with transaction being orphaned
			Set<String> keysToSaveSnapshot = new HashSet<String>();
			for (String key : keySet)
				if (allKeysForOrphanSaving.contains(key))
					keysToSaveSnapshot.add(key);

			// Update post-orphan previous values for this [following] transaction
			OrphanNameStorageMap orphanNameStorageMap = db.getOrphanNameStorageMap();
			for (String keyForOrphaning : keysToSaveSnapshot)
				orphanNameStorageMap.add(followingSignature, keyForOrphaning, nameStorageMap.getOpt(name, keyForOrphaning));

			// Re-apply name storage updates for this [following] transaction, common keys only
			addTxChangesToStorage(followingJsonObject, name, nameStorageMap, keySet);
		}

		// Delete saved previous values for orphaned transaction as no longer needed
		db.getOrphanNameStorageMap().delete(signature);

		// Delete orphaned transaction from list of orphanable transactions for this name
		db.getOrphanNameStorageHelperMap().remove(name, signature);
	}
}
