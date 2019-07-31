package com.hedera.mirrorNodeProxy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.mirrorservice.CryptoServiceMirror;
import com.hedera.mirrorservice.FileServiceMirror;
import com.hedera.mirrorservice.SmartContractServiceMirror;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.AccountID;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The MirrorNodeProxy runs a grpc server that accepts HAPI transactions, and forwards them unchanged to the node specified in the transaction body. 
 */
public class MirrorNodeProxy {
	private static final Logger log = LogManager.getLogger("proxy");
	static final Marker MARKER = MarkerManager.getMarker("MIRROR_NODE");
	private static String nodeInfoFile;

	static HashMap<String, Pair<String, Integer>> accountIDHostPort;

	/**
	 * server thread for Netty
	 */
	private Server server;

	/**
	 * This accepts one parameter to start the proxy, a configuration file which specifies a port number to listen to as well as the json file name which contains a list of nodes's host and port.
	 */
	public static void main(String[] args) {
		ConfigLoader configLoader = new ConfigLoader("./config/config.json");
		new MirrorNodeProxy(configLoader.getProxyPort(), configLoader.getNodeInfoFile());
	}

	public MirrorNodeProxy(int port, String nodeInfoFileName) {
		nodeInfoFile = nodeInfoFileName;
		server = NettyServerBuilder.forPort(port)
				.addService(new CryptoServiceMirror())
				.addService(new FileServiceMirror())
				.addService(new SmartContractServiceMirror())
				.build();
		log.info(MARKER, "Starting NETTY server on port " + port);

		try {
			server.start();
			log.info(MARKER, "NettyServer STARTED .");
			Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownNetty));
			loadAccountIDHostPort();
			while(true){
				Thread.sleep(5000);
			}

		} catch (Throwable eth) {
			eth.printStackTrace();
			log.error(MARKER, eth.getMessage(), eth);
			log.info(MARKER, "MirrorNodeMain - ERROR starting GRPC server ! ");
		}

	}

	public static void loadAccountIDHostPort() {
		accountIDHostPort = new HashMap<>();
		log.info(MARKER, "Loading nodes info from " + nodeInfoFile);
		try {
			JsonObject nodesInfo = Utility.getJsonInput(nodeInfoFile);
			Set<Map.Entry<String, JsonElement>> entrySet = nodesInfo.entrySet();
			for (Map.Entry<String, JsonElement> entry : entrySet) {
				JsonObject jsonObject = entry.getValue().getAsJsonObject();
				String host = jsonObject.get("host").getAsString();
				int port = jsonObject.get("port").getAsInt();
				accountIDHostPort.put(entry.getKey(), Pair.of(host, port));
			}
			log.info(MARKER, "Loaded nodes info successfully");
		} catch (Exception ex) {
			log.error(MARKER, "Get an exception while loading NodesInfo {}", ex);
		}
	}

	public static Pair<String, Integer> getHostPort(AccountID accountID) {
		return accountIDHostPort.get(Utility.accountIDToString(accountID));
	}


	/**
	 * Placeholder for graceful shutdown of server
	 */
	void shutdownNetty() {
		try {
			log.info(MARKER, "NettyServer SHUTTING DOWN .");
			this.server.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			log.error(MARKER, "Error in shutdownNetty {}", ex);
		}
	}
}
