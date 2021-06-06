package network;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import database.QoraDb;
import settings.Settings;

public class PeerManager {

	private static PeerManager instance;
	
	public static PeerManager getInstance()
	{
		if(instance == null)
		{
			instance = new PeerManager();
		}
		
		return instance;
	}
	
	private PeerManager()
	{
		
	}
	
	public List<Peer> getBestPeers()
	{
		return QoraDb.getInstance().getPeerMap().getBestPeers(Settings.getInstance().getMaxSentPeers(), false);
	}
	
	
	public List<Peer> getKnownPeers()
	{
		List<Peer> knownPeers = new ArrayList<Peer>();
		//ASK DATABASE FOR A LIST OF PEERS
		if(!QoraDb.getInstance().isStopped()){
			knownPeers = QoraDb.getInstance().getPeerMap().getBestPeers(Settings.getInstance().getMaxReceivePeers(), true);
		}
		
		//RETURN
		return knownPeers;
	}
	
	public void addPeer(Peer peer)
	{
		//ADD TO DATABASE
		if(!QoraDb.getInstance().isStopped()){
			QoraDb.getInstance().getPeerMap().addPeer(peer);
		}
	}
	
	public void blacklistPeer(Peer peer)
	{
		QoraDb.getInstance().getPeerMap().blacklistPeer(peer);
	}
	
	public boolean isBlacklisted(InetAddress address)
	{
		if(!QoraDb.getInstance().isStopped()){
			return QoraDb.getInstance().getPeerMap().isBlacklisted(address);
		}else{
			return true;
		}
	}
	
	public boolean isBlacklisted(Peer peer)
	{
		return QoraDb.getInstance().getPeerMap().isBlacklisted(peer.getAddress());
	}
}
