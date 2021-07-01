package qora;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import controller.Controller;
import database.QoraDb;
import qora.account.Account;
import qora.assets.Asset;
import qora.block.Block;
import qora.block.GenesisBlock;
import qora.crypto.Base58;
import qora.transaction.ArbitraryTransaction;
import qora.transaction.Transaction;
import settings.Settings;
import utils.Pair;

public final class Blockchain {

	public static final int MAX_SIGNATURES = 500;
	public static final Map<Integer, String> CHECKPOINTS = new HashMap<>();

	private static final Logger LOGGER = LogManager.getLogger(Blockchain.class);

	static {
		// Create checkpoints
		CHECKPOINTS.put(700_000,
				"CqCUGba5Bx6iEVApVNenQ3Upq1mYqV8F8cYe8Hd2bf9at3z345KdRKrSNMj71CSKLPUpz5iG8MctD7UN8jzEUtnMX4UotVzFvuLp3FQqt8iq6r3fZRnpSiRZkQJwvnsWkzYF1NLj8k6jnWyWFNU9w3TXc3BEuo5dJv8sHjLyZRq5R1A");
		CHECKPOINTS.put(650_000,
				"4sKaURU6JBkhTNTJ2gDEQ9T2UAiYQynu2Lpq5Pqhf7ht5cVFGjm5hf8SF82kmmiz6gtG8BdYwFfzaZ8336it6SYpg2YEzJDU1VrM3o6VoLnhSQub2nUGS2AyArQ1PSNmb5YWioxK2zxxnHtPh8BaSxw36EgXbBqgQDpKucjrSFpjJf4");
		CHECKPOINTS.put(600_000,
				"RZMxPSwHFR431mDvKwfWoZs91tF4hdz7dBs3dRLdRoCaoVpEkjxyUhV6Zi328Sy1xS5FDmbUhHwq3ZVZHy3F2rSG1aAygzbqzeJRCNTTm8Wx1ziY9S7P1jnhSq49V5v79QjwAqQLQL8cbAzVRAbmZ1JoGJPkh73b34Tay8oXLvjndBp");
		CHECKPOINTS.put(550_000,
				"AiyjGnm5ra4oRpCAFkrvvavorf9VKJFABiydjX63Jx2AFU8vU7Z399AmCwdreamRsoAqy5EkqRhHLFZGsnBechg5a62SD1ctrFqnyERKKTKZS2xZPaGQLBYC6FyCCF2QbZzKMvudHfU6QVcHGRgprsaMrRVp5a984Avappy9Q4cXN82");
		CHECKPOINTS.put(500_000,
				"P9wTEfKHdhYqhU2BJ8x5JUpF4mAWNNSuccHgE4TeRdb452D7WKqApfM6iWHoqtp6LxRbKDwdgcQnZC1cNVH3sj9UHmWQaW1b3GuFgDrmtwFmFkeXsJvZ7WoK2Vp58u1ByeNFsNrTddhjRBzzEkWT838nr3WNEWmZGMq9CYtY18oR8CK");
		CHECKPOINTS.put(450_000,
				"RYj5fjTGzk4jMQ98QAE8Pxe2utkhzHEd5E5bf5tmjVuNQpZ1HD2LbYdCRWioSYd4AHWsF6gNUeuk4r9juwCTP6pGCyge7mjKyDSiD8Zi4CoScTRMKN7iLPGUTEC6d53pHVc88PX27DwcMZFNCHaPkPXaUgkMz1s5CjL7Joz151sKSGq");
		CHECKPOINTS.put(400_000,
				"P16LKxqfcDggZXLux9MEcLrQzV6Nukrt7rWdUzo2MsdNq47Exti7nHKyAFVsjbdBhFx8w8Vp6h3ZA4pa1LQDpfmextN6hUxbtEuP5Ddk8RgGL8KoMQ4Cejk3GDNFyM5jfiv4PHRMQgWhmrU7QtFmY4vS9YYSJkNJoF31CUcNCpsYs7K");
		CHECKPOINTS.put(350_000,
				"4zPpyTcVoitgdinJXtiGJxoUbtn8zzSTyfDTv3S3e5pazuVqmg2xBtN6QRFxmMxomj2WWjDHNNtTmuxvxokacNBJnT9NFSF8AkGk4u7i6MUbCYfupbtSGqsgjddFtcK6HXs1LvBwPBrM1SDwGaenozuASK1R6rpJxTxnn3yUdTVNAzR");
		CHECKPOINTS.put(300_000,
				"B2Yjw7rd2uUSwomEHPVGjzEYmV9vdMm9DfmPkdnbTKSPiNMu48NSH8eesk6b9qwrf3oYAEJSzTXSE6A4VbyBeL12G8YdYipTuA1NEWYavBX58Cnkrri6yMxGvo9pzBM9VZHXPbgsdsyERsgQfM4s5LNHGcxLEj6BL7i3nbjD5vTt4XT");
		CHECKPOINTS.put(250_000,
				"3ktDoGoQLmLBkoSJq4TbjVwhwr31gTBzspp7c2py36gxrpF9VAp3H9se2CX1xfBRzW3tZoJKXTucm6nhLeC18cmB8A7eiPYa3YxZmAuX4PNo3jZr6UT2Ffw64moV3kkhAfy6TBHiecthYJUhAPH6hqEJvj3fGbDLTXArprC6zVgfijb");
		CHECKPOINTS.put(200_000,
				"HfapnZMyAr5rFg2YJBC3XhqdDsvM39cFNyHDnxRR2qUUGYZGvTYUzCjHF5TMciUPFybWzuAo29RvkzyC5uCaQJSJpNHa5osE7wQiYWey4ZLSPxwdqREMfurkPbPXJ7tfHxvQJLrCSddyssmQGurrLzRjuBKELjj9uhTmdGW3Y7ppnT5");
		CHECKPOINTS.put(150_000,
				"JetQcRmvf8ds1GEb21yMxiWyTtzZGuQzTKSWJQ6QoE945JYqR38jcjgyHzB2PKdfdF15jQQQsPvUAgAoWfmEsoACZhXHHJ97X2JbuWVaQW4nhDmtXgrRF6mHmLmPafCz1BGEeUUndp5W2F9ryyaVkETm4wWBLQSxXGGCzkGbeZQJDcG");
		CHECKPOINTS.put(100_000,
				"Fae4sXUUHP1BTvucgU6VWejvyMgSXSZLeT1SYnVY3UNgd6Qao5mQvCd6bFUQTmEas449s8RHk9KdcEp26Yxj1JqE1j1k11PbSWDBjefAsUitprh3Tqk3uCaRYRvW4rxfoevcQknzSmiu9GvHkeu7UttMokT5Mf5R29cBsHK3Pk688QH");
		CHECKPOINTS.put(50_000,
				"CmZNskd6Wkqnwu8FwFrF9aLWr7kArMGFTvtpjZ8zSQNhGSaQ1ec27V2D2XwijjheFCjkRbEyduBVdJoZLPJBMp8cNUEadYm83nDnSwNrsoaMihFxExc5ZS72QG78nq8eATZQT1beTy4UAGBPCpMC74WPVu7ffXS3ZTiyWkZ34dD5ape");
	}

	public Blockchain() {
		// Create genesis block
		final Block genesis = new GenesisBlock();

		if (Settings.getInstance().isTestnet()) {
			LOGGER.info(((GenesisBlock) genesis).getTestNetInfo());
		}

		if (!QoraDb.getInstance().getBlockMap().contains(genesis.getSignature()) || QoraDb.getInstance().getBlockMap()
				.get(genesis.getSignature()).getTimestamp() != genesis.getTimestamp()) {
			if (QoraDb.getInstance().getBlockMap().getLastBlockSignature() != null) {
				LOGGER.warn("Recreating database...");
				try {
					QoraDb.getInstance().close();
					Controller.getInstance().recreateDatabase(false);
				} catch (final Exception exception) {
					LOGGER.error("Failed to recreate database: " + exception.getMessage(), exception);
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
		
		// Verify blockchain integrity
		verify();
	}

	private void verify() {
		// Verify that block map passes checkpoints
		CHECKPOINTS.forEach((Integer height, String signature) -> {
			final Block block = getBlock(Base58.decode(signature));
			if (block == null) {
				return;
			}
			
			if (block.getHeight() != height) {
				LOGGER.error("Block " + height + " does not pass checkpoint. Database needs to be recreated.");
				LOGGER.warn("Recreating database...");
				try {
					QoraDb.getInstance().close();
					Controller.getInstance().recreateDatabase(false);
				} catch (final Exception exception) {
					LOGGER.error("Failed to recreate database: " + exception.getMessage(), exception);
				}
				
			}
		});
		LOGGER.info("Successfully verified blockchain integrity");
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

	public Pair<Block, List<Transaction>> scanTransactions(Block block, final int blockLimit,
			final int transactionLimit, final int type, final int service, final Account account) {
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
