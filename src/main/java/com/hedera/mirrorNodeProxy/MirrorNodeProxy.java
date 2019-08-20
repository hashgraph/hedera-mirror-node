package com.hedera.mirrorNodeProxy;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.mirrorservice.CryptoServiceMirror;
import com.hedera.mirrorservice.FileServiceMirror;
import com.hedera.mirrorservice.SmartContractServiceMirror;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.IOException;
import java.util.HashMap;
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
		new MirrorNodeProxy(ConfigLoader.getProxyPort());
	}

	public MirrorNodeProxy(int port) {
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
			byte[] addressBookBytes = Utility.getBytes(ConfigLoader.getAddressBookFile());
			if (addressBookBytes != null) {
				NodeAddressBook nodeAddressBook = NodeAddressBook.parseFrom(addressBookBytes);
				for (NodeAddress address : nodeAddressBook.getNodeAddressList()) {
					String host = address.getIpAddress().toStringUtf8();
					int port = address.getPortno();
					String node = address.getMemo().toStringUtf8();
					accountIDHostPort.put(node, Pair.of(host, port));				}
			} else {
				log.error(MARKER, "Address book file {}, empty or unavailable", ConfigLoader.getAddressBookFile());
			}
		} catch (IOException ex) {
			log.warn(MARKER, "loadNodeAccountIDs - Fail to load from {}. Exception: {}", ConfigLoader.getAddressBookFile(), ex);
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
