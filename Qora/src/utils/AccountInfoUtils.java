package utils;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.google.common.base.Charsets;

import database.AccountInfoHelperMap;
import database.AccountInfoMap;
import database.DBSet;
import database.TransactionMap;
import qora.account.PublicKeyAccount;
import qora.transaction.ArbitraryTransaction;

public class AccountInfoUtils {
	private static final Logger LOGGER = LogManager.getLogger(AccountInfoUtils.class);

	// Account alias (mandatory)
	public static final String ALIAS_KEY = "alias";
	
	public static final int MIN_ALIAS_LENGTH = 3;
	public static final int MAX_ALIAS_LENGTH = 32;

	/**
	 * Check account alias validity
	 * <p>
	 * Must be at between MIN_ALIAS_LENGTH and MAX_ALIAS_LENGTH in length.<br>
	 * Must not start, or end, with whitespace.
	 *  
	 * @param {String} alias
	 * @return <code>true</code> - if valid
	 */
	public static boolean isAliasValid(String alias) {
		if (alias == null)
			return false;
		
		final int length = alias.length();
		
		if (length < MIN_ALIAS_LENGTH || length > MAX_ALIAS_LENGTH)
			return false;
		
		int codePoint = alias.codePointAt(0);
		if (Character.isWhitespace(codePoint))
			return false;

		codePoint = alias.codePointBefore(length);
		if (Character.isWhitespace(codePoint))
			return false;
		
		return true;
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
	public static void processUpdate(byte[] data, byte[] signature, PublicKeyAccount account, DBSet db) {
		String dataAsString = new String(data, Charsets.UTF_8);

		dataAsString = GZIP.webDecompress(dataAsString);

		JSONObject jsonObject = (JSONObject) JSONValue.parse(dataAsString);

		if (jsonObject == null)
			return;

		AccountInfoMap accountInfoMap = db.getAccountInfoMap();
		final String address = account.getAddress();

		// "alias" must be in JSON
		Object jsonAlias = jsonObject.get(ALIAS_KEY);
		if (jsonAlias == null || !(jsonAlias instanceof String)) {
			LOGGER.info("Set-account-info transaction being processed has no \"alias\" in data");
			return;
		}

		final String alias = (String) jsonAlias;
		
		// Perform validity checks on alias
		if (!isAliasValid(alias))
			return;

		AccountInfoHelperMap accountInfoHelperMap = db.getAccountInfoHelperMap();

		// If this transaction is in the map then it's been processed already
		List<byte[]> list = accountInfoHelperMap.get(address);
		if (list != null && ByteArrayUtils.contains(list, signature))
			return;

		// If requested alias already exists then it cannot belong to someone else
		final String aliasOwner = accountInfoMap.getAddressByAlias(alias);
		if (aliasOwner != null && !aliasOwner.equals(address)) {
			LOGGER.info("Alias \"" + alias + "\" already used by " + address);
			return;
		}

		// Set 'new' account info for this account
		accountInfoMap.set(address, dataAsString);

		// Add this transaction to list of account info setting transactions for this account
		accountInfoHelperMap.add(address, signature);
	}

	/**
	 * Unlink transaction from blockchain and undo storage changes
	 * 
	 * @param data
	 * @param signature
	 * @param db
	 */
	public static void orphanUpdate(byte[] signature, PublicKeyAccount account, DBSet db) throws Exception {
		final String address = account.getAddress();

		// Check whether this transaction has been orphaned already
		AccountInfoHelperMap accountInfoHelperMap = db.getAccountInfoHelperMap();

		// If this transaction is in the map then it's been processed already
		List<byte[]> orphanableSignatures = accountInfoHelperMap.get(address);

		// If this transaction isn't in list then it's been orphaned already
		if (orphanableSignatures == null)
			return;

		int signaturesIndex = ByteArrayUtils.indexOf(orphanableSignatures, signature);

		if (signaturesIndex == -1)
			return;

		// Surely this will be the last signature in the list?
		if (signaturesIndex != orphanableSignatures.size() - 1)
			LOGGER.info("Orphaning non-last account info setting transaction?");

		// Remove this signature from list
		accountInfoHelperMap.remove(address, signature);

		AccountInfoMap accountInfoMap = db.getAccountInfoMap();

		if (signaturesIndex == 0) {
			// No previous account info
			accountInfoMap.delete(address);
			return;
		}

		byte[] previousSignature = orphanableSignatures.get(signaturesIndex - 1);
		
		TransactionMap transactionMap = db.getTransactionMap();

		// Grab previous account info setting transaction
		ArbitraryTransaction previousTransaction = (ArbitraryTransaction) transactionMap.get(previousSignature);

		if (previousTransaction == null) {
			LOGGER.info("Can't find previous account info setting transaction");
			return;
		}

		byte[] data = previousTransaction.getData();
		String dataAsString = new String(data, Charsets.UTF_8);
		dataAsString = GZIP.webDecompress(dataAsString);

		accountInfoMap.set(address, dataAsString);
	}
}
