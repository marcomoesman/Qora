package gui.models;

import java.util.Comparator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.stream.Collectors;

import javax.swing.table.AbstractTableModel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.primitives.Longs;

import controller.Controller;
import database.QoraDb;
import lang.Lang;
import qora.block.Block;
import qora.transaction.Transaction;
import utils.DateTimeFormat;
import utils.NumberAsString;
import utils.ObserverMessage;

@SuppressWarnings("serial")
public class TransactionsTableModel extends AbstractTableModel implements Observer {

	public static final int COLUMN_TIMESTAMP = 0;
	public static final int COLUMN_BLOCK = 1;
	public static final int COLUMN_TYPE = 2;
	public static final int COLUMN_FEE = 3;

	private static final Logger LOGGER = LogManager.getLogger(TransactionsTableModel.class);
	private List<Transaction> transactions;

	private String[] columnNames = Lang.getInstance().translate(new String[] { "Timestamp", "Block", "Type", "Fee" });
	private String[] transactionTypes = Lang.getInstance()
			.translate(new String[] { "", "Genesis", "Payment", "Name Registration", "Name Update", "Name Sale",
					"Cancel Name Sale", "Name Purchase", "Poll Creation", "Poll Vote", "Arbitrary Transaction",
					"Asset Issue", "Asset Transfer", "Order Creation", "Cancel Order", "Multi Payment", "Deploy AT",
					"Message Transaction" });

	public TransactionsTableModel() {
		Controller.getInstance().addObserver(this);
		this.transactions = QoraDb.getInstance().getTransactionFinalMap().getValues().parallelStream()
				.sorted(Comparator.comparingLong(Transaction::getTimestamp).reversed()).collect(Collectors.toList());
	}

	public Transaction getTransaction(int row) {
		return transactions.get(row);
	}

	@Override
	public int getColumnCount() {
		return columnNames.length;
	}

	@Override
	public String getColumnName(int index) {
		return columnNames[index];
	}

	@Override
	public int getRowCount() {
		if (this.transactions == null) {
			return 0;
		}

		return this.transactions.size();
	}

	@Override
	public Object getValueAt(int row, int column) {
		try {
			if (this.transactions == null || this.transactions.size() - 1 < row) {
				return null;
			}

			final Transaction transaction = this.transactions.get(row);
			switch (column) {
			case COLUMN_TIMESTAMP:
				return DateTimeFormat.timestamptoString(transaction.getTimestamp());
			case COLUMN_BLOCK:
				return transaction.getParent().getHeight();
			case COLUMN_TYPE:
				return this.transactionTypes[transaction.getType()];
			case COLUMN_FEE:
				return NumberAsString.getInstance().numberAsString(transaction.getFee());
			}
			return null;
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			return null;
		}
	}

	@Override
	public void update(Observable o, Object arg) {
		try {
			syncUpdate(o, arg);
		} catch (Exception e) {
			// GUI ERROR
		}
	}

	public synchronized void syncUpdate(Observable o, Object arg) {
		final ObserverMessage message = (ObserverMessage) arg;

		// CHECK IF NEW LIST
		if (message.getType() == ObserverMessage.ADD_BLOCK_TYPE) {
			if (this.transactions == null) {
				final Block block = (Block) arg;
				if (block.getTransactionCount() > 0) {
					this.transactions = QoraDb.getInstance().getTransactionFinalMap().getValues().stream().sorted(
							(Transaction one, Transaction two) -> Longs.compare(two.getTimestamp(), one.getTimestamp()))
							.collect(Collectors.toList());
				}
			}

			fireTableDataChanged();
		}

		// CHECK IF LIST UPDATED
		if (message.getType() == ObserverMessage.ADD_TRANSACTION_TYPE
				|| message.getType() == ObserverMessage.REMOVE_TRANSACTION_TYPE) {
			fireTableDataChanged();
		}
	}

	public void removeObservers() {
		Controller.getInstance().deleteObserver(this);
	}
}
