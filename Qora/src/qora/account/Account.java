package qora.account;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;

import org.mapdb.Fun.Tuple2;

import at.AT_Transaction;
import controller.Controller;
import qora.BlockGenerator;
import qora.block.Block;
import qora.transaction.Transaction;
import utils.NumberAsString;
import database.DBSet;

public class Account {

	public static final int ADDRESS_LENGTH = 25;

	protected String address;

	private byte[] lastBlockSignature;
	private BigDecimal generatingBalance;

	protected Account() {
		this.generatingBalance = BigDecimal.ZERO.setScale(8);
	}

	public Account(String address) {
		this.address = address;
	}

	public String getAddress() {
		return address;
	}

	// BALANCE

	public BigDecimal getUnconfirmedBalance() {
		return this.getUnconfirmedBalance(DBSet.getInstance());
	}

	public BigDecimal getUnconfirmedBalance(DBSet db) {
		return Controller.getInstance().getUnconfirmedBalance(this.getAddress());
	}

	public BigDecimal getConfirmedBalance() {
		return this.getConfirmedBalance(DBSet.getInstance());
	}

	public BigDecimal getConfirmedBalance(DBSet db) {
		return db.getBalanceMap().get(getAddress());
	}

	public BigDecimal getConfirmedBalance(long key) {
		return this.getConfirmedBalance(key, DBSet.getInstance());
	}

	public BigDecimal getConfirmedBalance(long key, DBSet db) {
		return db.getBalanceMap().get(getAddress(), key);
	}

	public void setConfirmedBalance(BigDecimal amount) {
		this.setConfirmedBalance(amount, DBSet.getInstance());
	}

	public void setConfirmedBalance(BigDecimal amount, DBSet db) {
		// Actually update balance in DB
		db.getBalanceMap().set(getAddress(), amount);
	}

	public void setConfirmedBalance(long key, BigDecimal amount) {
		this.setConfirmedBalance(key, amount, DBSet.getInstance());
	}

	public void setConfirmedBalance(long key, BigDecimal amount, DBSet db) {
		// Actually update balance in DB
		db.getBalanceMap().set(getAddress(), key, amount);
	}

	public BigDecimal getBalance(int confirmations) {
		return this.getBalance(confirmations, DBSet.getInstance());
	}

	public BigDecimal getBalance(int confirmations, DBSet db) {
		// If no confirmations then use unconfirmed balance
		if (confirmations <= 0)
			return this.getUnconfirmedBalance(db);

		// If we only need balance with 1 confirmation then simply fetch from DB
		if (confirmations == 1)
			return this.getConfirmedBalance(db);

		/*
		 * For a balance with more confirmations work back from lastBlock, undoing transactions involving this account, until we have processed required number
		 * of blocks.
		 */
		BigDecimal balance = this.getConfirmedBalance(db);
		Block block = db.getBlockMap().getLastBlock();

		// Note: "block.getHeight(db) > 1" to make sure we don't examine genesis block
		for (int i = 1; i < confirmations && block != null && block.getHeight(db) > 1; ++i) {
			for (Transaction transaction : block.getTransactions()) {
				if (transaction.isInvolved(this))
					balance = balance.subtract(transaction.getAmount(this));
			}

			// Also check AT transactions for amounts received to this account
			LinkedHashMap<Tuple2<Integer, Integer>, AT_Transaction> atTxs = db.getATTransactionMap().getATTransactions(block.getHeight(db));
			Iterator<AT_Transaction> iter = atTxs.values().iterator();
			while (iter.hasNext()) {
				AT_Transaction key = iter.next();

				if (key.getRecipient().equals(this.getAddress()))
					balance = balance.subtract(BigDecimal.valueOf(key.getAmount(), 8));
			}

			block = block.getParent(db);
		}

		// Return balance
		return balance;
	}

	private void updateGeneratingBalance(DBSet db) {
		// If our cached lastBlock has changed then we need to recalculate this account's generating balance
		if (this.lastBlockSignature == null || !Arrays.equals(this.lastBlockSignature, db.getBlockMap().getLastBlockSignature())) {
			this.lastBlockSignature = db.getBlockMap().getLastBlockSignature();
			this.generatingBalance = calculateGeneratingBalance(db);
		}
	}

	/**
	 * Calculate current generating balance for this account.
	 * <p>
	 * This is the current confirmed balance minus amounts received in the last <code>BlockGenerator.RETARGET</code> blocks.
	 * 
	 */
	public BigDecimal calculateGeneratingBalance(DBSet db) {
		BigDecimal balance = this.getConfirmedBalance(db);

		Block block = db.getBlockMap().getLastBlock();

		for (int i = 1; i < BlockGenerator.RETARGET && block != null && block.getHeight(db) > 1; ++i) {
			for (Transaction transaction : block.getTransactions()) {
				if (transaction.isInvolved(this)) {
					final BigDecimal amount = transaction.getAmount(this);

					// Subtract positive amounts only
					if (amount.compareTo(BigDecimal.ZERO) == 1)
						balance = balance.subtract(amount);
				}
			}

			LinkedHashMap<Tuple2<Integer, Integer>, AT_Transaction> atTxs = db.getATTransactionMap().getATTransactions(block.getHeight(db));
			Iterator<AT_Transaction> iter = atTxs.values().iterator();
			while (iter.hasNext()) {
				AT_Transaction key = iter.next();

				if (key.getRecipient().equals(this.getAddress()))
					balance = balance.subtract(BigDecimal.valueOf(key.getAmount(), 8));
			}

			block = block.getParent(db);
		}

		// Do not go below 0
		// XXX: How would this even be possible?
		if (balance.compareTo(BigDecimal.ZERO) == -1)
			balance = BigDecimal.ZERO.setScale(8);

		 return balance;
	}

	public BigDecimal getGeneratingBalance() {
		return this.getGeneratingBalance(DBSet.getInstance());
	}

	public BigDecimal getGeneratingBalance(DBSet db) {
		// Potentially update generating balance
		updateGeneratingBalance(db);

		// Return generating balance
		return this.generatingBalance;
	}

	// REFERENCE

	public byte[] getLastReference() {
		return this.getLastReference(DBSet.getInstance());
	}

	public byte[] getLastReference(DBSet db) {
		return db.getReferenceMap().get(this);
	}

	public void setLastReference(byte[] reference) {
		this.setLastReference(reference, DBSet.getInstance());
	}

	public void setLastReference(byte[] reference, DBSet db) {
		db.getReferenceMap().set(this, reference);
	}

	public void removeReference() {
		this.removeReference(DBSet.getInstance());
	}

	public void removeReference(DBSet db) {
		db.getReferenceMap().delete(this);
	}

	// TOSTRING

	@Override
	public String toString() {
		return NumberAsString.getInstance().numberAsString(this.getBalance(0)) + " - " + this.getAddress();
	}

	public String toString(long key) {
		return NumberAsString.getInstance().numberAsString(this.getConfirmedBalance(key)) + " - " + this.getAddress();
	}

	@Override
	public int hashCode() {
		return this.getAddress().hashCode();
	}

	// EQUALS
	@Override
	public boolean equals(Object b) {
		if (!(b instanceof Account))
			return false;

		return this.getAddress().equals(((Account) b).getAddress());
	}
}
