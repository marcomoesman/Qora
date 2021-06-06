package database;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Observable;
import java.util.Observer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import qora.web.NameStorageMap;
import qora.web.OrphanNameStorageHelperMap;
import qora.web.OrphanNameStorageMap;
import qora.web.SharedPostsMap;
import controller.Controller;
import settings.Settings;
import utils.ObserverMessage;
import utils.SimpleFileVisitorForRecursiveFolderDeletion;

public final class QoraDb implements Observer, IDB {

	private static final Logger LOGGER = LogManager.getLogger(QoraDb.class);
	private static final int ACTIONS_BEFORE_COMMIT = 10000;

	private static QoraDb instance;

	private final BalanceMap balanceMap;
	private final BlockMap blockMap;
	private final ChildMap childMap;
	private final HeightMap heightMap;
	private final ReferenceMap referenceMap;
	private final PeerMap peerMap;
	private final TransactionMap transactionMap;
	private final NameMap nameMap;
	private final NameStorageMap nameStorageMap;
	private final OrphanNameStorageMap orphanNameStorageMap;
	private final OrphanNameStorageHelperMap orphanNameStorageHelperMap;
	private final SharedPostsMap sharedPostsMap;
	private final PostCommentMap postCommentMap;
	private final CommentPostMap commentPostMap;
	private final LocalDataMap localDataMap;
	private final BlogPostMap blogPostMap;
	private final HashtagPostMap hashtagPostMap;
	private final TransactionParentMap transactionParentMap;
	private final NameExchangeMap nameExchangeMap;
	private final UpdateNameMap updateNameMap;
	private final CancelSellNameMap cancelSellNameMap;
	private final PollMap pollMap;
	private final VoteOnPollMap voteOnPollMap;
	private final AssetMap assetMap;
	private final IssueAssetMap issueAssetMap;
	private final OrderMap orderMap;
	private final CompletedOrderMap completedOrderMap;
	private final TradeMap tradeMap;
	private final ATMap atMap;
	private final ATStateMap atStateMap;
	private final ATTransactionMap atTransactionMap;
	private final TransactionFinalMap transactionFinalMap;

	private DB database;
	private int actions;

	public static QoraDb getInstance() {
		if (instance == null) {
			createDatabase();
		}

		return instance;
	}

	public static void deleteDataFolder() {
		final File dataFolder = new File(Settings.getInstance().getDataDir());
		if (!dataFolder.exists()) {
			return;
		}

		try {
			Files.walkFileTree(dataFolder.toPath(), new SimpleFileVisitorForRecursiveFolderDeletion(dataFolder.toPath()));
		} catch (IOException e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public static void deleteDataBackup() {
		final File dataFolder = new File(Settings.getInstance().getDataDir());

		if (!dataFolder.exists()) {
			return;
		}

		final File dataBak = new File(dataFolder.getParent(), "dataBak");
		if (!dataBak.exists()) {
			return;
		}

		try {
			Files.walkFileTree(dataBak.toPath(), new SimpleFileVisitorForRecursiveFolderDeletion(dataBak.toPath()));
		} catch (IOException e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public static void createDatabase() {
		// Create database file and folder
		final File file = new File(Settings.getInstance().getDataDir(), "data.dat");
		file.getParentFile().mkdirs();

		// Create database
		final DB database = DBMaker.newFileDB(file).cacheSize(2048).checksumEnable().mmapFileEnableIfSupported()
				.cacheLRUEnable().make();

		// Set instance
		instance = new QoraDb(database);
	}

	public static QoraDb createMemoryDatabase() {
		// Create database
		final DB database = DBMaker.newMemoryDB().make();
		
		// Set instance
		instance = new QoraDb(database);
		return instance;
	}

	public QoraDb(final DB database) {
		try {
			this.database = database;
			this.actions = 0;

			this.balanceMap = new BalanceMap(this, database);
			this.transactionFinalMap = new TransactionFinalMap(this, database);
			this.blockMap = new BlockMap(this, database);
			this.childMap = new ChildMap(this, database);
			this.heightMap = new HeightMap(this, database);
			this.referenceMap = new ReferenceMap(this, database);
			this.peerMap = new PeerMap(this, database);
			this.transactionMap = new TransactionMap(this, database);
			this.nameMap = new NameMap(this, database);
			this.nameStorageMap = new NameStorageMap(this, database);
			this.orphanNameStorageMap = new OrphanNameStorageMap(this, database);
			this.orphanNameStorageHelperMap = new OrphanNameStorageHelperMap(this, database);
			this.sharedPostsMap = new SharedPostsMap(this, database);
			this.postCommentMap = new PostCommentMap(this, database);
			this.commentPostMap = new CommentPostMap(this, database);
			this.localDataMap = new LocalDataMap(this, database);
			this.blogPostMap = new BlogPostMap(this, database);
			this.hashtagPostMap = new HashtagPostMap(this, database);
			this.transactionParentMap = new TransactionParentMap(this, database);
			this.nameExchangeMap = new NameExchangeMap(this, database);
			this.updateNameMap = new UpdateNameMap(this, database);
			this.cancelSellNameMap = new CancelSellNameMap(this, database);
			this.pollMap = new PollMap(this, database);
			this.voteOnPollMap = new VoteOnPollMap(this, database);
			this.assetMap = new AssetMap(this, database);
			this.issueAssetMap = new IssueAssetMap(this, database);
			this.orderMap = new OrderMap(this, database);
			this.completedOrderMap = new CompletedOrderMap(this, database);
			this.tradeMap = new TradeMap(this, database);
			this.atMap = new ATMap(this, database);
			this.atStateMap = new ATStateMap(this, database);
			this.atTransactionMap = new ATTransactionMap(this, database);

		} catch (Throwable e) {
			close();
			throw e;
		}
	}

	protected QoraDb(final QoraDb parent) {
		this.balanceMap = new BalanceMap(parent.balanceMap);
		this.transactionFinalMap = new TransactionFinalMap(parent.transactionFinalMap);
		this.blockMap = new BlockMap(parent.blockMap);
		this.childMap = new ChildMap(this.blockMap, parent.childMap);
		this.heightMap = new HeightMap(parent.heightMap);
		this.referenceMap = new ReferenceMap(parent.referenceMap);
		this.peerMap = new PeerMap(parent.peerMap);
		this.transactionMap = new TransactionMap(parent.transactionMap);
		this.nameMap = new NameMap(parent.nameMap);
		this.nameStorageMap = new NameStorageMap(parent.nameStorageMap);
		this.orphanNameStorageMap = new OrphanNameStorageMap(parent.orphanNameStorageMap);
		this.sharedPostsMap = new SharedPostsMap(parent.sharedPostsMap);
		this.postCommentMap = new PostCommentMap(parent.postCommentMap);
		this.commentPostMap = new CommentPostMap(parent.commentPostMap);
		this.orphanNameStorageHelperMap = new OrphanNameStorageHelperMap(parent.orphanNameStorageHelperMap);
		this.localDataMap = new LocalDataMap(parent.localDataMap);
		this.blogPostMap = new BlogPostMap(parent.blogPostMap);
		this.hashtagPostMap = new HashtagPostMap(parent.hashtagPostMap);
		this.transactionParentMap = new TransactionParentMap(this.blockMap, parent.transactionParentMap);
		this.nameExchangeMap = new NameExchangeMap(parent.nameExchangeMap);
		this.updateNameMap = new UpdateNameMap(parent.updateNameMap);
		this.cancelSellNameMap = new CancelSellNameMap(parent.cancelSellNameMap);
		this.pollMap = new PollMap(parent.pollMap);
		this.voteOnPollMap = new VoteOnPollMap(parent.voteOnPollMap);
		this.assetMap = new AssetMap(parent.assetMap);
		this.issueAssetMap = new IssueAssetMap(parent.issueAssetMap);
		this.orderMap = new OrderMap(parent.orderMap);
		this.completedOrderMap = new CompletedOrderMap(parent.completedOrderMap);
		this.tradeMap = new TradeMap(parent.tradeMap);
		this.atMap = new ATMap(parent.atMap);
		this.atStateMap = new ATStateMap(parent.atStateMap);
		this.atTransactionMap = new ATTransactionMap(parent.atTransactionMap);
	}

	public void reset() {
		this.balanceMap.reset();
		this.heightMap.reset();
		this.referenceMap.reset();
		this.peerMap.reset();
		this.transactionFinalMap.reset();
		this.transactionMap.reset();
		this.nameMap.reset();
		this.nameStorageMap.reset();
		this.orphanNameStorageMap.reset();
		this.orphanNameStorageHelperMap.reset();
		this.sharedPostsMap.reset();
		this.commentPostMap.reset();
		this.postCommentMap.reset();
		this.localDataMap.reset();
		this.blogPostMap.reset();
		this.hashtagPostMap.reset();
		this.transactionParentMap.reset();
		this.nameExchangeMap.reset();
		this.updateNameMap.reset();
		this.cancelSellNameMap.reset();
		this.pollMap.reset();
		this.voteOnPollMap.reset();
		this.tradeMap.reset();
		this.orderMap.reset();
		this.completedOrderMap.reset();
		this.issueAssetMap.reset();
		this.assetMap.reset();
		this.atMap.reset();
		this.atStateMap.reset();
		this.atTransactionMap.reset();
	}

	public BalanceMap getBalanceMap() {
		return this.balanceMap;
	}

	public BlockMap getBlockMap() {
		return this.blockMap;
	}

	public ChildMap getChildMap() {
		return this.childMap;
	}

	public HeightMap getHeightMap() {
		return this.heightMap;
	}

	public ReferenceMap getReferenceMap() {
		return this.referenceMap;
	}

	public PeerMap getPeerMap() {
		return this.peerMap;
	}

	public TransactionMap getTransactionMap() {
		return this.transactionMap;
	}

	public TransactionFinalMap getTransactionFinalMap() {
		return this.transactionFinalMap;
	}

	public NameMap getNameMap() {
		return this.nameMap;
	}

	public NameStorageMap getNameStorageMap() {
		return this.nameStorageMap;
	}

	public OrphanNameStorageMap getOrphanNameStorageMap() {
		return this.orphanNameStorageMap;
	}

	public SharedPostsMap getSharedPostsMap() {
		return this.sharedPostsMap;
	}

	public PostCommentMap getPostCommentMap() {
		return this.postCommentMap;
	}

	public CommentPostMap getCommentPostMap() {
		return this.commentPostMap;
	}

	public OrphanNameStorageHelperMap getOrphanNameStorageHelperMap() {
		return this.orphanNameStorageHelperMap;
	}

	public LocalDataMap getLocalDataMap() {
		return this.localDataMap;
	}

	public BlogPostMap getBlogPostMap() {
		return this.blogPostMap;
	}

	public HashtagPostMap getHashtagPostMap() {
		return this.hashtagPostMap;
	}

	public TransactionParentMap getTransactionParentMap() {
		return this.transactionParentMap;
	}

	public NameExchangeMap getNameExchangeMap() {
		return this.nameExchangeMap;
	}

	public UpdateNameMap getUpdateNameMap() {
		return this.updateNameMap;
	}

	public CancelSellNameMap getCancelSellNameMap() {
		return this.cancelSellNameMap;
	}

	public PollMap getPollMap() {
		return this.pollMap;
	}

	public VoteOnPollMap getVoteOnPollDatabase() {
		return this.voteOnPollMap;
	}

	public AssetMap getAssetMap() {
		return this.assetMap;
	}

	public IssueAssetMap getIssueAssetMap() {
		return this.issueAssetMap;
	}

	public OrderMap getOrderMap() {
		return this.orderMap;
	}

	public CompletedOrderMap getCompletedOrderMap() {
		return this.completedOrderMap;
	}

	public TradeMap getTradeMap() {
		return this.tradeMap;
	}

	public ATMap getATMap() {
		return this.atMap;
	}

	public ATStateMap getATStateMap() {
		return this.atStateMap;
	}

	public ATTransactionMap getATTransactionMap() {
		return this.atTransactionMap;
	}

	public QoraDb fork() {
		return new QoraDb(this);
	}

	public void close() {
		if (this.database != null) {
			if (!this.database.isClosed()) {
				this.database.commit();
				this.database.close();
			}
		}
	}

	public boolean isStopped() {
		return this.database.isClosed();
	}

	public void commit() {
		this.actions++;
	}

	@Override
	public void update(final Observable o, final Object arg) {
		final ObserverMessage message = (ObserverMessage) arg;

		// Check if new block message
		if (message.getType() == ObserverMessage.LIST_BLOCK_TYPE) {

			// Check if we need to commit
			if (this.actions >= ACTIONS_BEFORE_COMMIT) {
				this.database.commit();
				this.actions = 0;

				// Notify controller of commit
				Controller.getInstance().onDatabaseCommit();
			}
		}
	}

}
