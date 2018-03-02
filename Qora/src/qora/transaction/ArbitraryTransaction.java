package qora.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.google.common.primitives.Longs;

import database.BalanceMap;
import database.DBSet;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.crypto.Base58;
import qora.payment.Payment;
import utils.BlogUtils;
import utils.StorageUtils;

public abstract class ArbitraryTransaction extends Transaction {

	private int version;
	protected PublicKeyAccount creator;
	protected int service;
	protected byte[] data;

	private static final Logger LOGGER = LogManager.getLogger(ArbitraryTransaction.class);
	protected List<Payment> payments;

	public static final int SERVICE_NAME_STORAGE = 10;
	public static final int SERVICE_BLOG_POST = 777;
	public static final int SERVICE_BLOG_COMMENT = 778;

	public static final Map<Integer, String> serviceNames = new HashMap<Integer, String>();
	static {
		serviceNames.put(SERVICE_NAME_STORAGE, "name storage");
		serviceNames.put(SERVICE_BLOG_POST, "blog post");
		serviceNames.put(SERVICE_BLOG_COMMENT, "blog comment");
	}

	public ArbitraryTransaction(BigDecimal fee, long timestamp, byte[] reference, byte[] signature) {
		super(ARBITRARY_TRANSACTION, fee, timestamp, reference, signature);

		if (timestamp < Transaction.getPOWFIX_RELEASE()) {
			version = 1;
		} else {
			version = 3;
		}
	}

	public int getVersion() {
		return this.version;
	}

	// GETTERS/SETTERS

	public int getService() {
		return this.service;
	}

	public byte[] getData() {
		return this.data;
	}

	public List<Payment> getPayments() {
		if (this.payments != null) {
			return this.payments;
		} else {
			return new ArrayList<Payment>();
		}
	}

	// PARSE CONVERT

	@SuppressWarnings("unchecked")
	@Override
	public JSONObject toJson() {
		// GET BASE
		JSONObject transaction = this.getJsonBase();

		// ADD CREATOR/SERVICE/DATA
		transaction.put("creator", this.creator.getAddress());
		transaction.put("service", this.service);
		transaction.put("data", Base58.encode(this.data));

		JSONArray payments = new JSONArray();
		for (Payment payment : this.payments)
			payments.add(payment.toJson());

		if (payments.size() > 0)
			transaction.put("payments", payments);

		return transaction;
	}

	@Override
	public abstract byte[] toBytes();

	@Override
	public abstract int getDataLength();

	// VALIDATE

	@Override
	public abstract boolean isSignatureValid();

	@Override
	public abstract int isValid(DBSet db); // XXX: maybe this should check service and call specific service isValid?

	public static Transaction Parse(byte[] data) throws Exception {
		// READ TIMESTAMP
		byte[] timestampBytes = Arrays.copyOfRange(data, 0, TIMESTAMP_LENGTH);
		long timestamp = Longs.fromByteArray(timestampBytes);

		if (timestamp < Transaction.getPOWFIX_RELEASE()) {
			return ArbitraryTransactionV1.Parse(data);
		} else {
			return ArbitraryTransactionV3.Parse(data);
		}
	}

	@Override
	public PublicKeyAccount getCreator() {
		return this.creator;
	}

	@Override
	public HashSet<Account> getInvolvedAccounts() {
		HashSet<Account> accounts = new HashSet<>();

		accounts.add(this.creator);
		accounts.addAll(this.getRecipientAccounts());

		return accounts;
	}

	@Override
	public HashSet<Account> getRecipientAccounts() {
		HashSet<Account> accounts = new HashSet<>();

		for (Payment payment : this.payments)
			accounts.add(payment.getRecipient());

		return accounts;
	}

	@Override
	public boolean isInvolved(Account account) {
		String address = account.getAddress();

		for (Account involved : this.getInvolvedAccounts())
			if (address.equals(involved.getAddress()))
				return true;

		return false;
	}

	@Override
	public BigDecimal getAmount(Account account) {
		BigDecimal amount = BigDecimal.ZERO.setScale(8);
		String address = account.getAddress();

		// IF SENDER
		if (address.equals(this.creator.getAddress()))
			amount = amount.subtract(this.fee);

		// CHECK PAYMENTS
		for (Payment payment : this.payments) {
			// IF QORA ASSET
			if (payment.getAsset() == BalanceMap.QORA_KEY) {
				// IF SENDER
				if (address.equals(this.creator.getAddress()))
					amount = amount.subtract(payment.getAmount());

				// IF RECIPIENT
				if (address.equals(payment.getRecipient().getAddress()))
					amount = amount.add(payment.getAmount());
			}
		}

		return amount;
	}

	@Override
	public Map<String, Map<Long, BigDecimal>> getAssetAmount() {
		Map<String, Map<Long, BigDecimal>> assetAmount = new LinkedHashMap<>();

		assetAmount = subAssetAmount(assetAmount, this.creator.getAddress(), BalanceMap.QORA_KEY, this.fee);

		for (Payment payment : this.payments) {
			assetAmount = subAssetAmount(assetAmount, this.creator.getAddress(), payment.getAsset(), payment.getAmount());
			assetAmount = addAssetAmount(assetAmount, payment.getRecipient().getAddress(), payment.getAsset(), payment.getAmount());
		}

		return assetAmount;
	}

	// PROCESS/ORPHAN
	@Override
	public void process(DBSet db) {

		try {
			if (this.getService() == SERVICE_NAME_STORAGE) {
				// NAME STORAGE UPDATE
				StorageUtils.processUpdate(getData(), signature, this.getCreator(), db);
			} else if (this.getService() == SERVICE_BLOG_POST) {
				// BLOGPOST
				BlogUtils.processBlogPost(getData(), signature, this.getCreator(), db);
			} else if (this.getService() == SERVICE_BLOG_COMMENT) {
				BlogUtils.processBlogComment(getData(), signature, this.getCreator(), db);
			}
		} catch (Throwable e) {
			// failed to process
			LOGGER.error(e.getMessage(), e);
			// fall-through - transaction fees still apply!
		}

		// UPDATE CREATOR
		this.getCreator().setConfirmedBalance(this.getCreator().getConfirmedBalance(db).subtract(this.fee), db);

		// UPDATE REFERENCE OF CREATOR
		this.getCreator().setLastReference(this.signature, db);

		// PROCESS PAYMENTS
		for (Payment payment : this.getPayments()) {
			payment.process(this.getCreator(), db);

			// UPDATE REFERENCE OF RECIPIENT
			if (Arrays.equals(payment.getRecipient().getLastReference(db), new byte[0]))
				payment.getRecipient().setLastReference(this.signature, db);
		}
	}

	@Override
	public void orphan(DBSet db) {

		try {
			if (this.getService() == SERVICE_NAME_STORAGE) {
				// NAME STORAGE UPDATE
				StorageUtils.orphanUpdate(getData(), signature, db);
			} else if (this.getService() == SERVICE_BLOG_POST) {
				// BLOGPOST
				BlogUtils.orphanBlogPost(getData(), signature, this.getCreator(), db);
			} else if (this.getService() == SERVICE_BLOG_COMMENT) {
				BlogUtils.orphanBlogComment(getData(), signature, this.getCreator(), db);
			}
		} catch (Throwable e) {
			// failed to process
			LOGGER.error(e.getMessage(), e);
			// fall-through - transaction fees still apply!
		}

		// UPDATE CREATOR
		this.getCreator().setConfirmedBalance(this.getCreator().getConfirmedBalance(db).add(this.fee), db);

		// UPDATE REFERENCE OF CREATOR
		this.getCreator().setLastReference(this.reference, db);

		// ORPHAN PAYMENTS
		for (Payment payment : this.getPayments()) {
			payment.orphan(this.getCreator(), db);

			// UPDATE REFERENCE OF RECIPIENT
			if (Arrays.equals(payment.getRecipient().getLastReference(db), this.signature))
				payment.getRecipient().removeReference(db);
		}
	}
}
