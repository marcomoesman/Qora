package qora.transaction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import controller.Controller;
import database.QoraDb;
import qora.account.Account;
import qora.account.PublicKeyAccount;
import qora.block.Block;
import qora.crypto.Base58;
import settings.Settings;

public abstract class Transaction {
	
	
	protected static final Logger LOGGER = LogManager.getLogger(Transaction.class);
	//VALIDATION CODE
	public static final int VALIDATE_OK = 1;
	public static final int INVALID_ADDRESS = 2;
	public static final int NEGATIVE_AMOUNT = 3;
	public static final int NEGATIVE_FEE = 4;
	public static final int NO_BALANCE = 5;
	public static final int INVALID_REFERENCE = 6;
	
	public static final int INVALID_NAME_LENGTH = 7;
	public static final int INVALID_VALUE_LENGTH = 8;
	public static final int NAME_ALREADY_REGISTRED = 9;
	
	public static final int NAME_DOES_NOT_EXIST = 10;
	public static final int INVALID_NAME_OWNER = 11;
	public static final int NAME_ALREADY_FOR_SALE = 12;
	public static final int NAME_NOT_FOR_SALE = 13;
	public static final int BUYER_ALREADY_OWNER = 14;
	public static final int INVALID_AMOUNT = 15;
	public static final int INVALID_SELLER = 16;
	
	public static final int NAME_NOT_LOWER_CASE = 17;
	
	public static final int INVALID_DESCRIPTION_LENGTH = 18;
	public static final int INVALID_OPTIONS_LENGTH = 19;
	public static final int INVALID_OPTION_LENGTH = 20;
	public static final int DUPLICATE_OPTION = 21;
	public static final int POLL_ALREADY_CREATED = 22;
	public static final int POLL_ALREADY_HAS_VOTES = 23;
	public static final int POLL_NO_EXISTS = 24;
	public static final int OPTION_NO_EXISTS = 25;
	public static final int ALREADY_VOTED_FOR_THAT_OPTION = 26;
	public static final int INVALID_DATA_LENGTH = 27;
	
	public static final int INVALID_QUANTITY = 28;
	public static final int ASSET_DOES_NOT_EXIST = 29;
	public static final int INVALID_RETURN = 30;
	public static final int HAVE_EQUALS_WANT = 31;
	public static final int ORDER_DOES_NOT_EXIST = 32;
	public static final int INVALID_ORDER_CREATOR = 33;
	public static final int INVALID_PAYMENTS_LENGTH = 34;
	public static final int NEGATIVE_PRICE = 35;
	public static final int INVALID_CREATION_BYTES = 36;
	public static final int AT_ERROR = 10000;
	public static final int INVALID_TAGS_LENGTH = 37;
	public static final int INVALID_TYPE_LENGTH = 38;
	
	public static final int FEE_LESS_REQUIRED = 40;
	
	public static final int INVALID_RAW_DATA = 41;
	
	public static final int NOT_YET_RELEASED = 1000;
	
	//TYPES
	public static final int GENESIS_TRANSACTION = 1;
	public static final int PAYMENT_TRANSACTION = 2;
	
	public static final int REGISTER_NAME_TRANSACTION = 3;
	public static final int UPDATE_NAME_TRANSACTION = 4;
	public static final int SELL_NAME_TRANSACTION = 5;
	public static final int CANCEL_SELL_NAME_TRANSACTION = 6;
	public static final int BUY_NAME_TRANSACTION = 7;
	
	public static final int CREATE_POLL_TRANSACTION = 8;
	public static final int VOTE_ON_POLL_TRANSACTION = 9;
	
	public static final int ARBITRARY_TRANSACTION = 10;
	
	public static final int ISSUE_ASSET_TRANSACTION = 11;
	public static final int TRANSFER_ASSET_TRANSACTION = 12;
	public static final int CREATE_ORDER_TRANSACTION = 13;
	public static final int CANCEL_ORDER_TRANSACTION = 14;
	public static final int MULTI_PAYMENT_TRANSACTION = 15;

	public static final int DEPLOY_AT_TRANSACTION = 16;
	
	public static final int MESSAGE_TRANSACTION = 17;
	
	//MINIMUM FEE
	public static final BigDecimal MINIMUM_FEE = BigDecimal.ONE;
	
	//RELEASES
	private static final long VOTING_RELEASE = 1403715600000l;
	private static final long ARBITRARY_TRANSACTIONS_RELEASE = 1405702800000l;
	private static final int AT_BLOCK_HEIGHT_RELEASE = 99000;
	private static final int MESSAGE_BLOCK_HEIGHT_RELEASE = 99000;
	//public static final long ASSETS_RELEASE = 1411308000000l;
	private static final long ASSETS_RELEASE = 0l;
	private static final long POWFIX_RELEASE = 1456426800000L; // Block Version 3 // 2016-02-25T19:00:00+00:00
											   
	public static long getVOTING_RELEASE() {
		if(Settings.getInstance().isTestnet()) {
			return Settings.getInstance().getGenesisStamp();
		}
		return VOTING_RELEASE;
	}

	public static long getARBITRARY_TRANSACTIONS_RELEASE() {
		if(Settings.getInstance().isTestnet()) {
			return Settings.getInstance().getGenesisStamp();
		}
		return ARBITRARY_TRANSACTIONS_RELEASE;
	}

	public static int getAT_BLOCK_HEIGHT_RELEASE() {
		if(Settings.getInstance().isTestnet()) {
			return 1;
		}
		return AT_BLOCK_HEIGHT_RELEASE;
	}
	
	public static int getMESSAGE_BLOCK_HEIGHT_RELEASE() {
		if(Settings.getInstance().isTestnet()) {
			return 1;
		}
		return MESSAGE_BLOCK_HEIGHT_RELEASE;
	}
	
	public static long getASSETS_RELEASE() {
		if(Settings.getInstance().isTestnet()) {
			return Settings.getInstance().getGenesisStamp();
		}
		return ASSETS_RELEASE;
	}
	
	public static long getPOWFIX_RELEASE() {
		if(Settings.getInstance().isTestnet()) {
			return Settings.getInstance().getGenesisStamp();
		}
		return POWFIX_RELEASE;
	}
	
	//PROPERTIES LENGTH
	protected static final int TYPE_LENGTH = 4;
	public static final int TIMESTAMP_LENGTH = 8;
	protected static final int REFERENCE_LENGTH = 64;
		
	protected byte[] reference;
	protected BigDecimal fee;
	protected int type;
	protected byte[] signature;
	protected long timestamp;
	
	protected Transaction(int type, BigDecimal fee, long timestamp, byte[] reference, byte[] signature)
	{
		this.fee = fee;
		this.type = type;
		this.signature = signature;
		this.timestamp = timestamp;
		this.reference = reference;
	}
	
	//GETTERS/SETTERS
	
	public int getType()
	{
		return this.type;
	}
	
	public long getTimestamp()
	{
		return this.timestamp;
	}
	
	public long getDeadline()
	{
		//24HOUR DEADLINE TO INCLUDE TRANSACTION IN BLOCK
		return this.timestamp + (1000*60*60*24);
	}
	
	public BigDecimal getFee()
	{
		return this.fee;
	}
	
	public byte[] getSignature()
	{
		return this.signature;
	}
	
	public byte[] getReference()
	{
		return this.reference;
	}
	
	public BigDecimal feePerByte()
	{
		return this.fee.divide(new BigDecimal(this.getDataLength()), MathContext.DECIMAL32);
	}
	
	public boolean hasMinimumFee()
	{
		return this.fee.compareTo(MINIMUM_FEE) >= 0;
	}
	
	public boolean hasMinimumFeePerByte()
	{
		BigDecimal minFeePerByte = BigDecimal.ONE.divide(BigDecimal.valueOf(Settings.getInstance().getMaxBytePerFee()), MathContext.DECIMAL32);
		
		return this.feePerByte().compareTo(minFeePerByte) >= 0;
	}
	
	public BigDecimal calcRecommendedFee()
	{
		BigDecimal recommendedFee = BigDecimal.valueOf(this.getDataLength()).divide(BigDecimal.valueOf(Settings.getInstance().getMaxBytePerFee()), MathContext.DECIMAL32).setScale(8);

		//security margin
		recommendedFee = recommendedFee.add(new BigDecimal("0.0000001"));
		if(recommendedFee.compareTo(MINIMUM_FEE) <= 0)
		{
			recommendedFee = MINIMUM_FEE;
		}
		else
		{
			recommendedFee = recommendedFee.setScale(0, BigDecimal.ROUND_UP); 
		}
		
		return recommendedFee.setScale(8);
	}
	
	public Block getParent() {
		
		return QoraDb.getInstance().getTransactionParentMap().getParent(this.signature);
	}

	//PARSE/CONVERT
	
	@SuppressWarnings("unchecked")
	protected JSONObject getJsonBase()
	{
		JSONObject transaction = new JSONObject();
		
		transaction.put("type", this.type);
		transaction.put("fee", this.fee.toPlainString());
		transaction.put("timestamp", this.timestamp);
		transaction.put("reference", Base58.encode(this.reference));
		transaction.put("signature", Base58.encode(this.signature));
		transaction.put("confirmations", this.getConfirmations());
		
		return transaction;
	}
	
	public abstract JSONObject toJson();
	
	public abstract byte[] toBytes();
	
	public abstract int getDataLength();
	
	//VALIDATE
	
	public abstract boolean isSignatureValid();
	
	public int isValid()
	{
		return this.isValid(QoraDb.getInstance());
	}
	
	public abstract int isValid(QoraDb db);
	
	//PROCESS/ORPHAN
	
	public void process()
	{
		this.process(QoraDb.getInstance());
	}
		
	public abstract void process(QoraDb db);

	public void orphan()
	{
		this.orphan(QoraDb.getInstance());
	}
	
	public abstract void orphan(QoraDb db);
	
	//REST
	
	public abstract PublicKeyAccount getCreator();
	
	public abstract HashSet<Account> getInvolvedAccounts();
	
	public abstract HashSet<Account> getRecipientAccounts();
		
	public abstract boolean isInvolved(Account account);
	
	public abstract BigDecimal getAmount(Account account);
	
	public int getSeq()
	{
		if(this.isConfirmed())
		{
			return this.getParent().getTransactionSeq(this.signature);
		}
		return -1;
	}
	
	@Override 
	public boolean equals(Object object)
	{
		if(object instanceof Transaction)
		{
			Transaction transaction = (Transaction) object;
			
			return Arrays.equals(this.getSignature(), transaction.getSignature());
		}
		
		return false;
	}

	public boolean isConfirmed()
	{
		return this.isConfirmed(QoraDb.getInstance());
	}
	
	public boolean isConfirmed(QoraDb db)
	{
		return QoraDb.getInstance().getTransactionParentMap().contains(this.getSignature());
	}
	
	public int getConfirmations()
	{
		
		try
		{
		//CHECK IF IN TRANSACTIONDATABASE
		if(QoraDb.getInstance().getTransactionMap().contains(this))
		{
			return 0;
		}
		
		//CALCULATE CONFIRMATIONS
		int lastBlockHeight = QoraDb.getInstance().getHeightMap().get(QoraDb.getInstance().getBlockMap().getLastBlockSignature());
		int transactionBlockHeight = QoraDb.getInstance().getHeightMap().get(QoraDb.getInstance().getTransactionParentMap().getParent(this.signature));
		
		//RETURN
		return 1 + lastBlockHeight - transactionBlockHeight;

		}catch(Exception e)
		{
			LOGGER.error(e.getMessage(),e);
			return 0;
		}
	}

	public int getBlockVersion()
	{
		// IF ALREADY IN THE BLOCK. CONFIRMED 
		if(this.isConfirmed())
		{
			return QoraDb.getInstance().getTransactionParentMap().getParent(this.getSignature()).getVersion();
		}
		
		// IF UNCONFIRMED
		return Controller.getInstance().getLastBlock().getNextBlockVersion(QoraDb.getInstance());	
	}

	public abstract Map<String, Map<Long, BigDecimal>> getAssetAmount();
	
	public static Map<String, Map<Long, BigDecimal>> subAssetAmount(Map<String, Map<Long, BigDecimal>> allAssetAmount, String address, Long assetKey, BigDecimal amount) 
	{
		return addAssetAmount(allAssetAmount, address, assetKey, BigDecimal.ZERO.setScale(8).subtract(amount));
	}
	
	public static Map<String, Map<Long, BigDecimal>> addAssetAmount(Map<String, Map<Long, BigDecimal>> allAssetAmount, String address, Long assetKey, BigDecimal amount) 
	{
		Map<String, Map<Long, BigDecimal>> newAllAssetAmount;
		if(allAssetAmount != null) {
			newAllAssetAmount = new LinkedHashMap<String, Map<Long, BigDecimal>>(allAssetAmount);
		} else {
			newAllAssetAmount = new LinkedHashMap<String, Map<Long, BigDecimal>>();
		}

		Map<Long, BigDecimal> newAssetAmountOfAddress;
		
		if(!newAllAssetAmount.containsKey(address)){
			newAssetAmountOfAddress = new LinkedHashMap<Long, BigDecimal>();
			newAssetAmountOfAddress.put(assetKey, amount);
			
			newAllAssetAmount.put(address, newAssetAmountOfAddress);
		} else {
			if(!newAllAssetAmount.get(address).containsKey(assetKey)) {
				newAssetAmountOfAddress = new LinkedHashMap<Long, BigDecimal>(newAllAssetAmount.get(address));
				newAssetAmountOfAddress.put(assetKey, amount);

				newAllAssetAmount.put(address, newAssetAmountOfAddress);
			} else {
				newAssetAmountOfAddress = new LinkedHashMap<Long, BigDecimal>(newAllAssetAmount.get(address));
				BigDecimal newAmount = newAllAssetAmount.get(address).get(assetKey).add(amount);
				newAssetAmountOfAddress.put(assetKey, newAmount);
				
				newAllAssetAmount.put(address, newAssetAmountOfAddress);
			}
		}
		
		return newAllAssetAmount;
	}
}
