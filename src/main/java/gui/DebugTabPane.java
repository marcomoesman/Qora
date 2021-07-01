package gui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;

import database.BlockMap;
import database.TransactionMap;
import gui.models.BlocksTableModel;
import gui.models.PeersTableModel;
import gui.models.TransactionsTableModel;
import gui.transaction.TransactionDetailsFactory;
import lang.Lang;
import qora.transaction.Transaction;
import settings.Settings;

public class DebugTabPane extends JTabbedPane {

	private static final long serialVersionUID = 2717571093561259483L;

	private PeersTableModel peersTableModel;
	private TransactionsTableModel transactionsTableModel;
	private BlocksTableModel blocksTableModel;
	private JTable transactionsTable;

	public DebugTabPane() {
		super();

		// ADD TABS
		if (Settings.getInstance().isGuiConsoleEnabled()) {
			this.addTab(Lang.getInstance().translate("Console"), new ConsolePanel());
		}

		this.peersTableModel = new PeersTableModel();
		this.addTab(Lang.getInstance().translate("Peers"), new JScrollPane(new JTable(this.peersTableModel)));

		// TRANSACTIONS TABLE MODEL
		this.transactionsTableModel = new TransactionsTableModel();
		this.transactionsTable = new JTable(this.transactionsTableModel);

		// TRANSACTIONS SORTER
		Map<Integer, Integer> indexes = new TreeMap<Integer, Integer>();
		indexes.put(TransactionsTableModel.COLUMN_TIMESTAMP, TransactionMap.TIMESTAMP_INDEX);

		// TRANSACTION DETAILS
		this.transactionsTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					// GET ROW
					int row = transactionsTable.getSelectedRow();
					row = transactionsTable.convertRowIndexToModel(row);

					// GET TRANSACTION
					Transaction transaction = transactionsTableModel.getTransaction(row);

					// SHOW DETAIL SCREEN OF TRANSACTION
					TransactionDetailsFactory.getInstance().createTransactionDetail(transaction);
				}
			}
		});

		// ADD TRANSACTIONS TABLE
		this.addTab(Lang.getInstance().translate("Transactions"), new JScrollPane(this.transactionsTable));

		// BLOCKS TABLE MODEL
		this.blocksTableModel = new BlocksTableModel();
		JTable blocksTable = new JTable(this.blocksTableModel);

		// BLOCKS SORTER
		indexes = new TreeMap<Integer, Integer>();
		indexes.put(BlocksTableModel.COLUMN_HEIGHT, BlockMap.HEIGHT_INDEX);
		QoraRowSorter sorter = new QoraRowSorter(blocksTableModel, indexes);
		blocksTable.setRowSorter(sorter);

		// ADD BLOCK TABLE
		this.addTab(Lang.getInstance().translate("Blocks"), new JScrollPane(blocksTable));
	}

	public void close() {
		// REMOVE OBSERVERS/HANLDERS
		this.peersTableModel.removeObservers();
		this.transactionsTableModel.removeObservers();
		this.blocksTableModel.removeObservers();
	}

}
