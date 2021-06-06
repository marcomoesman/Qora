package qora;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import controller.Controller;
import database.QoraDb;
import qora.account.Account;
import qora.assets.Asset;
import qora.block.Block;
import qora.block.GenesisBlock;
import qora.transaction.ArbitraryTransaction;
import qora.transaction.Transaction;
import settings.Settings;
import utils.Pair;

public final class Blockchain {
	public static final int MAX_SIGNATURES = 500;

	private static final Logger LOGGER = LogManager.getLogger(Blockchain.class);

	public Blockchain() {
		// Create genesis block
		final Block genesis = new GenesisBlock();

		if (Settings.getInstance().isTestnet()) {
			LOGGER.info(((GenesisBlock) genesis).getTestNetInfo());
		}

		if (!QoraDb.getInstance().getBlockMap().contains(genesis.getSignature()) || QoraDb.getInstance()
				.getBlockMap().get(genesis.getSignature()).getTimestamp() != genesis.getTimestamp()) {
			if (QoraDb.getInstance().getBlockMap().getLastBlockSignature() != null) {
				LOGGER.info("Recreating Database...");

				try {
					QoraDb.getInstance().close();
					Controller.getInstance().reCreateDB(false);
				} catch (Exception e) {
					LOGGER.error(e.getMessage(), e);
				}
			}
			
			// Process genesis block
			genesis.process();

			// Add simulated Qora asset
			final Asset qora = new Asset(genesis.getGenerator(), "Qora", "This is the simulated Qora asset.",
					10000000000L, true, genesis.getGeneratorSignature());
			QoraDb.getInstance().getIssueAssetMap().set(genesis.getGeneratorSignature(), 0L);
			QoraDb.getInstance().getAssetMap().set(0L, qora);
		}
	}

	public int getHeight() {
		// Get last block signature
		final byte[] lastBlockSignature = QoraDb.getInstance().getBlockMap().getLastBlockSignature();
		// Get and return height
		return QoraDb.getInstance().getHeightMap().get(lastBlockSignature);
	}

	public List<byte[]> getSignatures(final byte[] parent) {
		final List<byte[]> headers = new ArrayList<byte[]>();
		// Check if block exists
		if (QoraDb.getInstance().getBlockMap().contains(parent)) {
			Block parentBlock = QoraDb.getInstance().getBlockMap().get(parent).getChild();
			int counter = 0;
			while (parentBlock != null && counter < MAX_SIGNATURES) {
				headers.add(parentBlock.getSignature());
				parentBlock = parentBlock.getChild();
				counter++;
			}
		}
		return headers;
	}

	public Block getBlock(final byte[] header) {
		return QoraDb.getInstance().getBlockMap().get(header);
	}

	public boolean isNewBlockValid(final Block block) {
		// Check if block isn't genesis
		if (block instanceof GenesisBlock) {
			return false;
		}

		// Check if signature is valid
		if (!block.isSignatureValid()) {
			return false;
		}

		// Check if we know reference
		if (!QoraDb.getInstance().getBlockMap().contains(block.getReference())) {
			return false;
		}

		// Check if reference is last block signature
		if (!Arrays.equals(QoraDb.getInstance().getBlockMap().getLastBlockSignature(), block.getReference())) {
			return false;
		}
		return true;
	}

	public Pair<Block, List<Transaction>> scanTransactions(Block block, final int blockLimit, final int transactionLimit, final int type,
			final int service, final Account account) {
		// Create list
		final List<Transaction> transactions = new ArrayList<Transaction>();

		// If block is null start from genesis
		if (block == null) {
			block = new GenesisBlock();
		}

		int scannedBlocks = 0;
		do {
			// Loop though transactions in block
			for (final Transaction transaction : block.getTransactions()) {
				// Check if account is involved
				if (account != null && !transaction.isInvolved(account)) {
					continue;
				}

				// Check if type is OK
				if (type != -1 && transaction.getType() != type) {
					continue;
				}

				// CHheck if service is OK
				if (service != -1 && transaction.getType() == Transaction.ARBITRARY_TRANSACTION) {
					final ArbitraryTransaction arbitraryTransaction = (ArbitraryTransaction) transaction;
					if (arbitraryTransaction.getService() != service) {
						continue;
					}
				}

				// Add to list
				transactions.add(transaction);
			}

			// Set block to child
			block = block.getChild();
			scannedBlocks++;
		}
		
		while (block != null && (transactions.size() < transactionLimit || transactionLimit == -1)
				&& (scannedBlocks < blockLimit || blockLimit == -1));

		// Check if we reached the end
		if (block == null) {
			block = this.getLastBlock();
		} else {
			block = block.getParent();
		}

		// Return parent block
		return new Pair<Block, List<Transaction>>(block, transactions);
	}

	public Block getLastBlock() {
		return QoraDb.getInstance().getBlockMap().getLastBlock();
	}
}
