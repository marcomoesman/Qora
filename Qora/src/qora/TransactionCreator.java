package qora;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import controller.Controller;
import database.DBSet;
import ntp.NTP;
import qora.account.Account;
import qora.account.GenesisAccount;
import qora.account.PrivateKeyAccount;
import qora.account.PublicKeyAccount;
import qora.assets.Asset;
import qora.assets.Order;
import qora.block.Block;
import qora.naming.Name;
import qora.naming.NameSale;
import qora.payment.Payment;
import qora.transaction.ArbitraryTransactionV1;
import qora.transaction.ArbitraryTransactionV3;
import qora.transaction.BuyNameTransaction;
import qora.transaction.CancelOrderTransaction;
import qora.transaction.CancelSellNameTransaction;
import qora.transaction.CreateOrderTransaction;
import qora.transaction.CreatePollTransaction;
import qora.transaction.DeployATTransaction;
import qora.transaction.IssueAssetTransaction;
import qora.transaction.MessageTransactionV1;
import qora.transaction.MessageTransactionV3;
import qora.transaction.MultiPaymentTransaction;
import qora.transaction.PaymentTransaction;
import qora.transaction.RegisterNameTransaction;
import qora.transaction.SellNameTransaction;
import qora.transaction.Transaction;
import qora.transaction.TransactionFactory;
import qora.transaction.TransferAssetTransaction;
import qora.transaction.UpdateNameTransaction;
import qora.transaction.VoteOnPollTransaction;
import qora.voting.Poll;
import settings.Settings;
import utils.Pair;
import utils.TransactionTimestampComparator;

public class TransactionCreator {
	private DBSet fork;
	private Block lastBlock;

	/**
	 * Check we are using the latest block to create transactions.
	 * <p>
	 * Transactions are created using a fork based on latest block.
	 * 
	 * @see updateFork()
	 */
	private void checkBlock() {
		// If we've no lastBlock or lastBlock has changed then we need a new fork
		if (this.lastBlock == null || !Arrays.equals(this.lastBlock.getSignature(), Controller.getInstance().getLastBlock().getSignature()))
			updateFork();
	}

	/**
	 * Create a fork from last block for this wallet's unconfirmed transactions.
	 * <p>
	 * Any existing unconfirmed transactions (created by this wallet) are also re-processed on this fork in chronological order to preserve the transaction
	 * reference chain.
	 * 
	 * @see checkBlock()
	 */
	private void updateFork() {
		// Create a new fork
		this.fork = DBSet.getInstance().fork();

		// Cache the lastBlock we're using as the base for this fork
		this.lastBlock = Controller.getInstance().getLastBlock();

		/*
		 * We need to re-add unconfirmed transactions belonging to this wallet, in chronological order, so that future transactions have the correct chained
		 * references.
		 */

		// First filter unconfirmed transactions, keeping those created by this wallet.
		List<Account> accounts = Controller.getInstance().getAccounts();
		List<Transaction> transactions = DBSet.getInstance().getTransactionMap().getTransactions();
		transactions.removeIf((Transaction transaction) -> !accounts.contains(transaction.getCreator()));

		// Sort transactions chronologically
		Collections.sort(transactions, new TransactionTimestampComparator());

		// Process valid transactions on fork
		for (Transaction transaction : transactions) {
			if (transaction.isValid(this.fork) == Transaction.VALIDATE_OK && transaction.isSignatureValid()) {
				transaction.process(this.fork);
			} else {
				// Transactions is invalid - delete from unconfirmed transactions
				DBSet.getInstance().getTransactionMap().delete(transaction);
			}
		}
	}

	public Pair<Transaction, Integer> createPayment(PrivateKeyAccount sender, Account recipient, BigDecimal amount, BigDecimal fee) {
		// Check we're using latest block
		this.checkBlock();

		// Grab timestamp for this transaction
		final long time = NTP.getTime();

		// Create transaction signature
		final byte[] signature = PaymentTransaction.generateSignature(this.fork, sender, recipient, amount, fee, time);

		// Create payment transaction
		PaymentTransaction payment = new PaymentTransaction(new PublicKeyAccount(sender.getPublicKey()), recipient, amount, fee, time,
				sender.getLastReference(this.fork), signature);

		// Validate and process
		return this.afterCreate(payment);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForPayment() {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount sender = new GenesisAccount();

		// Create payment transaction
		PaymentTransaction payment = new PaymentTransaction(sender, sender, Transaction.MINIMUM_FEE, Transaction.MINIMUM_FEE, time, signature, signature);

		return new Pair(payment.calcRecommendedFee(), payment.getDataLength());
	}

	public Pair<Transaction, Integer> createNameRegistration(PrivateKeyAccount registrant, Name name, BigDecimal fee) {
		// Check we're using latest block
		this.checkBlock();

		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = RegisterNameTransaction.generateSignature(this.fork, registrant, name, fee, time);

		// Create name registration transaction
		RegisterNameTransaction nameRegistration = new RegisterNameTransaction(registrant, name, fee, time, registrant.getLastReference(this.fork), signature);

		// Validate and process
		return this.afterCreate(nameRegistration);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForNameRegistration(Name name) {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount registrant = new GenesisAccount();

		// Create name registration transaction
		RegisterNameTransaction nameRegistration = new RegisterNameTransaction(registrant, name, Transaction.MINIMUM_FEE, time, signature, signature);

		return new Pair(nameRegistration.calcRecommendedFee(), nameRegistration.getDataLength());
	}

	public Pair<Transaction, Integer> createNameUpdate(PrivateKeyAccount owner, Name name, BigDecimal fee) {
		// Check we're using latest block
		this.checkBlock();

		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = UpdateNameTransaction.generateSignature(this.fork, owner, name, fee, time);

		// Create name update transaction
		UpdateNameTransaction nameUpdate = new UpdateNameTransaction(owner, name, fee, time, owner.getLastReference(this.fork), signature);

		// Validate and process
		return this.afterCreate(nameUpdate);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForNameUpdate(Name name) {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount owner = new GenesisAccount();

		// Create name update transaction
		UpdateNameTransaction nameUpdate = new UpdateNameTransaction(owner, name, Transaction.MINIMUM_FEE, time, signature, signature);

		return new Pair(nameUpdate.calcRecommendedFee(), nameUpdate.getDataLength());
	}

	public Pair<Transaction, Integer> createNameSale(PrivateKeyAccount owner, NameSale nameSale, BigDecimal fee) {
		// Check we're using latest block
		this.checkBlock();

		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = SellNameTransaction.generateSignature(this.fork, owner, nameSale, fee, time);

		// Create name sale transaction
		SellNameTransaction nameSaleTransaction = new SellNameTransaction(owner, nameSale, fee, time, owner.getLastReference(this.fork), signature);

		// Validate and process
		return this.afterCreate(nameSaleTransaction);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForNameSale(NameSale nameSale) {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount owner = new GenesisAccount();

		// Create name sale transaction
		SellNameTransaction nameSaleTransaction = new SellNameTransaction(owner, nameSale, Transaction.MINIMUM_FEE, time, signature, signature);

		return new Pair(nameSaleTransaction.calcRecommendedFee(), nameSaleTransaction.getDataLength());
	}

	public Pair<Transaction, Integer> createCancelNameSale(PrivateKeyAccount owner, NameSale nameSale, BigDecimal fee) {
		// Check we're using latest block
		this.checkBlock();

		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = CancelSellNameTransaction.generateSignature(this.fork, owner, nameSale.getKey(), fee, time);

		// Create cancel name sale transaction
		CancelSellNameTransaction cancelNameSaleTransaction = new CancelSellNameTransaction(owner, nameSale.getKey(), fee, time,
				owner.getLastReference(this.fork), signature);

		// Validate and process
		return this.afterCreate(cancelNameSaleTransaction);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForCancelNameSale(NameSale nameSale) {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount owner = new GenesisAccount();

		// Create cancel name sale transaction
		CancelSellNameTransaction cancelNameSaleTransaction = new CancelSellNameTransaction(owner, nameSale.getKey(), Transaction.MINIMUM_FEE, time, signature,
				signature);

		return new Pair(cancelNameSaleTransaction.calcRecommendedFee(), cancelNameSaleTransaction.getDataLength());
	}

	public Pair<Transaction, Integer> createNamePurchase(PrivateKeyAccount buyer, NameSale nameSale, BigDecimal fee) {
		// Check we're using latest block
		this.checkBlock();

		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = BuyNameTransaction.generateSignature(this.fork, buyer, nameSale, nameSale.getName().getOwner(), fee, time);

		// Create buy name transaction
		BuyNameTransaction namePurchase = new BuyNameTransaction(buyer, nameSale, nameSale.getName().getOwner(), fee, time, buyer.getLastReference(this.fork),
				signature);

		// Validate and process
		return this.afterCreate(namePurchase);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForNamePurchase(NameSale nameSale) {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount buyer = new GenesisAccount();

		// Create buy name transaction
		BuyNameTransaction namePurchase = new BuyNameTransaction(buyer, nameSale, nameSale.getName().getOwner(), Transaction.MINIMUM_FEE, time, signature,
				signature);

		return new Pair(namePurchase.calcRecommendedFee(), namePurchase.getDataLength());
	}

	public Pair<Transaction, Integer> createPollCreation(PrivateKeyAccount creator, Poll poll, BigDecimal fee) {
		// Check we're using latest block
		this.checkBlock();

		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = CreatePollTransaction.generateSignature(this.fork, creator, poll, fee, time);

		// Create new poll transaction
		CreatePollTransaction pollCreation = new CreatePollTransaction(creator, poll, fee, time, creator.getLastReference(this.fork), signature);

		// Validate and process
		return this.afterCreate(pollCreation);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForPollCreation(Poll poll) {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount creator = new GenesisAccount();

		// Create new poll transaction
		CreatePollTransaction pollCreation = new CreatePollTransaction(creator, poll, Transaction.MINIMUM_FEE, time, signature, signature);

		return new Pair(pollCreation.calcRecommendedFee(), pollCreation.getDataLength());
	}

	public Pair<Transaction, Integer> createPollVote(PrivateKeyAccount creator, String poll, int optionIndex, BigDecimal fee) {
		// Check we're using latest block
		this.checkBlock();

		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = VoteOnPollTransaction.generateSignature(this.fork, creator, poll, optionIndex, fee, time);

		// Create poll vote transaction
		VoteOnPollTransaction pollVote = new VoteOnPollTransaction(creator, poll, optionIndex, fee, time, creator.getLastReference(this.fork), signature);

		// Validate and process
		return this.afterCreate(pollVote);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForPollVote(String poll) {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount creator = new GenesisAccount();

		// Create poll vote transaction
		VoteOnPollTransaction pollVote = new VoteOnPollTransaction(creator, poll, 0, Transaction.MINIMUM_FEE, time, signature, signature);

		return new Pair(pollVote.calcRecommendedFee(), pollVote.getDataLength());
	}

	public Pair<Transaction, Integer> createArbitraryTransaction(PrivateKeyAccount creator, List<Payment> payments, int service, byte[] data, BigDecimal fee) {
		// Check we're using latest block
		this.checkBlock();

		Transaction arbitraryTransaction;

		// Grab timestamp for this transaction
		long time = NTP.getTime();

		if (time < Transaction.getPOWFIX_RELEASE()) {
			// Create transaction signature
			byte[] signature = ArbitraryTransactionV1.generateSignature(this.fork, creator, service, data, fee, time);

			// Create arbitrary transaction v1
			arbitraryTransaction = new ArbitraryTransactionV1(creator, service, data, fee, time, creator.getLastReference(this.fork), signature);
		} else {
			// Create transaction signature
			byte[] signature = ArbitraryTransactionV3.generateSignature(this.fork, creator, payments, service, data, fee, time);

			// Create arbitrary transaction v3
			arbitraryTransaction = new ArbitraryTransactionV3(creator, payments, service, data, fee, time, creator.getLastReference(this.fork), signature);
		}

		// Validate and process
		return this.afterCreate(arbitraryTransaction);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForArbitraryTransaction(byte[] data, List<Payment> payments) {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount creator = new GenesisAccount();

		Transaction arbitraryTransaction;

		if (time < Transaction.getPOWFIX_RELEASE()) {
			// Create arbitrary transaction v1
			arbitraryTransaction = new ArbitraryTransactionV1(creator, 0, data, Transaction.MINIMUM_FEE, time, signature, signature);
		} else {
			// Create arbitrary transaction v3
			arbitraryTransaction = new ArbitraryTransactionV3(creator, payments, 0, data, Transaction.MINIMUM_FEE, time, signature, signature);
		}

		return new Pair(arbitraryTransaction.calcRecommendedFee(), arbitraryTransaction.getDataLength());
	}

	public Pair<Transaction, Integer> createIssueAssetTransaction(PrivateKeyAccount creator, String name, String description, long quantity, boolean divisible,
			BigDecimal fee) {
		// Check we're using latest block
		this.checkBlock();

		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		Asset asset = new Asset(creator, name, description, quantity, divisible, new byte[64]);
		byte[] signature = IssueAssetTransaction.generateSignature(this.fork, creator, asset, fee, time);

		// Create issue asset transaction
		asset = new Asset(creator, name, description, quantity, divisible, signature);
		IssueAssetTransaction issueAssetTransaction = new IssueAssetTransaction(creator, asset, fee, time, creator.getLastReference(this.fork), signature);

		// Validate and process
		return this.afterCreate(issueAssetTransaction);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForIssueAssetTransaction(String name, String description) {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount creator = new GenesisAccount();

		// Create issue asset transaction
		Asset asset = new Asset(creator, name, description, 10000, true, signature);
		IssueAssetTransaction issueAssetTransaction = new IssueAssetTransaction(creator, asset, Transaction.MINIMUM_FEE, time, signature, signature);

		return new Pair(issueAssetTransaction.calcRecommendedFee(), issueAssetTransaction.getDataLength());
	}

	public Pair<Transaction, Integer> createOrderTransaction(PrivateKeyAccount creator, Asset have, Asset want, BigDecimal amount, BigDecimal price,
			BigDecimal fee) {
		// Check we're using latest block
		this.checkBlock();

		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = CreateOrderTransaction.generateSignature(this.fork, creator, have.getKey(), want.getKey(), amount, price, fee, time);

		// Create new order transaction
		CreateOrderTransaction createOrderTransaction = new CreateOrderTransaction(creator, have.getKey(), want.getKey(), amount, price, fee, time,
				creator.getLastReference(this.fork), signature);

		// Validate and process
		return this.afterCreate(createOrderTransaction);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForOrderTransaction() {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount creator = new GenesisAccount();

		// Create new order transaction
		CreateOrderTransaction createOrderTransaction = new CreateOrderTransaction(creator, 0, 0, Transaction.MINIMUM_FEE, Transaction.MINIMUM_FEE,
				Transaction.MINIMUM_FEE, time, signature, signature);

		return new Pair(createOrderTransaction.calcRecommendedFee(), createOrderTransaction.getDataLength());
	}

	public Pair<Transaction, Integer> createCancelOrderTransaction(PrivateKeyAccount creator, Order order, BigDecimal fee) {
		// Check we're using latest block
		this.checkBlock();

		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = CancelOrderTransaction.generateSignature(this.fork, creator, order.getId(), fee, time);

		// Create cancel order transaction
		CancelOrderTransaction cancelOrderTransaction = new CancelOrderTransaction(creator, order.getId(), fee, time, creator.getLastReference(this.fork),
				signature);

		// Validate and process
		return this.afterCreate(cancelOrderTransaction);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForCancelOrderTransaction() {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount creator = new GenesisAccount();

		// Create cancel order transaction
		CancelOrderTransaction cancelOrderTransaction = new CancelOrderTransaction(creator, BigInteger.ONE, Transaction.MINIMUM_FEE, time, signature,
				signature);

		return new Pair(cancelOrderTransaction.calcRecommendedFee(), cancelOrderTransaction.getDataLength());
	}

	public Pair<Transaction, Integer> createAssetTransfer(PrivateKeyAccount sender, Account recipient, Asset asset, BigDecimal amount, BigDecimal fee) {
		// Check we're using latest block
		this.checkBlock();

		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = TransferAssetTransaction.generateSignature(this.fork, sender, recipient, asset.getKey(), amount, fee, time);

		// Create asset transfer transaction
		TransferAssetTransaction assetTransfer = new TransferAssetTransaction(sender, recipient, asset.getKey(), amount, fee, time,
				sender.getLastReference(this.fork), signature);

		// Validate and process
		return this.afterCreate(assetTransfer);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForAssetTransfer() {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount sender = new GenesisAccount();

		// Create asset transfer transaction
		TransferAssetTransaction assetTransfer = new TransferAssetTransaction(sender, sender, 0L, Transaction.MINIMUM_FEE, Transaction.MINIMUM_FEE, time,
				signature, signature);

		return new Pair(assetTransfer.calcRecommendedFee(), assetTransfer.getDataLength());
	}

	public Pair<Transaction, Integer> sendMultiPayment(PrivateKeyAccount sender, List<Payment> payments, BigDecimal fee) {
		// Check we're using latest block
		this.checkBlock();

		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = MultiPaymentTransaction.generateSignature(this.fork, sender, payments, fee, time);

		// Create multi-payment transaction
		MultiPaymentTransaction multiPayment = new MultiPaymentTransaction(sender, payments, fee, time, sender.getLastReference(this.fork), signature);

		// Validate and process
		return this.afterCreate(multiPayment);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForMultiPayment(List<Payment> payments) {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount creator = new GenesisAccount();

		// Create multi-payment transaction
		MultiPaymentTransaction multiPayment = new MultiPaymentTransaction(creator, payments, Transaction.MINIMUM_FEE, time, signature, signature);

		return new Pair(multiPayment.calcRecommendedFee(), multiPayment.getDataLength());
	}

	public Pair<Transaction, Integer> deployATTransaction(PrivateKeyAccount creator, String name, String description, String type, String tags,
			byte[] creationBytes, BigDecimal amount, BigDecimal fee) {
		// Check we're using latest block
		this.checkBlock();

		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = DeployATTransaction.generateSignature(this.fork, creator, name, description, creationBytes, amount, fee, time);

		// Create deploy AT transaction
		DeployATTransaction deployAT = new DeployATTransaction(creator, name, description, type, tags, creationBytes, amount, fee, time,
				creator.getLastReference(this.fork), signature);

		return this.afterCreate(deployAT);

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForDeployATTransaction(String name, String description, String type, String tags, byte[] creationBytes) {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount creator = new GenesisAccount();

		// Create deploy AT transaction
		DeployATTransaction deployAT = new DeployATTransaction(creator, name, description, type, tags, creationBytes, Transaction.MINIMUM_FEE,
				Transaction.MINIMUM_FEE, time, signature, signature);

		return new Pair(deployAT.calcRecommendedFee(), deployAT.getDataLength());
	}

	public Pair<Transaction, Integer> createMessage(PrivateKeyAccount sender, Account recipient, long key, BigDecimal amount, BigDecimal fee, byte[] isText,
			byte[] message, byte[] encryptMessage) {
		// Check we're using latest block
		this.checkBlock();

		Transaction messageTx;

		long timestamp = NTP.getTime();

		if (timestamp < Transaction.getPOWFIX_RELEASE()) {
			// Create message transaction v1
			byte[] signature = MessageTransactionV1.generateSignature(this.fork, sender, recipient, amount, fee, message, isText, encryptMessage, timestamp);
			messageTx = new MessageTransactionV1(sender, recipient, amount, fee, message, isText, encryptMessage, timestamp, sender.getLastReference(this.fork),
					signature);
		} else {
			// Create message transaction v3
			byte[] signature = MessageTransactionV3.generateSignature(this.fork, sender, recipient, key, amount, fee, message, isText, encryptMessage,
					timestamp);
			messageTx = new MessageTransactionV3(sender, recipient, key, amount, fee, message, isText, encryptMessage, timestamp,
					sender.getLastReference(this.fork), signature);
		}

		return afterCreate(messageTx);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Pair<BigDecimal, Integer> calcRecommendedFeeForMessage(byte[] message) {
		// Grab timestamp for this transaction
		long time = NTP.getTime();

		// Create transaction signature
		byte[] signature = new byte[64];

		// Use genesis account as dummy account to calculate fee
		PublicKeyAccount sender = new GenesisAccount();

		Transaction messageTx;

		long timestamp = NTP.getTime();

		if (timestamp < Transaction.getPOWFIX_RELEASE()) {
			// Create message transaction v1
			messageTx = new MessageTransactionV1(sender, sender, Transaction.MINIMUM_FEE, Transaction.MINIMUM_FEE, message, new byte[1], new byte[1], time,
					signature, signature);
		} else {
			// Create message transaction v3
			messageTx = new MessageTransactionV3(sender, sender, 0L, Transaction.MINIMUM_FEE, Transaction.MINIMUM_FEE, message, new byte[1], new byte[1], time,
					signature, signature);
		}

		return new Pair(messageTx.calcRecommendedFee(), messageTx.getDataLength());
	}

	public Pair<Transaction, Integer> createTransactionFromRaw(byte[] rawData) {
		// Check we're using latest block
		this.checkBlock();

		// Attempt to create transaction using raw bytes
		Transaction transaction;
		try {
			transaction = TransactionFactory.getInstance().parse(rawData);
		} catch (Exception e) {
			return new Pair<Transaction, Integer>(null, Transaction.INVALID_RAW_DATA);
		}

		// Validate and process
		return this.afterCreate(transaction);
	}

	private Pair<Transaction, Integer> afterCreate(Transaction transaction) {
		// Validate transaction
		int valid = transaction.isValid(this.fork);

		if (valid == Transaction.VALIDATE_OK) {
			// Check fee is greater than required minimums
			if (!Settings.getInstance().isAllowFeeLessRequired() && !transaction.hasMinimumFeePerByte()) {
				valid = Transaction.FEE_LESS_REQUIRED;
			} else {
				// Process on fork
				transaction.process(this.fork);

				// Call controller's new transaction callback hook
				Controller.getInstance().onTransactionCreate(transaction);
			}
		}

		// Return transaction-status pair
		return new Pair<Transaction, Integer>(transaction, valid);
	}

}