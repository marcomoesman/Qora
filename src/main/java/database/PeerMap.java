package database;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import network.Peer;
import ntp.NTP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mapdb.BTreeKeySerializer;
import org.mapdb.DB;

import settings.Settings;
import utils.PeerInfoComparator;
import utils.ReverseComparator;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.google.common.primitives.UnsignedBytes;

import lang.Lang;

public class PeerMap extends DbMap<byte[], byte[]> {
	private static final byte[] BYTE_WHITELISTED = new byte[] { 0, 0 };
	private static final byte[] BYTE_BLACKLISTED = new byte[] { 1, 1 };
	private static final byte[] BYTE_NOTFOUND = new byte[] { 2, 2 };

	private static final Logger LOGGER = LogManager.getLogger(PeerMap.class);
	private Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();

	public PeerMap(QoraDb databaseSet, DB database) {
		super(databaseSet, database);
	}

	public PeerMap(PeerMap parent) {
		super(parent);
	}

	protected void createIndexes(DB database) {
	}

	@Override
	protected Map<byte[], byte[]> getMap(DB database) {
		// OPEN MAP
		return database.createTreeMap("peers").keySerializer(BTreeKeySerializer.BASIC).comparator(UnsignedBytes.lexicographicalComparator()).makeOrGet();
	}

	@Override
	protected Map<byte[], byte[]> getMemoryMap() {
		return new TreeMap<byte[], byte[]>(UnsignedBytes.lexicographicalComparator());
	}

	@Override
	protected byte[] getDefaultValue() {
		return null;
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	public List<Peer> getKnownPeers(int amount) {
		try {
			// GET ITERATOR
			Iterator<byte[]> iterator = this.getKeys().iterator();

			// PEERS
			List<Peer> peers = new ArrayList<Peer>();

			// ITERATE AS LONG AS:
			// 1. we have not reached the amount of peers
			// 2. we have read all records
			while (iterator.hasNext() && peers.size() < amount) {
				// GET ADDRESS
				byte[] addressBI = iterator.next();

				// CHECK IF ADDRESS IS WHITELISTED
				if (!Arrays.equals(Arrays.copyOfRange(this.get(addressBI), 0, 2), BYTE_WHITELISTED))
					continue;

				InetAddress address = InetAddress.getByAddress(addressBI);

				// CHECK IF SOCKET IS NOT LOCALHOST
				if (Settings.getInstance().isLocalAddress(address))
					continue;

				// CREATE PEER
				Peer peer = new Peer(address);

				// ADD TO LIST
				peers.add(peer);
			}

			// RETURN
			return peers;
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);

			return new ArrayList<Peer>();
		}
	}

	public class PeerInfo {

		static final int TIMESTAMP_LENGTH = 8;
		static final int STATUS_LENGTH = 2;

		private byte[] address;
		private byte[] status;
		private long findingTime;
		private long whiteConnectTime;
		private long grayConnectTime;
		private long whitePingCounter;

		public byte[] getAddress() {
			return address;
		}

		public byte[] getStatus() {
			return status;
		}

		public long getFindingTime() {
			return findingTime;
		}

		public long getWhiteConnectTime() {
			return whiteConnectTime;
		}

		public long getGrayConnectTime() {
			return grayConnectTime;
		}

		public long getWhitePingCounter() {
			return whitePingCounter;
		}

		public PeerInfo(byte[] address, byte[] data) {
			if (data != null && data.length == 2 + TIMESTAMP_LENGTH * 4) {
				int position = 0;

				byte[] statusBytes = Arrays.copyOfRange(data, position, position + STATUS_LENGTH);
				position += STATUS_LENGTH;

				byte[] findTimeBytes = Arrays.copyOfRange(data, position, position + TIMESTAMP_LENGTH);
				long longFindTime = Longs.fromByteArray(findTimeBytes);
				position += TIMESTAMP_LENGTH;

				byte[] whiteConnectTimeBytes = Arrays.copyOfRange(data, position, position + TIMESTAMP_LENGTH);
				long longWhiteConnectTime = Longs.fromByteArray(whiteConnectTimeBytes);
				position += TIMESTAMP_LENGTH;

				byte[] grayConnectTimeBytes = Arrays.copyOfRange(data, position, position + TIMESTAMP_LENGTH);
				long longGrayConnectTime = Longs.fromByteArray(grayConnectTimeBytes);
				position += TIMESTAMP_LENGTH;

				byte[] whitePingCounerBytes = Arrays.copyOfRange(data, position, position + TIMESTAMP_LENGTH);
				long longWhitePingCouner = Longs.fromByteArray(whitePingCounerBytes);

				this.address = address;
				this.status = statusBytes;
				this.findingTime = longFindTime;
				this.whiteConnectTime = longWhiteConnectTime;
				this.grayConnectTime = longGrayConnectTime;
				this.whitePingCounter = longWhitePingCouner;
			} else if (Arrays.equals(data, BYTE_NOTFOUND)) {
				this.address = address;
				this.status = BYTE_NOTFOUND;
				this.findingTime = 0;
				this.whiteConnectTime = 0;
				this.grayConnectTime = 0;
				this.whitePingCounter = 0;

				this.updateFindingTime();
			} else {
				this.address = address;
				this.status = BYTE_WHITELISTED;
				this.findingTime = 0;
				this.whiteConnectTime = 0;
				this.grayConnectTime = 0;
				this.whitePingCounter = 0;

				this.updateFindingTime();
			}
		}

		public void addWhitePingCouner(int n) {
			this.whitePingCounter += n;
		}

		public void updateWhiteConnectTime() {
			this.whiteConnectTime = NTP.getTime();
		}

		public void updateGrayConnectTime() {
			this.grayConnectTime = NTP.getTime();
		}

		public void updateFindingTime() {
			this.findingTime = NTP.getTime();
		}

		public boolean isBlacklisted() {
			return Arrays.equals(this.status, BYTE_BLACKLISTED);
		}
		
		public void setBlacklisted(boolean makeBlacklisted) {
			this.status = makeBlacklisted ? BYTE_BLACKLISTED : BYTE_WHITELISTED;
		}

		public byte[] toBytes() {
			byte[] findTimeBytes = Longs.toByteArray(this.findingTime);
			findTimeBytes = Bytes.ensureCapacity(findTimeBytes, TIMESTAMP_LENGTH, 0);

			byte[] whiteConnectTimeBytes = Longs.toByteArray(this.whiteConnectTime);
			whiteConnectTimeBytes = Bytes.ensureCapacity(whiteConnectTimeBytes, TIMESTAMP_LENGTH, 0);

			byte[] grayConnectTimeBytes = Longs.toByteArray(this.grayConnectTime);
			grayConnectTimeBytes = Bytes.ensureCapacity(grayConnectTimeBytes, TIMESTAMP_LENGTH, 0);

			byte[] whitePingCounerBytes = Longs.toByteArray(this.whitePingCounter);
			whitePingCounerBytes = Bytes.ensureCapacity(whitePingCounerBytes, TIMESTAMP_LENGTH, 0);

			return Bytes.concat(this.status, findTimeBytes, whiteConnectTimeBytes, grayConnectTimeBytes, whitePingCounerBytes);
		}

	}

	public List<Peer> getBestPeers(int amount, boolean allFromSettings) {
		try {
			// PEERS
			List<Peer> peers = new ArrayList<Peer>();
			List<PeerInfo> listPeerInfo = new ArrayList<PeerInfo>();

			try {
				// GET ITERATOR
				Iterator<byte[]> iterator = this.getKeys().iterator();

				// ITERATE AS LONG AS:
				// 1. we have not reached the amount of peers
				// 2. we have read all records
				while (iterator.hasNext() && peers.size() < amount) {
					// GET ADDRESS
					byte[] addressBI = iterator.next();

					// CHECK IF ADDRESS IS WHITELISTED
					byte[] data = this.get(addressBI);

					try {
						PeerInfo peerInfo = new PeerInfo(addressBI, data);

						if (Arrays.equals(peerInfo.getStatus(), BYTE_WHITELISTED)) {
							listPeerInfo.add(peerInfo);
						}
					} catch (Exception e) {
						LOGGER.error(e.getMessage(), e);
					}
				}
				Collections.sort(listPeerInfo, new ReverseComparator<PeerInfo>(new PeerInfoComparator()));

			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}

			for (PeerInfo peer : listPeerInfo) {
				InetAddress address = InetAddress.getByAddress(peer.getAddress());

				// CHECK IF SOCKET IS NOT LOCALHOST
				if (!Settings.getInstance().isLocalAddress(address)) {
					if (peers.size() >= amount) {
						if (allFromSettings)
							break;
						else
							return peers;
					}

					// ADD TO LIST
					peers.add(new Peer(address));
				}
			}

			if (allFromSettings) {
				LOGGER.info(Lang.getInstance().translate("Peers loaded from database : %peers%").replace("%peers%", String.valueOf(peers.size())));
			}

			List<Peer> knownPeers = Settings.getInstance().getKnownPeers();

			if (allFromSettings) {
				LOGGER.info(Lang.getInstance().translate("Peers loaded from settings : %peers%").replace("%peers%", String.valueOf(knownPeers.size())));
			}

			for (Peer knownPeer : knownPeers) {
				try {
					if (!allFromSettings && peers.size() >= amount)
						break;

					boolean found = false;

					for (Peer peer : peers) {
						if (peer.getAddress().equals(knownPeer.getAddress())) {
							found = true;
							break;
						}
					}

					if (found)
						continue;

					// ADD TO LIST
					peers.add(knownPeer);
				} catch (Exception e) {
					LOGGER.error(e.getMessage(), e);
				}

			}

			// RETURN
			return peers;
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);

			return new ArrayList<Peer>();
		}
	}

	public List<String> getAllPeersAddresses(int amount) {
		try {
			List<String> addresses = new ArrayList<String>();
			Iterator<byte[]> iterator = this.getKeys().iterator();

			while (iterator.hasNext() && (amount == -1 || addresses.size() < amount)) {
				byte[] addressBI = iterator.next();
				addresses.add(InetAddress.getByAddress(addressBI).getHostAddress());
			}

			return addresses;
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);

			return new ArrayList<String>();
		}
	}

	public List<PeerInfo> getAllPeers(int amount) {
		try {
			// GET ITERATOR
			Iterator<byte[]> iterator = this.getKeys().iterator();

			// PEERS
			List<PeerInfo> peers = new ArrayList<PeerInfo>();

			// ITERATE AS LONG AS:
			// 1. we have not reached the amount of peers
			// 2. we have read all records
			while (iterator.hasNext() && peers.size() < amount) {
				// GET ADDRESS
				byte[] addressBI = iterator.next();
				byte[] data = this.get(addressBI);

				peers.add(new PeerInfo(addressBI, data));
			}

			// SORT
			Collections.sort(peers, new ReverseComparator<PeerInfo>(new PeerInfoComparator()));

			// RETURN
			return peers;
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);

			return new ArrayList<PeerInfo>();
		}
	}

	public void addPeer(final Peer peer) {
		if (this.map == null) {
			return;
		}

		PeerInfo peerInfo;
		final byte[] address = peer.getAddress().getAddress();

		if (this.map.containsKey(address)) {
			final byte[] data = this.map.get(address);
			peerInfo = new PeerInfo(address, data);
		} else {
			peerInfo = new PeerInfo(address, null);
		}

		if (peer.getPingCounter() > 1) {
			if (peer.isWhite()) {
				peerInfo.addWhitePingCouner(1);
				peerInfo.updateWhiteConnectTime();
			} else {
				peerInfo.updateGrayConnectTime();
			}
		}

		// ADD PEER INTO DB
		this.map.put(address, peerInfo.toBytes());
	}

	public PeerInfo getInfo(InetAddress address) {
		byte[] addressBytes = address.getAddress();

		if (this.map == null)
			return new PeerInfo(addressBytes, BYTE_NOTFOUND);

		if (!this.map.containsKey(addressBytes))
			return new PeerInfo(addressBytes, BYTE_NOTFOUND);

		byte[] data = this.map.get(addressBytes);
		return new PeerInfo(addressBytes, data);
	}

	public boolean isBlacklisted(InetAddress address) {
		PeerInfo peerInfo = getInfo(address);

		if (!peerInfo.isBlacklisted())
			return false;

		// Maybe time to un-blacklist peer?
		boolean blacklistingExpired = (NTP.getTime() - peerInfo.getGrayConnectTime() > 24 * 60 * 60 * 1000);

		if (!blacklistingExpired)
			return true;

		// Un-blacklist
		peerInfo.setBlacklisted(false);
		this.map.put(address.getAddress(), peerInfo.toBytes());

		return false;
	}

	public void blacklistPeer(Peer peer) {
		// Update peer in DB
		PeerInfo peerInfo = getInfo(peer.getAddress());
		peerInfo.setBlacklisted(true);
		peerInfo.updateGrayConnectTime();
		this.map.put(peer.getAddress().getAddress(), peerInfo.toBytes());
	}

	public boolean isBad(InetAddress address) {
		byte[] addressByte = address.getAddress();

		// CHECK IF PEER IS BAD
		if (!this.contains(addressByte))
			return false;

		byte[] data = this.map.get(addressByte);
		PeerInfo peerInfo = new PeerInfo(addressByte, data);

		boolean findMoreWeekAgo = (NTP.getTime() - peerInfo.getFindingTime() > 7 * 24 * 60 * 60 * 1000);
		boolean neverWhite = peerInfo.getWhitePingCounter() == 0;

		return findMoreWeekAgo && neverWhite;
	}
}
