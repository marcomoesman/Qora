package utils;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import qora.block.Block;
import qora.block.GenesisBlock;
import qora.crypto.Base58;
import qora.transaction.ArbitraryTransaction;
import qora.transaction.Transaction;
import api.BlogPostResource;
import database.BlockMap;
import database.QoraDb;
import database.SortableList;

public class UpdateUtil {
	private static final Logger LOGGER = LogManager.getLogger(UpdateUtil.class);

	public static void repopulateNameStorage(int height) throws Exception {
		QoraDb.getInstance().getNameStorageMap().reset();
		QoraDb.getInstance().getOrphanNameStorageHelperMap().reset();
		QoraDb.getInstance().getOrphanNameStorageMap().reset();
		QoraDb.getInstance().getHashtagPostMap().reset();

		SortableList<byte[], Block> blocks = QoraDb.getInstance().getBlockMap().getList();
		blocks.sort(BlockMap.HEIGHT_INDEX);

		Block block = new GenesisBlock();
		do {
			if (block.getHeight() >= height) {
				List<Transaction> txs = block.getTransactions();
				for (Transaction tx : txs) {
					if (!(tx instanceof ArbitraryTransaction))
						continue;

					ArbitraryTransaction arbTx = (ArbitraryTransaction) tx;

					int service = arbTx.getService();

					if (service == ArbitraryTransaction.SERVICE_NAME_STORAGE) {
						LOGGER.info("name storage tx " + Base58.encode(arbTx.getSignature()));
						StorageUtils.processUpdate(arbTx.getData(), arbTx.getSignature(), arbTx.getCreator(), QoraDb.getInstance());
					} else if (service == ArbitraryTransaction.SERVICE_BLOG_POST) {
						byte[] data = arbTx.getData();

						String string = new String(data);

						JSONObject jsonObject = (JSONObject) JSONValue.parse(string);

						if (jsonObject == null)
							continue;

						String post = (String) jsonObject.get(BlogPostResource.POST_KEY);

						String share = (String) jsonObject.get(BlogPostResource.SHARE_KEY);

						boolean isShare = false;

						if (StringUtils.isNotEmpty(share))
							isShare = true;

						// DOES POST MEET MINIMUM CRITERIUM?
						if (StringUtils.isNotBlank(post)) {
							// Shares won't be hashtagged!
							if (!isShare) {
								List<String> hashTags = BlogUtils.getHashTags(post);

								for (String hashTag : hashTags)
									QoraDb.getInstance().getHashtagPostMap().add(hashTag, arbTx.getSignature());
							}
						}
					}
				}
			}

			block = block.getChild();
		} while (block != null);
	}

	public static void repopulateTransactionFinalMap() {
		QoraDb.getInstance().getTransactionFinalMap().reset();
		QoraDb.getInstance().commit();
		Block block = new GenesisBlock();

		do {
			List<Transaction> txs = block.getTransactions();
			int counter = 1;

			for (Transaction tx : txs) {
				QoraDb.getInstance().getTransactionFinalMap().add(block.getHeight(), counter, tx);
				counter++;
			}

			if (block.getHeight() % 2000 == 0) {
				LOGGER.info("UpdateUtil - Repopulating TransactionMap : " + block.getHeight());
				QoraDb.getInstance().commit();
			}

			block = block.getChild();
		} while (block != null);
	}

	public static void repopulateCommentPostMap() {
		QoraDb.getInstance().getPostCommentMap().reset();
		QoraDb.getInstance().commit();
		Block block = new GenesisBlock();

		do {
			List<Transaction> txs = block.getTransactions();

			for (Transaction tx : txs) {
				if (tx instanceof ArbitraryTransaction) {
					ArbitraryTransaction at = (ArbitraryTransaction) tx;

					if (at.getService() == ArbitraryTransaction.SERVICE_BLOG_COMMENT)
						BlogUtils.processBlogComment(at.getData(), at.getSignature(), at.getCreator(), QoraDb.getInstance());
				}
			}

			if (block.getHeight() % 2000 == 0) {
				LOGGER.info("UpdateUtil - Repopulating CommentPostMap : " + block.getHeight());
				QoraDb.getInstance().commit();
			}

			block = block.getChild();
		} while (block != null);
	}
}