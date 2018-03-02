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
import database.DBSet;
import database.SortableList;

public class UpdateUtil {
	private static final Logger LOGGER = LogManager.getLogger(UpdateUtil.class);

	public static void repopulateNameStorage(int height) throws Exception {
		DBSet.getInstance().getNameStorageMap().reset();
		DBSet.getInstance().getOrphanNameStorageHelperMap().reset();
		DBSet.getInstance().getOrphanNameStorageMap().reset();
		DBSet.getInstance().getHashtagPostMap().reset();

		SortableList<byte[], Block> blocks = DBSet.getInstance().getBlockMap().getList();
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
						StorageUtils.processUpdate(arbTx.getData(), arbTx.getSignature(), arbTx.getCreator(), DBSet.getInstance());
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
									DBSet.getInstance().getHashtagPostMap().add(hashTag, arbTx.getSignature());
							}
						}
					}
				}
			}

			block = block.getChild();
		} while (block != null);
	}

	public static void repopulateTransactionFinalMap() {
		DBSet.getInstance().getTransactionFinalMap().reset();
		DBSet.getInstance().commit();
		Block block = new GenesisBlock();

		do {
			List<Transaction> txs = block.getTransactions();
			int counter = 1;

			for (Transaction tx : txs) {
				DBSet.getInstance().getTransactionFinalMap().add(block.getHeight(), counter, tx);
				counter++;
			}

			if (block.getHeight() % 2000 == 0) {
				LOGGER.info("UpdateUtil - Repopulating TransactionMap : " + block.getHeight());
				DBSet.getInstance().commit();
			}

			block = block.getChild();
		} while (block != null);
	}

	public static void repopulateCommentPostMap() {
		DBSet.getInstance().getPostCommentMap().reset();
		DBSet.getInstance().commit();
		Block block = new GenesisBlock();

		do {
			List<Transaction> txs = block.getTransactions();

			for (Transaction tx : txs) {
				if (tx instanceof ArbitraryTransaction) {
					ArbitraryTransaction at = (ArbitraryTransaction) tx;

					if (at.getService() == ArbitraryTransaction.SERVICE_BLOG_COMMENT)
						BlogUtils.processBlogComment(at.getData(), at.getSignature(), at.getCreator(), DBSet.getInstance());
				}
			}

			if (block.getHeight() % 2000 == 0) {
				LOGGER.info("UpdateUtil - Repopulating CommentPostMap : " + block.getHeight());
				DBSet.getInstance().commit();
			}

			block = block.getChild();
		} while (block != null);
	}
}