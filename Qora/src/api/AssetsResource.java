package api;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import controller.Controller;
import database.DBSet;
import database.TradeMap;
import database.TransactionFinalMap;
import qora.assets.Order;
import qora.assets.Trade;
import qora.blockexplorer.BlockExplorer;
import qora.transaction.CreateOrderTransaction;
import qora.transaction.Transaction;

@Path("assets")
@Produces(MediaType.APPLICATION_JSON)
public class AssetsResource {
	private static final Logger LOGGER = LogManager.getLogger(AssetsResource.class);

	@GET
	public String getAssetsLite()
	{
		return JSONValue.toJSONString(BlockExplorer.getInstance().jsonQueryAssetsLite());
	}
	
	@GET
	@Path("/full")	
	public String getAssetsFull()
	{
		return JSONValue.toJSONString(BlockExplorer.getInstance().jsonQueryAssets());
	}	

	@GET
	@Path("/{key}")	
	public String getAssetLite(@PathParam("key") String key)
	{
		Long assetAsLong = null;
		
		// HAS ASSET NUMBERFORMAT
		try {
			assetAsLong = Long.valueOf(key);

		} catch (NumberFormatException e) {
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_INVALID_ASSET_ID);
		}

		// DOES ASSETID EXIST
		if (!DBSet.getInstance().getAssetMap().contains(assetAsLong)) {
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_INVALID_ASSET_ID);
		}
		
		return Controller.getInstance().getAsset(assetAsLong).toJson().toJSONString();
	}
	
	@GET
	@Path("/{key}/full")	
	public String getAsset(@PathParam("key") String key)
	{
		Long assetAsLong = null;
		
		// HAS ASSET NUMBERFORMAT
		try {
			assetAsLong = Long.valueOf(key);

		} catch (NumberFormatException e) {
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_INVALID_ASSET_ID);
		}

		// DOES ASSETID EXIST
		if (!DBSet.getInstance().getAssetMap().contains(assetAsLong)) {
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_INVALID_ASSET_ID);
		}
		
		return JSONValue.toJSONString(BlockExplorer.getInstance().jsonQueryAsset(assetAsLong));
	}	

	@SuppressWarnings("unchecked")
	@POST
	@Path("/trades")
	public String getAssetTrades(String x) {
		// we need at least one of: assetID ("key") or involved addresses
		try {
			JSONObject jsonObject = (JSONObject) JSONValue.parse(x);

			List<Trade> trades = null;

			long key = -1;
			if (jsonObject.containsKey("key")) {
				key = ((Long) jsonObject.get("key")).longValue();

				// DOES ASSETID EXIST
				if (!DBSet.getInstance().getAssetMap().contains(key))
					throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_INVALID_ASSET_ID);
			}

			// This needs to be "final" for use inside closure below
			final JSONArray addresses = jsonObject.containsKey("addresses") ? (JSONArray) jsonObject.get("addresses") : null;

			// must have at least one of the above
			if (key == -1 && addresses == null)
				throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_JSON);

			// Grab limit and offset now (if any)
			int limit = 0; // default: no limit
			int offset = 0; // default: from the start

			if (jsonObject.containsKey("limit")) {
				limit = ((Long) jsonObject.get("limit")).intValue();

				// Check limit is either zero (no limit) or positive
				if (limit < 0)
					throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_INVALID_RESULTS_LIMIT);
			}

			if (jsonObject.containsKey("offset")) {
				offset = ((Long) jsonObject.get("offset")).intValue();

				// Check offset is either zero or positive
				if (offset < 0)
					throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_INVALID_RESULTS_OFFSET);
			}

			// This needs to be "final" for use inside closure below
			final long minTimestamp = jsonObject.containsKey("minTimestamp") ? ((Long) jsonObject.get("minTimestamp")).longValue() : -1;

			// key-then-addresses better than addresses-then-key
			if (key != -1) {
				trades = DBSet.getInstance().getTradeMap().getTrades(key);

				// filter by timestamp?
				if (minTimestamp != -1)
					trades.removeIf( (Trade trade) -> trade.getTimestamp() < minTimestamp );

				if (addresses != null) {
					// filter by addresses (trade->orders->address)
					trades.removeIf( (Trade trade) -> {
						Order orderA = trade.getInitiatorOrder(DBSet.getInstance());
						Order orderB = trade.getTargetOrder(DBSet.getInstance());

						for (int i=0; i<addresses.size(); ++i) {
							final String address = (String) addresses.get(i);

							if (orderA.getCreator().getAddress().equals(address))
								return false;

							if (orderB.getCreator().getAddress().equals(address))
								return false;
						}

						return true;
					});
				}
			} else if (addresses != null) {
				trades = new ArrayList<Trade>();

				TransactionFinalMap transactionFinalMap = DBSet.getInstance().getTransactionFinalMap();
				TradeMap tradeMap = DBSet.getInstance().getTradeMap();

				for (int i=0; i<addresses.size(); ++i) {
					String address = (String) addresses.get(i);

					// get CreateOrderTransactions by address
					List<Transaction> transactions = transactionFinalMap.getTransactionsByTypeAndAddress(address, Transaction.CREATE_ORDER_TRANSACTION, limit);

					// get trades by order
					for (int k=0; k < transactions.size(); ++k) {
						CreateOrderTransaction orderTransaction = (CreateOrderTransaction) transactions.get(k);
						Order order = orderTransaction.getOrder();

						// can we filter by key here to save time?
						if (key != -1 && order.getHave() != key && order.getWant() != key)
							continue;

						List<Trade> ordersTrades = tradeMap.getTrades(order);

						// filter by timestamp?
						if (minTimestamp != -1)
							ordersTrades.removeIf( (Trade trade) -> trade.getTimestamp() < minTimestamp );

						// append to returnable trades
						trades.addAll(ordersTrades);
					}
				}
			}

			JSONArray outputArray = new JSONArray();
			// Check whether offset is out of bounds
			if (offset >= trades.size())
				return outputArray.toJSONString();

			// subset by offset/limit
			if (limit == 0 || limit > trades.size())
				limit = trades.size();

			if (offset + limit > trades.size())
				limit = trades.size() - offset;

			for (Trade trade : trades.subList(offset, offset + limit))
				outputArray.add(trade.toJson());

			return outputArray.toJSONString();
		} catch (NullPointerException | ClassCastException e) {
			//JSON EXCEPTION
			LOGGER.info(e);
			throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_JSON);
		}
	}
}
