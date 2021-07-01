package controller;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.TrayIcon.MessageType;
import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import javax.servlet.http.HttpServletRequest;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mapdb.Fun.Tuple2;

import com.google.common.primitives.Longs;

import api.ApiClient;
import api.ApiService;
import at.AT;
import database.QoraDb;
import database.LocalDataMap;
import database.SortableList;
import gui.ClosingDialog;
import gui.Gui;
import gui.SplashFrame;
import lang.Lang;
import network.Network;
import network.Peer;
import network.message.BlockMessage;
import network.message.GetBlockMessage;
import network.message.GetSignaturesMessage;
import network.message.HeightMessage;
import network.message.Message;
import network.message.MessageFactory;
import network.message.TransactionMessage;
import network.message.VersionMessage;
import ntp.NTP;
import qora.Blockchain;
import qora.BlockGenerator;
import qora.BlockGenerator.ForgingStatus;
import qora.Synchronizer;
import qora.TransactionCreator;
import qora.account.Account;
import qora.account.PrivateKeyAccount;
import qora.assets.Asset;
import qora.assets.Order;
import qora.assets.Trade;
import qora.block.Block;
import qora.crypto.Base58;
import qora.crypto.Crypto;
import qora.naming.Name;
import qora.naming.NameSale;
import qora.payment.Payment;
import qora.transaction.Transaction;
import qora.voting.Poll;
import qora.voting.PollOption;
import qora.wallet.Wallet;
import settings.Settings;
import utils.DateTimeFormat;
import utils.ObserverMessage;
import utils.Pair;
import utils.SysTray;
import utils.TransactionTimestampComparator;
import utils.UpdateUtil;
import webserver.WebService;

public final class Controller extends Observable {

	private static final Logger LOGGER = LogManager.getLogger(Controller.class);
	private static final String VERSION = "0.26.12-rc2";
	private static final String BUILD_TIME = "2021-07-01 14:00:00 UTC";

	// TODO ENUM would be better here
	public static final int STATUS_NO_CONNECTIONS = 0;
	public static final int STATUS_SYNCHRONIZING = 1;
	public static final int STATUS_OK = 2;

	private final Random random = new SecureRandom();
	
	private boolean processingWalletSynchronize = false;
	private int status;
	private Network network;
	private ApiService rpcService;
	private WebService webService;
	private Blockchain blockchain;
	private BlockGenerator blockGenerator;
	private Wallet wallet;
	private Synchronizer synchronizer;
	private TransactionCreator transactionCreator;
	private Timer timer = new Timer();
	private Timer timerPeerHeightUpdate = new Timer();
	private boolean needSync = false;
	private byte[] foundMyselfID = new byte[128];
	private byte[] messageMagic;
	private long toOfflineTime;
	private long buildTimestamp;

	private Map<Peer, Integer> peerHeight;

	private Map<Peer, Pair<String, Long>> peersVersions;

	private static Controller instance;

	public boolean isProcessingWalletSynchronize() {
		return processingWalletSynchronize;
	}

	public void setProcessingWalletSynchronize(boolean isPocessing) {
		this.processingWalletSynchronize = isPocessing;
	}

	public String getVersion() {
		return VERSION;
	}

	public int getNetworkPort() {
		if (Settings.getInstance().isTestnet()) {
			return Network.TESTNET_PORT;
		} else {
			return Network.MAINNET_PORT;
		}
	}

	public String getBuildDateTimeString() {
		return DateTimeFormat.timestamptoString(getBuildTimestamp(), "yyyy-MM-dd HH:mm:ss z", "UTC");
	}

	public String getBuildDateString() {
		return DateTimeFormat.timestamptoString(getBuildTimestamp(), "yyyy-MM-dd", "UTC");
	}

	public long getBuildTimestamp() {
		if (this.buildTimestamp == 0) {
			Date date = new Date();
			URL resource = getClass().getResource(getClass().getSimpleName() + ".class");

			if (resource == null || !resource.getProtocol().equals("file")) {
				// Use compiled buildTime
				DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
				try {
					date = (Date) formatter.parse(BUILD_TIME);
				} catch (ParseException e) {
					LOGGER.error(e.getMessage(), e);
				}
			}

			this.buildTimestamp = date.getTime();
		}
		return this.buildTimestamp;
	}

	public byte[] getMessageMagic() {
		if (this.messageMagic == null) {
			if (Settings.getInstance().isTestnet()) {
				long longTestNetStamp = Settings.getInstance().getGenesisStamp();
				byte[] seedTestNetStamp = Crypto.getInstance().digest(Longs.toByteArray(longTestNetStamp));
				this.messageMagic = Arrays.copyOfRange(seedTestNetStamp, 0, Message.MAGIC_LENGTH);
			} else {
				this.messageMagic = Message.MAINNET_MAGIC;
			}
		}
		return this.messageMagic;
	}

	public void logStatusInfo() {
		LOGGER.info(Lang.getInstance().translate("STATUS OK") + "\n" + "| " + Lang.getInstance().translate("Last Block Signature") + ": "
				+ Base58.encode(this.blockchain.getLastBlock().getSignature()) + "\n" + "| " + Lang.getInstance().translate("Last Block Height") + ": "
				+ this.blockchain.getLastBlock().getHeight() + "\n" + "| " + Lang.getInstance().translate("Last Block Time") + ": "
				+ DateTimeFormat.timestamptoString(this.blockchain.getLastBlock().getTimestamp()) + "\n" + "| " + Lang.getInstance()
						.translate("Last Block Found %time% ago.").replace("%time%", DateTimeFormat.timeAgo(this.blockchain.getLastBlock().getTimestamp())));
	}

	public byte[] getFoundMyselfID() {
		return this.foundMyselfID;
	}

	public int getWalletSyncHeight() {
		return this.wallet.getSyncHeight();
	}

	public void sendOurHeightToPeer(final Peer peer) {
		// Get height
		final int height = this.blockchain.getHeight();

		// Send height message
		peer.sendMessage(MessageFactory.getInstance().createHeightMessage(height));
	}

	public Map<Peer, Integer> getPeerHeights() {
		return peerHeight;
	}

	public Integer getHeightOfPeer(final Peer peer) {
		synchronized (this.peerHeight) {
			if (peerHeight == null || !peerHeight.containsKey(peer)) {
				return 0;
			}

			return peerHeight.get(peer);
		}
	}

	public Map<Peer, Pair<String, Long>> getPeersVersions() {
		return peersVersions;
	}

	public Pair<String, Long> getVersionOfPeer(Peer peer) {
		if (peersVersions == null || !peersVersions.containsKey(peer))
			return new Pair<String, Long>("", 0L);

		return peersVersions.get(peer);
	}

	public static Controller getInstance() {
		if (instance == null) {
			instance = new Controller();
		}

		return instance;
	}

	public int getStatus() {
		return this.status;
	}

	public void setNeedSync(boolean needSync) {
		this.needSync = needSync;
	}

	public boolean isNeedSync() {
		return this.needSync;
	}

	public void start() throws Exception {
		this.toOfflineTime = NTP.getTime();

		this.foundMyselfID = new byte[128];
		this.random.nextBytes(this.foundMyselfID);

		// CHECK NETWORK PORT AVAILABLE
		if (!Network.isPortAvailable(Controller.getInstance().getNetworkPort())) {
			throw new Exception(Lang.getInstance().translate("Network port %port% already in use!").replace("%port%",
					String.valueOf(Controller.getInstance().getNetworkPort())));
		}

		// CHECK RPC PORT AVAILABLE
		if (Settings.getInstance().isRpcEnabled()) {
			if (!Network.isPortAvailable(Settings.getInstance().getRpcPort())) {
				throw new Exception(
						Lang.getInstance().translate("Rpc port %port% already in use!").replace("%port%", String.valueOf(Settings.getInstance().getRpcPort())));
			}
		}

		// CHECK WEB PORT AVAILABLE
		if (Settings.getInstance().isWebEnabled()) {
			if (!Network.isPortAvailable(Settings.getInstance().getWebPort())) {
				LOGGER.error(
						Lang.getInstance().translate("Web port %port% already in use!").replace("%port%", String.valueOf(Settings.getInstance().getWebPort())));
			}
		}

		// LINKED HashMap to preserve longest-connection-first ordering
		this.peerHeight = new LinkedHashMap<Peer, Integer>();

		this.peersVersions = new LinkedHashMap<Peer, Pair<String, Long>>();

		this.status = STATUS_NO_CONNECTIONS;
		this.transactionCreator = new TransactionCreator();

		SplashFrame.getInstance().updateProgress("Opening databases");

		// OPENING DATABASES
		try {
			QoraDb.getInstance();
		} catch (Throwable e) {
			LOGGER.error(e.getMessage(), e);
			LOGGER.info(Lang.getInstance().translate("Error during startup detected trying to restore backup database..."));

			SplashFrame.getInstance().updateProgress("Creating databases");
			recreateDatabase();
		}

		// startFromScratchOnDemand();

		// If BlockMap database was closed while processing a block
		// then assume all databases are corrupt and rebuild from scratch
		if (QoraDb.getInstance().getBlockMap().isProcessing()) {
			try {
				QoraDb.getInstance().close();
			} catch (Throwable e) {
				LOGGER.error(e.getMessage(), e);
			}

			SplashFrame.getInstance().updateProgress("Recreating databases");
			recreateDatabase();
		}

		// Check whether DB needs updates
		if (QoraDb.getInstance().getBlockMap().getLastBlockSignature() != null) {
			LocalDataMap localDataMap = QoraDb.getInstance().getLocalDataMap();

			// Check whether name storage needs rebuilding
			if (localDataMap.get("nsupdate") == null || !localDataMap.get("nsupdate").equals("2")) {
				SplashFrame.getInstance().updateProgress("Rebuilding name storage");

				// Rebuild name storage
				UpdateUtil.repopulateNameStorage(70000); // Don't bother scanning blocks below height 70,000
				localDataMap.set("nsupdate", "2");
			}
			// Check whether final transaction map needs rebuilding
			if (localDataMap.get("txfinalmap") == null || !localDataMap.get("txfinalmap").equals("2")) {
				SplashFrame.getInstance().updateProgress("Rebuilding transaction-block mapping");

				// Rebuild final transaction map
				UpdateUtil.repopulateTransactionFinalMap();
				localDataMap.set("txfinalmap", "2");
			}

			if (localDataMap.get("blogpostmap") == null || !localDataMap.get("blogpostmap").equals("3")) {
				SplashFrame.getInstance().updateProgress("Rebuilding blog comments");

				// Recreate comment postmap
				UpdateUtil.repopulateCommentPostMap();
				localDataMap.set("blogpostmap", "3");
			}
		} else {
			QoraDb.getInstance().getLocalDataMap().set("nsupdate", "2");
			QoraDb.getInstance().getLocalDataMap().set("txfinalmap", "2");
			QoraDb.getInstance().getLocalDataMap().set("blogpostmap", "3");
		}

		// CREATE SYNCHRONIZOR
		SplashFrame.getInstance().updateProgress("Starting synchronizer");
		this.synchronizer = new Synchronizer();

		// CREATE BLOCKCHAIN
		SplashFrame.getInstance().updateProgress("Starting blockchain");
		this.blockchain = new Blockchain();

		// START API SERVICE
		if (Settings.getInstance().isRpcEnabled()) {
			SplashFrame.getInstance().updateProgress("Starting RPC API");
			this.rpcService = new ApiService();
			this.rpcService.start();
		}

		// START WEB SERVICE
		if (Settings.getInstance().isWebEnabled()) {
			SplashFrame.getInstance().updateProgress("Starting web service");
			this.webService = new WebService();
			this.webService.start();
		}

		// CREATE WALLET
		SplashFrame.getInstance().updateProgress("Starting wallet");
		this.wallet = new Wallet();

		if (this.wallet.isWalletDatabaseExisting()) {
			this.wallet.initiateAssetsFavorites();
		}

		if (Settings.getInstance().isTestnet() && this.wallet.isWalletDatabaseExisting() && this.wallet.getAccounts().size() > 0) {
			this.wallet.synchronize();
		}

		// CREATE BLOCKGENERATOR
		SplashFrame.getInstance().updateProgress("Starting block generator");
		this.blockGenerator = new BlockGenerator();
		// START BLOCKGENERATOR
		this.blockGenerator.start();

		// CREATE NETWORK
		SplashFrame.getInstance().updateProgress("Starting networking");
		this.network = new Network();

		// CLOSE ON UNEXPECTED SHUTDOWN
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				Thread.currentThread().setName("Controller Shutdown");
				stopAll();
			}
		});

		// TIMER TO SEND HEIGHT TO RANDOM PEER

		this.timerPeerHeightUpdate.cancel();
		this.timerPeerHeightUpdate = new Timer();

		TimerTask action = new TimerTask() {
			public void run() {
				if (Controller.getInstance().getStatus() == STATUS_OK) {
					if (Controller.getInstance().getActivePeers().size() > 0) {
						Peer peer = Controller.getInstance().getActivePeers().get(random.nextInt(Controller.getInstance().getActivePeers().size()));
						if (peer != null) {
							Controller.getInstance().sendOurHeightToPeer(peer);
						}
					}
				}
			}
		};

		this.timerPeerHeightUpdate.schedule(action, 30 * 1000, 30 * 1000);

		// REGISTER DATABASE OBSERVER
		this.addObserver(QoraDb.getInstance().getTransactionMap());
		this.addObserver(QoraDb.getInstance());
	}

	public void replaseAssetsFavorites() {
		if (this.wallet != null) {
			this.wallet.replaseAssetFavorite();
		}
	}

	public void recreateDatabase() throws IOException, Exception {
		recreateDatabase(true);
	}

	public void recreateDatabase(final boolean fromBackup) throws IOException, Exception {
		final File dataDir = new File(Settings.getInstance().getDataDir());
		if (dataDir.exists()) {
			// Delete data folder (if any)
			QoraDb.deleteDataFolder();

			// Try to use backup?
			final File dataBak = getDataBakDir(dataDir);
			if (fromBackup && dataBak.exists() && Settings.getInstance().isCheckpointingEnabled()) {
				FileUtils.copyDirectory(dataBak, dataDir); // Assumes dataDir exists

				LOGGER.info(Lang.getInstance().translate("restoring backup database"));

				try {
					QoraDb.createDatabase();
				} catch (IOError e) {
					LOGGER.error(e.getMessage(), e);

					// backup DB is buggy too - start from scratch

					// delete data folder
					QoraDb.deleteDataFolder();

					// delete backup data folder
					QoraDb.deleteDataBackup();

					QoraDb.createDatabase();
				}
			} else {
				QoraDb.createDatabase();
			}
		} else {
			QoraDb.createDatabase();
		}

		if (QoraDb.getInstance().getBlockMap().isProcessing()) {
			throw new Exception(Lang.getInstance().translate("The application was not closed correctly! Delete the folder ") + dataDir.getAbsolutePath()
					+ Lang.getInstance().translate(" and start the application again."));
		}
	}

	public void startFromScratchOnDemand() throws IOException {
		String dataVersion = QoraDb.getInstance().getLocalDataMap().get(LocalDataMap.LOCAL_DATA_VERSION_KEY);

		if (dataVersion == null || !dataVersion.equals(VERSION)) {
			QoraDb.getInstance().close();

			// delete data folder
			QoraDb.deleteDataFolder();

			// delete backup data folder
			QoraDb.deleteDataBackup();

			QoraDb.createDatabase();

			QoraDb.getInstance().getLocalDataMap().set(LocalDataMap.LOCAL_DATA_VERSION_KEY, Controller.VERSION);

		}
	}

	private File getDataBakDir(File dataDir) {
		return new File(dataDir.getParent(), "dataBak");
	}

	public void rpcServiceRestart() {
		this.rpcService.stop();

		// START API SERVICE
		if (Settings.getInstance().isRpcEnabled()) {
			this.rpcService = new ApiService();
			this.rpcService.start();
		}
	}

	public void webServiceRestart() {
		this.webService.stop();

		// START API SERVICE
		if (Settings.getInstance().isWebEnabled()) {
			this.webService = new WebService();
			this.webService.start();
		}
	}

	@Override
	public void addObserver(Observer o) {
		// ADD OBSERVER TO SYNCHRONIZER
		// this.synchronizer.addObserver(o);
		QoraDb.getInstance().getBlockMap().addObserver(o);

		// ADD OBSERVER TO BLOCKGENERATOR
		// this.blockGenerator.addObserver(o);
		QoraDb.getInstance().getTransactionMap().addObserver(o);

		// ADD OBSERVER TO NAMESALES
		QoraDb.getInstance().getNameExchangeMap().addObserver(o);

		// ADD OBSERVER TO POLLS
		QoraDb.getInstance().getPollMap().addObserver(o);

		// ADD OBSERVER TO ASSETS
		QoraDb.getInstance().getAssetMap().addObserver(o);

		// ADD OBSERVER TO ORDERS
		QoraDb.getInstance().getOrderMap().addObserver(o);

		// ADD OBSERVER TO TRADES
		QoraDb.getInstance().getTradeMap().addObserver(o);

		// ADD OBSERVER TO BALANCES
		QoraDb.getInstance().getBalanceMap().addObserver(o);

		// ADD OBSERVER TO ATMAP
		QoraDb.getInstance().getATMap().addObserver(o);

		// ADD OBSERVER TO ATTRANSACTION MAP
		QoraDb.getInstance().getATTransactionMap().addObserver(o);

		// ADD OBSERVER TO CONTROLLER
		super.addObserver(o);
		o.update(this, new ObserverMessage(ObserverMessage.NETWORK_STATUS, this.status));
	}

	@Override
	public void deleteObserver(Observer o) {
		QoraDb.getInstance().getBlockMap().deleteObserver(o);

		super.deleteObserver(o);
	}

	public void deleteWalletObserver(Observer o) {
		this.wallet.deleteObserver(o);
	}

	private boolean isStopping = false;
	private Object stoppingLock = new Object();

	public void stopAll() {
		// Prevent multiple calls.
		// This method can be called via JVM shutdown hook (e.g. signal), API 'stop' or GUI 'exit', among others.
		// In particular, ClosingDialog.getInstance() below can trigger a call to stopAll().
		// We want all successive calls to block until the first call has finished.
		synchronized (this.stoppingLock) {
			if (!this.isStopping) {
				this.isStopping = true;

				// STOP SENDING OUR HEIGHT TO PEERS
				this.timerPeerHeightUpdate.cancel();

				// STOP BLOCK PROCESSOR
				LOGGER.info(Lang.getInstance().translate("Stopping block processor"));
				ClosingDialog.getInstance().updateProgress("Stopping block processor");
				this.synchronizer.stop();

				// STOP BLOCK GENERATOR
				LOGGER.info(Lang.getInstance().translate("Stopping block generator"));
				ClosingDialog.getInstance().updateProgress("Stopping block generator");
				this.blockGenerator.shutdown();

				// STOP MESSAGE PROCESSOR
				LOGGER.info(Lang.getInstance().translate("Stopping message processor"));
				ClosingDialog.getInstance().updateProgress("Stopping message processor");
				this.network.stop();

				// CLOSE DATABASE
				LOGGER.info(Lang.getInstance().translate("Closing database (this may take some time)"));
				ClosingDialog.getInstance().updateProgress("Closing database (this may take some time)");
				QoraDb.getInstance().close();

				// CLOSE WALLET
				LOGGER.info(Lang.getInstance().translate("Closing wallet"));
				ClosingDialog.getInstance().updateProgress("Closing wallet");
				this.wallet.close();

				ClosingDialog.getInstance().updateProgress("Creating database backup");
				createDataCheckpoint();

				LOGGER.info(Lang.getInstance().translate("Closed."));
			}
		}
	}

	private void createDataCheckpoint() {
		if (!QoraDb.getInstance().getBlockMap().isProcessing() && Settings.getInstance().isCheckpointingEnabled()) {
			QoraDb.getInstance().close();

			File dataDir = new File(Settings.getInstance().getDataDir());

			File dataBak = getDataBakDir(dataDir);

			// delete old backup (if any)
			QoraDb.deleteDataBackup();

			// copy existing DB as backup
			try {
				FileUtils.copyDirectory(dataDir, dataBak);
			} catch (IOException e) {
				LOGGER.error(e.getMessage(), e);
			}
		}

	}

	// NETWORK

	public List<Peer> getActivePeers() {
		// Get active peers
		return this.network.getActiveConnections();
	}

	public void walletSyncStatusUpdate(final int height) {
		/*
		 * Prevent deadlock when a new block arrives from network while we're resyncing wallet.
		 * 
		 * New block arrival locks Controller and wants Synchronizer, 
		 * but it's possible Synchronizer is locked (e.g. by BlockGenerator) while performing a wallet sync 
		 * and this.setChanged() would want a lock on Controller too, causing a deadlock.
		 * 
		 * We avoid this by testing for block processing status and exiting early.
		 */

		if (QoraDb.getInstance().getBlockMap().isProcessing()) {
			return;
		}

		setChanged();
		notifyObservers(new ObserverMessage(ObserverMessage.WALLET_SYNC_STATUS, height));
	}

	public void blockchainSyncStatusUpdate(final int height) {
		setChanged();
		notifyObservers(new ObserverMessage(ObserverMessage.BLOCKCHAIN_SYNC_STATUS, height));
	}

	public long getToOfflineTime() {
		return this.toOfflineTime;
	}

	public void setToOfflineTime(final long time) {
		this.toOfflineTime = time;
	}

	public void onConnect(final Peer peer) {
		if (QoraDb.getInstance().isStopped()) {
			return;
		}

		if (NTP.getTime() >= Transaction.getPOWFIX_RELEASE()) {
			// Send found myself ID
			peer.sendMessage(MessageFactory.getInstance().createFindMyselfMessage(Controller.getInstance().getFoundMyselfID()));

			// Send version message
			peer.sendMessage(MessageFactory.getInstance().createVersionMessage(Controller.getInstance().getVersion(), getBuildTimestamp()));
		}

		// Send our height to peer
		sendOurHeightToPeer(peer);

		// Resend any unconfirmed transactions
		List<Transaction> transactions = QoraDb.getInstance().getTransactionMap().getTransactions();

		// Sort transactions chronologically
		Collections.sort(transactions, new TransactionTimestampComparator());

		// Send unconfirmed transactions
		for (final Transaction transaction : transactions) {
			final Message message = MessageFactory.getInstance().createTransactionMessage(transaction);
			peer.sendMessage(message);
		}

		if (this.status == STATUS_NO_CONNECTIONS) {
			// UPDATE STATUS
			this.status = STATUS_OK;

			// NOTIFY
			setChanged();
			notifyObservers(new ObserverMessage(ObserverMessage.NETWORK_STATUS, this.status));

			actionAfterConnect();
		}
	}

	public void actionAfterConnect() {
		this.timer.cancel();
		this.timer = new Timer();

		TimerTask action = new TimerTask() {
			public void run() {

				if (Controller.getInstance().getStatus() == STATUS_OK) {
					Controller.getInstance().logStatusInfo();

					Controller.getInstance().setToOfflineTime(0L);

					if (Controller.getInstance().isNeedSync() && !Controller.getInstance().isProcessingWalletSynchronize()) {
						Controller.getInstance().synchronizeWallet();
					}
				}
			}
		};

		this.timer.schedule(action, Settings.getInstance().getConnectionTimeout());
	}

	public void forgingStatusChanged(ForgingStatus status) {
		this.setChanged();
		this.notifyObservers(new ObserverMessage(ObserverMessage.FORGING_STATUS, status));
	}

	public void onDisconnect(Peer peer) {
		synchronized (this.peerHeight) {
			this.peerHeight.remove(peer);
			this.peersVersions.remove(peer);

			if (this.peerHeight.size() == 0) {
				if (this.getToOfflineTime() == 0L) {
					// SET START OFFLINE TIME
					this.setToOfflineTime(NTP.getTime());
				}

				// UPDATE STATUS
				this.status = STATUS_NO_CONNECTIONS;

				// If we're shutting down then don't notify observers
				// in case they attempt to access DB
				if (this.isStopping)
					return;
			}
		}

		// NOTIFY, but in separate thread to avoid MapDB interrupt issue
		new Thread() {
			@Override
			public void run() {
				Controller.getInstance().setChanged();
				Controller.getInstance().notifyObservers(new ObserverMessage(ObserverMessage.NETWORK_STATUS, Controller.getInstance().status));
			}
		}.start();
	}

	public void onError(Peer peer) {
		this.onDisconnect(peer);
	}

	// SYNCHRONIZED DO NOT PROCESS MESSAGES SIMULTANEOUSLY
	public void onMessage(Message message) {
		Message response;
		Block block;

		synchronized (this) {
			switch (message.getType()) {
				case Message.PING_TYPE:

					// CREATE PING
					response = MessageFactory.getInstance().createPingMessage();

					// SET ID
					response.setId(message.getId());

					// SEND BACK TO SENDER
					message.getSender().sendMessage(response);

					break;

				case Message.HEIGHT_TYPE:

					HeightMessage heightMessage = (HeightMessage) message;
					LOGGER.trace("Received height " + heightMessage.getHeight() + " from peer " + heightMessage.getSender().getAddress());

					// ADD TO LIST
					synchronized (this.peerHeight) {
						this.peerHeight.put(heightMessage.getSender(), heightMessage.getHeight());
					}

					break;

				case Message.GET_SIGNATURES_TYPE:

					// Don't send if we're synchronizing
					if (this.status == STATUS_SYNCHRONIZING)
						break;

					GetSignaturesMessage getHeadersMessage = (GetSignaturesMessage) message;

					// ASK SIGNATURES FROM BLOCKCHAIN
					List<byte[]> headers = this.blockchain.getSignatures(getHeadersMessage.getParent());
					LOGGER.trace("Found " + headers.size() + " block signatures to send to " + message.getSender().getAddress());

					// CREATE RESPONSE WITH SAME ID
					response = MessageFactory.getInstance().createHeadersMessage(headers);
					response.setId(message.getId());

					// SEND RESPONSE BACK WITH SAME ID
					message.getSender().sendMessage(response);

					break;

				case Message.GET_BLOCK_TYPE:

					// Don't send if we're synchronizing
					if (this.status == STATUS_SYNCHRONIZING)
						break;

					GetBlockMessage getBlockMessage = (GetBlockMessage) message;

					// ASK BLOCK FROM BLOCKCHAIN
					block = this.blockchain.getBlock(getBlockMessage.getSignature());

					// CREATE RESPONSE WITH SAME ID
					response = MessageFactory.getInstance().createBlockMessage(block);
					response.setId(message.getId());

					// SEND RESPONSE BACK WITH SAME ID
					message.getSender().sendMessage(response);

					break;

				case Message.BLOCK_TYPE:
					// Don't process if we're synchronizing
					if (this.status == STATUS_SYNCHRONIZING) {
						break;
					}

					final BlockMessage blockMessage = (BlockMessage) message;

					// Get block from message
					block = blockMessage.getBlock();
					LOGGER.trace("Received block from peer " + message.getSender().getAddress());

					// Compare to our blockchain tip
					final Block blockchainTip = this.blockchain.getLastBlock();
					if (blockchainTip.getHeight() == blockMessage.getHeight() && Arrays.equals(blockchainTip.getSignature(), block.getSignature())) {
						// We have this block already but update our peer DB to reflect peer's height anyway
						synchronized (this.peerHeight) {
							this.peerHeight.put(message.getSender(), blockMessage.getHeight());
						}
						break;
					}

					final boolean isNewBlockValid = this.blockchain.isNewBlockValid(block);

					if (isNewBlockValid) {
						synchronized (this.peerHeight) {
							this.peerHeight.put(message.getSender(), blockMessage.getHeight());
						}
					}

					if (isProcessingWalletSynchronize()) {
						break;
					}

					/*
					 * Prevent deadlock when a new block arrives from network while we're resyncing wallet.
					 * 
					 * New block arrival locks Controller and wants Synchronizer, 
					 * but it's possible Synchronizer is locked (e.g. by BlockGenerator) while performing a wallet sync 
					 * and this.setChanged() would want a lock on Controller too, causing a deadlock.
					 * 
					 * We avoid this by testing for block processing status and exiting early.
					 */
					if (QoraDb.getInstance().getBlockMap().isProcessing()) {
						break;
					}

					// Check if block is valid
					if (isNewBlockValid && this.synchronizer.process(block)) {
						LOGGER.info(Lang.getInstance().translate("Received new block") + " " + block.getHeight() + " (" + block.getTransactionCount() + " transactions)");

						// Broadcast to peers
						final List<Peer> excludes = new ArrayList<Peer>();
						excludes.add(message.getSender());
						this.network.broadcast(message, excludes);

						// Let sender know we've updated
						sendOurHeightToPeer(message.getSender());
					}
					break;

				case Message.TRANSACTION_TYPE:

					TransactionMessage transactionMessage = (TransactionMessage) message;

					// GET TRANSACTION
					Transaction transaction = transactionMessage.getTransaction();

					// CHECK IF SIGNATURE IS VALID OR GENESIS TRANSACTION
					if (!transaction.isSignatureValid() || transaction.getType() == Transaction.GENESIS_TRANSACTION) {
						// DISHONEST PEER
						this.network.onError(message.getSender(), Lang.getInstance().translate("invalid transaction signature"));

						return;
					}

					// CHECK IF TRANSACTION HAS MINIMUM FEE AND MINIMUM FEE PER BYTE
					// AND UNCONFIRMED
					if (transaction.hasMinimumFee() && transaction.hasMinimumFeePerByte()
							&& !QoraDb.getInstance().getTransactionParentMap().contains(transaction.getSignature())) {
						// ADD TO UNCONFIRMED TRANSACTIONS
						this.blockGenerator.addUnconfirmedTransaction(transaction);

						// NOTIFY OBSERVERS
						// this.setChanged();
						// this.notifyObservers(new
						// ObserverMessage(ObserverMessage.LIST_TRANSACTION_TYPE,
						// DatabaseSet.getInstance().getTransactionsDatabase().getTransactions()));

						this.setChanged();
						this.notifyObservers(new ObserverMessage(ObserverMessage.ADD_TRANSACTION_TYPE, transaction));

						// BROADCAST
						List<Peer> excludes = new ArrayList<Peer>();
						excludes.add(message.getSender());
						this.network.broadcast(message, excludes);
					}

					break;

				case Message.VERSION_TYPE:

					VersionMessage versionMessage = (VersionMessage) message;

					// ADD TO LIST
					synchronized (this.peersVersions) {
						this.peersVersions.put(versionMessage.getSender(),
								new Pair<String, Long>(versionMessage.getStrVersion(), versionMessage.getBuildDateTime()));
					}

					break;
			}
		}
	}

	public void addActivePeersObserver(Observer o) {
		this.network.addObserver(o);
	}

	public void removeActivePeersObserver(Observer o) {
		this.network.deleteObserver(o);
	}

	private void broadcastBlock(Block newBlock) {

		// CREATE MESSAGE
		Message message = MessageFactory.getInstance().createBlockMessage(newBlock);

		// BROADCAST MESSAGE
		List<Peer> excludes = new ArrayList<Peer>();
		this.network.broadcast(message, excludes);
	}

	private void broadcastTransaction(Transaction transaction) {

		if (Controller.getInstance().getStatus() == Controller.STATUS_OK) {
			// CREATE MESSAGE
			Message message = MessageFactory.getInstance().createTransactionMessage(transaction);

			// BROADCAST MESSAGE
			List<Peer> excludes = new ArrayList<Peer>();
			this.network.broadcast(message, excludes);
		}
	}

	// SYNCHRONIZE

	public boolean isUpToDate() {
		if (this.peerHeight.size() == 0)
			return true;

		int maxPeerHeight = this.getMaxPeerHeight();
		int chainHeight = this.blockchain.getHeight();
		return chainHeight >= maxPeerHeight;
	}

	public boolean isNSUpToDate() {
		return !Settings.getInstance().updateNameStorage();
	}

	public void update() {
		// UPDATE STATUS
		this.status = STATUS_SYNCHRONIZING;

		// NOTIFY
		this.setChanged();
		this.notifyObservers(new ObserverMessage(ObserverMessage.NETWORK_STATUS, this.status));

		Peer peer = null;
		try {
			// Synchronize while we're not up-to-date
			// (but bail out if we're shutdown while updating blockchain)
			while (!this.isStopping && !this.isUpToDate()) {
				// START UPDATE FROM HIGHEST HEIGHT PEER
				peer = this.getMaxHeightPeer();

				if (peer != null) {
					// Make a note of pre-sync height so we can tell if anything happened
					int preSyncHeight = this.blockchain.getHeight();

					// SYNCHRONIZE FROM PEER
					LOGGER.info("Synchronizing using peer " + peer.getAddress().getHostAddress() + " with height " + peerHeight.get(peer) + " - ping " + peer.getPing() + "ms");
					this.synchronizer.synchronize(peer);

					// If our height has changed, notify our peers
					if (this.blockchain.getHeight() > preSyncHeight) {
						Block blockchainTip = this.blockchain.getLastBlock();
						LOGGER.debug("Sending our new height " + blockchainTip.getHeight() + " to peers");
						Message message = MessageFactory.getInstance().createHeightMessage(blockchainTip.getHeight());

						List<Peer> excludes = new ArrayList<Peer>();
						this.network.broadcast(message, excludes);
					}
				}

				Thread.sleep(5 * 1000);
			}
		} catch (InterruptedException e) {
			return;
		} catch (Exception e) {
			LOGGER.debug(e.getMessage());

			if (peer != null) {
				// DISHONEST PEER
				this.network.onError(peer, e.getMessage());
			}

			return;
		}

		if (this.peerHeight.size() == 0) {
			// UPDATE STATUS
			this.status = STATUS_NO_CONNECTIONS;

			// NOTIFY
			this.setChanged();
			this.notifyObservers(new ObserverMessage(ObserverMessage.NETWORK_STATUS, this.status));
		} else {
			// UPDATE STATUS
			this.status = STATUS_OK;

			// NOTIFY
			this.setChanged();
			this.notifyObservers(new ObserverMessage(ObserverMessage.NETWORK_STATUS, this.status));

			Controller.getInstance().logStatusInfo();
		}
	}

	private Peer getMaxHeightPeer() {
		Peer highestPeer = null;
		// needs to be better than our height
		int bestHeight = this.blockchain.getHeight() + 1;
		long bestPing = Long.MAX_VALUE;

		try {
			synchronized (this.peerHeight) {
				for (Peer peer : this.peerHeight.keySet()) {
					int height = this.peerHeight.get(peer);
					long ping = peer.getPing();

					// No ping yet? skip it
					if (ping == Long.MAX_VALUE)
						continue;

					// Favour greatest height, failing that: lowest ping
					if (height > bestHeight || (height == bestHeight && ping < bestPing)) {
						highestPeer = peer;
						bestHeight = height;
						bestPing = ping;
					}
				}
			}
		} catch (Exception e) {
			// PEER REMOVED WHILE ITERATING
		}

		return highestPeer;
	}

	public int getMaxPeerHeight() {
		int bestHeight = 0;

		try {
			synchronized (this.peerHeight) {
				for (Peer peer : this.peerHeight.keySet()) {
					int height = this.peerHeight.get(peer);

					if (height > bestHeight)
						bestHeight = height;
				}
			}
		} catch (Exception e) {
			// PEER REMOVED WHILE ITERATING
		}

		return bestHeight;
	}

	// WALLET

	public boolean doesWalletExists() {
		// CHECK IF WALLET EXISTS
		return this.wallet != null && this.wallet.exists();
	}

	public boolean doesWalletDatabaseExists() {
		return wallet != null && this.wallet.isWalletDatabaseExisting();
	}

	public boolean createWallet(byte[] seed, String password, int amount) {
		// IF NEW WALLET CREADED
		return this.wallet.create(seed, password, amount, false);
	}

	public boolean recoverWallet(byte[] seed, String password, int amount) {
		if (this.wallet.create(seed, password, amount, false)) {
			LOGGER.info(Lang.getInstance().translate("Wallet needs to synchronize!"));
			this.actionAfterConnect();
			this.setNeedSync(true);

			return true;
		} else
			return false;
	}

	public List<Account> getAccounts() {
		return this.wallet.getAccounts();
	}

	public List<PrivateKeyAccount> getPrivateKeyAccounts() {
		return this.wallet.getprivateKeyAccounts();
	}

	public String generateNewAccount() {
		return this.wallet.generateNewAccount();
	}

	public PrivateKeyAccount getPrivateKeyAccountByAddress(String address) {
		if (!this.doesWalletExists())
			return null;

		return this.wallet.getPrivateKeyAccount(address);
	}

	public Account getAccountByAddress(String address) {
		if (!this.doesWalletExists())
			return null;

		return this.wallet.getAccount(address);
	}

	public BigDecimal getUnconfirmedBalance(String address) {
		return this.wallet.getUnconfirmedBalance(address);
	}

	public void addWalletListener(Observer o) {
		this.wallet.addObserver(o);
	}

	public String importAccountSeed(byte[] accountSeed) {
		return this.wallet.importAccountSeed(accountSeed);
	}

	public byte[] exportAccountSeed(String address) {
		return this.wallet.exportAccountSeed(address);
	}

	public byte[] exportSeed() {
		return this.wallet.exportSeed();
	}

	public boolean deleteAccount(PrivateKeyAccount account) {
		return this.wallet.deleteAccount(account);
	}

	public void synchronizeWallet() {
		this.wallet.synchronize();
	}

	public boolean isWalletUnlocked() {
		return this.wallet.isUnlocked();
	}

	public int checkAPICallAllowed(String json, HttpServletRequest request) throws Exception {
		int result = 0;

		if (request != null) {
			Enumeration<String> headers = request.getHeaders(ApiClient.APICALLKEY);
			String uuid = null;
			if (headers.hasMoreElements()) {
				uuid = headers.nextElement();
				if (ApiClient.isAllowedDebugWindowCall(uuid)) {
					return ApiClient.SELF_CALL;
				}
			}
		}

		if (!GraphicsEnvironment.isHeadless() && Gui.isGuiStarted()) {
			Gui gui = Gui.getInstance();
			SysTray.getInstance().sendMessage(Lang.getInstance().translate("INCOMING API CALL"),
					Lang.getInstance().translate("An API call needs authorization!"), MessageType.WARNING);
			Object[] options = { Lang.getInstance().translate("Yes"), Lang.getInstance().translate("No") };

			StringBuilder sb = new StringBuilder(Lang.getInstance().translate("Permission Request: "));
			sb.append(Lang.getInstance().translate("Do you want to authorize the following API call?\n\n") + json);
			JTextArea jta = new JTextArea(sb.toString());
			jta.setLineWrap(true);
			jta.setEditable(false);
			JScrollPane jsp = new JScrollPane(jta) {
				/**
				 *
				 */
				private static final long serialVersionUID = 1L;

				@Override
				public Dimension getPreferredSize() {
					return new Dimension(480, 200);
				}
			};

			gui.bringtoFront();

			result = JOptionPane.showOptionDialog(gui, jsp, Lang.getInstance().translate("INCOMING API CALL"), JOptionPane.YES_NO_OPTION,
					JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
		}

		return result;
	}

	public boolean lockWallet() {
		return this.wallet.lock();
	}

	public boolean unlockWallet(String password) {
		return this.wallet.unlock(password);
	}

	public void setSecondsToUnlock(int seconds) {
		this.wallet.setSecondsToUnlock(seconds);
	}

	public List<Pair<Account, Transaction>> getLastTransactions(int limit) {
		return this.wallet.getLastTransactions(limit);
	}

	public Transaction getTransaction(byte[] signature) {
		return getTransaction(signature, QoraDb.getInstance());
	}

	public Transaction getTransaction(byte[] signature, QoraDb database) {
		// CHECK IF IN BLOCK
		Block block = database.getTransactionParentMap().getParent(signature);
		if (block != null)
			return block.getTransaction(signature);

		// CHECK IF IN TRANSACTION DATABASE
		return database.getTransactionMap().get(signature);
	}

	public List<Transaction> getLastTransactions(Account account, int limit) {
		return this.wallet.getLastTransactions(account, limit);
	}

	public List<Pair<Account, Block>> getLastBlocks() {
		return this.wallet.getLastBlocks();
	}

	public List<Block> getLastBlocks(Account account) {
		return this.wallet.getLastBlocks(account);
	}

	public List<Pair<Account, Name>> getNames() {
		return this.wallet.getNames();
	}

	public List<Name> getNamesAsList() {
		List<Pair<Account, Name>> names = this.wallet.getNames();
		List<Name> result = new ArrayList<>();

		for (Pair<Account, Name> pair : names) {
			result.add(pair.getB());
		}

		return result;
	}

	public List<String> getNamesAsListAsString() {
		List<Name> namesAsList = getNamesAsList();
		List<String> results = new ArrayList<String>();

		for (Name name : namesAsList) {
			results.add(name.getName());
		}

		return results;
	}

	public List<Name> getNames(Account account) {
		return this.wallet.getNames(account);
	}

	public List<Pair<Account, NameSale>> getNameSales() {
		return this.wallet.getNameSales();
	}

	public List<NameSale> getNameSales(Account account) {
		return this.wallet.getNameSales(account);
	}

	public List<NameSale> getAllNameSales() {
		return QoraDb.getInstance().getNameExchangeMap().getNameSales();
	}

	public List<Pair<Account, Poll>> getPolls() {
		return this.wallet.getPolls();
	}

	public List<Poll> getPolls(Account account) {
		return this.wallet.getPolls(account);
	}

	public void addAssetFavorite(Asset asset) {
		this.wallet.addAssetFavorite(asset);
	}

	public void removeAssetFavorite(Asset asset) {
		this.wallet.removeAssetFavorite(asset);
	}

	public boolean isAssetFavorite(Asset asset) {
		return this.wallet.isAssetFavorite(asset);
	}

	public Collection<Poll> getAllPolls() {
		return QoraDb.getInstance().getPollMap().getValues();
	}

	public Collection<Asset> getAllAssets() {
		return QoraDb.getInstance().getAssetMap().getValues();
	}

	public void onDatabaseCommit() {
		this.wallet.commit();
	}

	public ForgingStatus getForgingStatus() {
		return this.blockGenerator.getForgingStatus();
	}

	// BLOCKCHAIN

	public int getHeight() {
		return this.blockchain.getHeight();
	}

	public Block getLastBlock() {
		return this.blockchain.getLastBlock();
	}

	public byte[] getWalletLastBlockSign() {
		return this.wallet.getLastBlockSignature();
	}

	public Block getBlock(byte[] header) {
		return this.blockchain.getBlock(header);
	}

	public Pair<Block, List<Transaction>> scanTransactions(Block block, int blockLimit, int transactionLimit, int type, int service, Account account) {
		return this.blockchain.scanTransactions(block, blockLimit, transactionLimit, type, service, account);
	}

	public long getNextBlockGeneratingBalance() {
		return BlockGenerator.getNextBlockGeneratingBalance(QoraDb.getInstance(), QoraDb.getInstance().getBlockMap().getLastBlock());
	}

	public long getNextBlockGeneratingBalance(Block parent) {
		return BlockGenerator.getNextBlockGeneratingBalance(QoraDb.getInstance(), parent);
	}

	// FORGE

	public void newBlockGenerated(Block newBlock) {
		// Only process if we have connections and are not synchronizing
		if (this.status == STATUS_OK) {
			if (this.synchronizer.process(newBlock)) {
				LOGGER.info("Forged new block " + newBlock.getHeight());

				// BROADCAST
				this.broadcastBlock(newBlock);
			} else {
				LOGGER.info("Couldn't forge new block");
			}
		}
	}

	public List<Transaction> getUnconfirmedTransactions() {
		return this.blockGenerator.getUnconfirmedTransactions();
	}

	// BALANCES

	public SortableList<Tuple2<String, Long>, BigDecimal> getBalances(long key) {
		return QoraDb.getInstance().getBalanceMap().getBalancesSortableList(key);
	}

	public SortableList<Tuple2<String, Long>, BigDecimal> getBalances(Account account) {
		return QoraDb.getInstance().getBalanceMap().getBalancesSortableList(account);
	}

	// NAMES

	public Name getName(String nameName) {
		return QoraDb.getInstance().getNameMap().get(nameName);
	}

	public NameSale getNameSale(String nameName) {
		return QoraDb.getInstance().getNameExchangeMap().getNameSale(nameName);
	}

	// POLLS

	public Poll getPoll(String name) {
		return QoraDb.getInstance().getPollMap().get(name);
	}

	// ASSETS

	public Asset getQoraAsset() {
		return QoraDb.getInstance().getAssetMap().get(0L);
	}

	public Asset getAsset(long key) {
		return QoraDb.getInstance().getAssetMap().get(key);
	}

	public SortableList<BigInteger, Order> getOrders(Asset have, Asset want) {
		return this.getOrders(have, want, false);
	}

	public SortableList<BigInteger, Order> getOrders(Asset have, Asset want, boolean filter) {
		return QoraDb.getInstance().getOrderMap().getOrdersSortableList(have.getKey(), want.getKey(), filter);
	}

	public SortableList<Tuple2<BigInteger, BigInteger>, Trade> getTrades(Asset have, Asset want) {
		return QoraDb.getInstance().getTradeMap().getTradesSortableList(have.getKey(), want.getKey());
	}

	public SortableList<Tuple2<BigInteger, BigInteger>, Trade> getTrades(Order order) {
		return QoraDb.getInstance().getTradeMap().getTrades(order);
	}

	// ATs

	public SortableList<String, AT> getAcctATs(String type, boolean initiators) {
		return QoraDb.getInstance().getATMap().getAcctATs(type, initiators);
	}

	// TRANSACTIONS

	public void onTransactionCreate(Transaction transaction) {
		// ADD TO UNCONFIRMED TRANSACTIONS
		this.blockGenerator.addUnconfirmedTransaction(transaction);

		// NOTIFY OBSERVERS
		this.setChanged();
		this.notifyObservers(new ObserverMessage(ObserverMessage.LIST_TRANSACTION_TYPE, QoraDb.getInstance().getTransactionMap().getValues()));

		this.setChanged();
		this.notifyObservers(new ObserverMessage(ObserverMessage.ADD_TRANSACTION_TYPE, transaction));

		// BROADCAST
		this.broadcastTransaction(transaction);
	}

	public Pair<Transaction, Integer> sendPayment(PrivateKeyAccount sender, Account recipient, BigDecimal amount, BigDecimal fee) {
		// CREATE ONLY ONE TRANSACTION AT A TIME
		synchronized (this.transactionCreator) {
			return this.transactionCreator.createPayment(sender, recipient, amount, fee);
		}
	}

	public Pair<Transaction, Integer> registerName(PrivateKeyAccount registrant, Account owner, String name, String value, BigDecimal fee) {
		// CREATE ONLY ONE TRANSACTION AT A TIME
		synchronized (this.transactionCreator) {
			return this.transactionCreator.createNameRegistration(registrant, new Name(owner, name, value), fee);
		}
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForNameRegistration(String name, String value) {
		// Use genesis address
		return this.transactionCreator.calcRecommendedFeeForNameRegistration(new Name(new Account("QLpLzqs4DW1FNJByeJ63qaqw3eAYCxfkjR"), name, value));
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForNameUpdate(String name, String value) {
		// Use genesis address
		return this.transactionCreator.calcRecommendedFeeForNameUpdate(new Name(new Account("QLpLzqs4DW1FNJByeJ63qaqw3eAYCxfkjR"), name, value));
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForPoll(String name, String description, List<String> options) {
		// CREATE ONLY ONE TRANSACTION AT A TIME

		// CREATE POLL OPTIONS
		List<PollOption> pollOptions = new ArrayList<PollOption>();
		for (String option : options) {
			pollOptions.add(new PollOption(option));
		}

		// CREATE POLL
		Poll poll = new Poll(new Account("QLpLzqs4DW1FNJByeJ63qaqw3eAYCxfkjR"), name, description, pollOptions);

		return this.transactionCreator.calcRecommendedFeeForPollCreation(poll);
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForArbitraryTransaction(byte[] data, List<Payment> payments) {
		if (payments == null)
			payments = new ArrayList<Payment>();

		return this.transactionCreator.calcRecommendedFeeForArbitraryTransaction(data, payments);
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForMessage(byte[] message) {
		return this.transactionCreator.calcRecommendedFeeForMessage(message);
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForPayment() {
		return this.transactionCreator.calcRecommendedFeeForPayment();
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForAssetTransfer() {
		return this.transactionCreator.calcRecommendedFeeForAssetTransfer();
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForOrderTransaction() {
		return this.transactionCreator.calcRecommendedFeeForOrderTransaction();
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForCancelOrderTransaction() {
		return this.transactionCreator.calcRecommendedFeeForCancelOrderTransaction();
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForNameSale(String name) {
		return this.transactionCreator.calcRecommendedFeeForNameSale(new NameSale(name, Transaction.MINIMUM_FEE));
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForNamePurchase(String name) {
		return this.transactionCreator.calcRecommendedFeeForNamePurchase(new NameSale(name, Transaction.MINIMUM_FEE));
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForCancelNameSale(String name) {
		return this.transactionCreator.calcRecommendedFeeForCancelNameSale(new NameSale(name, Transaction.MINIMUM_FEE));
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForPollVote(String pollName) {
		return this.transactionCreator.calcRecommendedFeeForPollVote(pollName);
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForIssueAssetTransaction(String name, String description) {
		return this.transactionCreator.calcRecommendedFeeForIssueAssetTransaction(name, description);
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForMultiPayment(List<Payment> payments) {
		return this.transactionCreator.calcRecommendedFeeForMultiPayment(payments);
	}

	public Pair<BigDecimal, Integer> calcRecommendedFeeForDeployATTransaction(String name, String description, String type, String tags, byte[] creationBytes) {
		return this.transactionCreator.calcRecommendedFeeForDeployATTransaction(name, description, type, tags, creationBytes);
	}

	public Pair<Transaction, Integer> updateName(PrivateKeyAccount owner, Account newOwner, String name, String value, BigDecimal fee) {
		// CREATE ONLY ONE TRANSACTION AT A TIME
		synchronized (this.transactionCreator) {
			return this.transactionCreator.createNameUpdate(owner, new Name(newOwner, name, value), fee);
		}
	}

	public Pair<Transaction, Integer> sellName(PrivateKeyAccount owner, String name, BigDecimal amount, BigDecimal fee) {
		// CREATE ONLY ONE TRANSACTION AT A TIME
		synchronized (this.transactionCreator) {
			return this.transactionCreator.createNameSale(owner, new NameSale(name, amount), fee);
		}
	}

	public Pair<Transaction, Integer> cancelSellName(PrivateKeyAccount owner, NameSale nameSale, BigDecimal fee) {
		// CREATE ONLY ONE TRANSACTION AT A TIME
		synchronized (this.transactionCreator) {
			return this.transactionCreator.createCancelNameSale(owner, nameSale, fee);
		}
	}

	public Pair<Transaction, Integer> BuyName(PrivateKeyAccount buyer, NameSale nameSale, BigDecimal fee) {
		// CREATE ONLY ONE TRANSACTION AT A TIME
		synchronized (this.transactionCreator) {
			return this.transactionCreator.createNamePurchase(buyer, nameSale, fee);
		}
	}

	public Pair<Transaction, Integer> createPoll(PrivateKeyAccount creator, String name, String description, List<String> options, BigDecimal fee) {
		// CREATE ONLY ONE TRANSACTION AT A TIME
		synchronized (this.transactionCreator) {
			// CREATE POLL OPTIONS
			List<PollOption> pollOptions = new ArrayList<PollOption>();
			for (String option : options) {
				pollOptions.add(new PollOption(option));
			}

			// CREATE POLL
			Poll poll = new Poll(creator, name, description, pollOptions);

			return this.transactionCreator.createPollCreation(creator, poll, fee);
		}
	}

	public Pair<Transaction, Integer> createPollVote(PrivateKeyAccount creator, Poll poll, PollOption option, BigDecimal fee) {
		// CREATE ONLY ONE TRANSACTION AT A TIME
		synchronized (this.transactionCreator) {
			// GET OPTION INDEX
			int optionIndex = poll.getOptions().indexOf(option);

			return this.transactionCreator.createPollVote(creator, poll.getName(), optionIndex, fee);
		}
	}

	public Pair<Transaction, Integer> createArbitraryTransaction(PrivateKeyAccount creator, List<Payment> payments, int service, byte[] data, BigDecimal fee) {
		if (payments == null)
			payments = new ArrayList<Payment>();

		// CREATE ONLY ONE TRANSACTION AT A TIME
		synchronized (this.transactionCreator) {
			return this.transactionCreator.createArbitraryTransaction(creator, payments, service, data, fee);
		}
	}

	public Pair<Transaction, Integer> createTransactionFromRaw(byte[] rawData) {
		synchronized (this.transactionCreator) {
			return this.transactionCreator.createTransactionFromRaw(rawData);
		}
	}

	public Pair<Transaction, Integer> issueAsset(PrivateKeyAccount creator, String name, String description, long quantity, boolean divisible, BigDecimal fee) {
		// CREATE ONLY ONE TRANSACTION AT A TIME
		synchronized (this.transactionCreator) {
			return this.transactionCreator.createIssueAssetTransaction(creator, name, description, quantity, divisible, fee);
		}
	}

	public Pair<Transaction, Integer> createOrder(PrivateKeyAccount creator, Asset have, Asset want, BigDecimal amount, BigDecimal price, BigDecimal fee) {
		// CREATE ONLY ONE TRANSACTION AT A TIME
		synchronized (this.transactionCreator) {
			return this.transactionCreator.createOrderTransaction(creator, have, want, amount, price, fee);
		}
	}

	public Pair<Transaction, Integer> cancelOrder(PrivateKeyAccount creator, Order order, BigDecimal fee) {
		// CREATE ONLY ONE TRANSACTION AT A TIME
		synchronized (this.transactionCreator) {
			return this.transactionCreator.createCancelOrderTransaction(creator, order, fee);
		}
	}

	public Pair<Transaction, Integer> transferAsset(PrivateKeyAccount sender, Account recipient, Asset asset, BigDecimal amount, BigDecimal fee) {
		// CREATE ONLY ONE TRANSACTION AT A TIME
		synchronized (this.transactionCreator) {
			return this.transactionCreator.createAssetTransfer(sender, recipient, asset, amount, fee);
		}
	}

	public Pair<Transaction, Integer> deployAT(PrivateKeyAccount creator, String name, String description, String type, String tags, byte[] creationBytes,
			BigDecimal quantity, BigDecimal fee) {

		synchronized (this.transactionCreator) {
			return this.transactionCreator.deployATTransaction(creator, name, description, type, tags, creationBytes, quantity, fee);
		}
	}

	public Pair<Transaction, Integer> sendMultiPayment(PrivateKeyAccount sender, List<Payment> payments, BigDecimal fee) {
		// CREATE ONLY ONE TRANSACTION AT A TIME
		synchronized (this.transactionCreator) {
			return this.transactionCreator.sendMultiPayment(sender, payments, fee);
		}
	}

	public Pair<Transaction, Integer> sendMessage(PrivateKeyAccount sender, Account recipient, long key, BigDecimal amount, BigDecimal fee, byte[] isText,
			byte[] message, byte[] encryptMessage) {
		synchronized (this.transactionCreator) {
			return this.transactionCreator.createMessage(sender, recipient, key, amount, fee, message, isText, encryptMessage);
		}
	}

	public Block getBlockByHeight(int parseInt) {
		byte[] b = QoraDb.getInstance().getHeightMap().getBlockByHeight(parseInt);
		if (b == null)
			return null;

		return QoraDb.getInstance().getBlockMap().get(b);
	}

	public byte[] getPublicKeyByAddress(String address) {
		// Is the address even valid?
		if (!Crypto.getInstance().isValidAddress(address))
			return null;

		// CHECK ACCOUNT IN OWN WALLET
		Account account = Controller.getInstance().getAccountByAddress(address);
		if (account != null && Controller.getInstance().isWalletUnlocked())
			return Controller.getInstance().getPrivateKeyAccountByAddress(address).getPublicKey();

		// Is there a transaction made by this address?
		if (!QoraDb.getInstance().getReferenceMap().contains(address))
			return null;

		Transaction transaction = Controller.getInstance().getTransaction(QoraDb.getInstance().getReferenceMap().get(address));

		if (transaction == null)
			return null;

		return transaction.getCreator().getPublicKey();
	}
}
