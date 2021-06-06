package api;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import controller.Controller;
import database.QoraDb;
import database.PeerMap.PeerInfo;
import network.Peer;
import network.PeerManager;
import ntp.NTP;
import settings.Settings;
import utils.APIUtils;
import utils.DateTimeFormat;

@Path("peers")
@Produces(MediaType.APPLICATION_JSON)
public class PeersResource 
{
	@Context
	HttpServletRequest request;
	
	@SuppressWarnings("unchecked")
	@GET
	public String getPeers()
	{
		List<Peer> peers = Controller.getInstance().getActivePeers();
		JSONArray array = new JSONArray();
		
		for(Peer peer: peers)
		{
			array.add(peer.getAddress().getHostAddress());
		}
		
		return array.toJSONString();
	}

	@POST
	public String addPeer(String address) {
		
		APIUtils.askAPICallAllowed("POST peers " + address, request );
		
		// CHECK WALLET UNLOCKED
		if (Controller.getInstance().doesWalletExists() && !Controller.getInstance().isWalletUnlocked()) {
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_WALLET_LOCKED);
		}
		
		Peer peer;
		try {
			peer = new Peer(InetAddress.getByName(address));
		} catch (UnknownHostException e) {
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_INVALID_NETWORK_ADDRESS);
		}
		/*
		 *  XXX what is the purpose of this call?
		 *  PeerMap.addPeer() tests for peer.pingCounter > 1 but after this call
		 *  peer.pingCounter will only be 1 so there's no difference code-path-wise
		 *  between pingCounter being 0 or 1.
		 *  Also creating a new Peer starts a Pinger thread which will increment pingCounter
		 *  and call PeerMap.addPeer()
		 */
		// was: peer.addPingCounter();
		// now: peer.onPingSuccess(); which also performs next line:
		QoraDb.getInstance().getPeerMap().addPeer(peer);
		
		return "OK";
	}
	
	@SuppressWarnings("unchecked")
	@GET
	@Path("height")
	public String getTest()
	{
		Map<Peer,Integer> peers = Controller.getInstance().getPeerHeights();
		JSONArray array = new JSONArray();
		
		for(Map.Entry<Peer, Integer> peer: peers.entrySet())
		{
			JSONObject o = new JSONObject();
			o.put("peer", peer.getKey().getAddress().getHostAddress());
			o.put("height", peer.getValue());
			array.add(o);
		}
		
		return array.toJSONString();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@GET
	@Path("detail")
	public String getDetail()
	{
		List<Peer> activePeers = Controller.getInstance().getActivePeers();
		Map output = new LinkedHashMap();

		for(int i=0; i < activePeers.size() ; i++)
		{
			Peer peer = activePeers.get(i);
			
			if(peer != null)
			{
				output.put(peer.getAddress().getHostAddress(), this.getDetail(peer));
			}
		}	
		
		return JSONValue.toJSONString(output);
	}

	@GET
	@Path("detail/{address}")
	public String getDetail(@PathParam("address") String address)
	{
		Peer peer = null;
		
		List<Peer> activePeers = Controller.getInstance().getActivePeers();
		
		for (Peer activePeer : activePeers) {
			if(activePeer.getAddress().getHostAddress().equals(address))
			{
				if(peer == null)
				{
					peer = activePeer;	
				}
				
				if(activePeer.isWhite())
				{
					peer = activePeer;
				}
			}
		}
		
		if(peer == null){
			try {
				peer = new Peer(InetAddress.getByName(address));
			} catch (UnknownHostException e) {
				throw ApiErrorFactory.getInstance().createError(
						ApiErrorFactory.ERROR_INVALID_NETWORK_ADDRESS);
			}
		}
		
		return this.getDetail(peer).toJSONString(); 
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject getDetail(Peer peer)
	{
		JSONObject o = new JSONObject();

		if(Controller.getInstance().getActivePeers().contains(peer))
		{
			o.put("status", "connected");
		}
		else if(QoraDb.getInstance().getPeerMap().contains(peer.getAddress().getAddress()))
		{
			o.put("status", "known disconnected");
		}
		
		if(Controller.getInstance().getPeerHeights().containsKey(peer)) {
			o.put("height", Controller.getInstance().getHeightOfPeer(peer));
		}
		if(Controller.getInstance().getPeersVersions().containsKey(peer)) {
			o.put("version", Controller.getInstance().getVersionOfPeer(peer).getA());
			o.put("buildTime", DateTimeFormat.timestamptoString(Controller.getInstance().getVersionOfPeer(peer).getB(), "yyyy-MM-dd HH:mm:ss z", "UTC"));
		}
		if(peer.hasPinger())	{
			o.put("ping", peer.getPing());
		}
		if(peer.getConnectionTime()>0) {
			o.put("onlineTime", (NTP.getTime() - peer.getConnectionTime())/1000);
		}
		
		
		if(QoraDb.getInstance().getPeerMap().contains(peer.getAddress().getAddress()))
		{
			PeerInfo peerInfo = QoraDb.getInstance().getPeerMap().getInfo(peer.getAddress());
			
			o.put("findingTime", DateTimeFormat.timestamptoString(peerInfo.getFindingTime()));
			o.put("findingTimeStamp", peerInfo.getFindingTime());
	
			if(peerInfo.getWhiteConnectTime()>0) {
				o.put("lastWhite", DateTimeFormat.timestamptoString(peerInfo.getWhiteConnectTime()));
				o.put("lastWhiteTimeStamp", peerInfo.getWhiteConnectTime());
	
			} else {
				o.put("lastWhite", "never");
			}
			if(peerInfo.getGrayConnectTime()>0) {
				o.put("lastGray", DateTimeFormat.timestamptoString(peerInfo.getGrayConnectTime()));
				o.put("lastGrayTimeStamp", peerInfo.getGrayConnectTime());
			} else {
				o.put("lastGray", "never");
			}
			o.put("whitePingCounter", peerInfo.getWhitePingCouner());

			if (peerInfo.isBlacklisted())
				o.put("isBlacklisted", "true");
		}
		
		if(o.size() == 0){
			o.put("status", "unknown disconnected");
		}
		
		return o; 
	}
	
	
	@SuppressWarnings("unchecked")
	@GET
	@Path("best")
	public String getTopPeers()
	{
		List<Peer> peers = PeerManager.getInstance().getBestPeers();
		JSONArray array = new JSONArray();
		
		for(Peer peer: peers)
		{
			array.add(peer.getAddress().getHostAddress());
		}
		
		return array.toJSONString();
	}
	
	@SuppressWarnings({ "unchecked" })
	@GET
	@Path("known")
	public String getKnown() throws UnknownHostException
	{
		List<String> addresses = QoraDb.getInstance().getPeerMap().getAllPeersAddresses(-1);
		
		JSONArray array = new JSONArray();

		array.addAll(addresses);
		
		return array.toJSONString();
	}
	
	@SuppressWarnings({ "unchecked" })
	@GET
	@Path("preset")
	public String getPreset()
	{
		List<String> addresses = new ArrayList<>();
		for (Peer peer : Settings.getInstance().getKnownPeers()) {
			addresses.add(peer.getAddress().getHostAddress());
		}
		
		JSONArray array = new JSONArray();
		
		array.addAll(addresses);

		return array.toJSONString();
	}
	
	@DELETE
	@Path("/known")
	public String clearPeers()
	{
		QoraDb.getInstance().getPeerMap().reset();
		
		return "OK";
	}

	@POST
	@Path("/blacklist")
	public String blacklistPeer(String address) {
		APIUtils.askAPICallAllowed("POST peers/blacklist " + address, request);

		// CHECK WALLET UNLOCKED
		if (Controller.getInstance().doesWalletExists() && !Controller.getInstance().isWalletUnlocked()) {
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_WALLET_LOCKED);
		}

		Peer peer;
		try {
			peer = new Peer(InetAddress.getByName(address));
		} catch (UnknownHostException e) {
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_INVALID_NETWORK_ADDRESS);
		}

		PeerManager.getInstance().blacklistPeer(peer);

		return "OK";
	}

}
