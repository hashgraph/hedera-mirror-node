package com.hedera.mirrorNodeProxy;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

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
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * The MirrorNodeProxy runs a grpc server that accepts HAPI transactions, and forwards them unchanged to the node specified in the transaction body. 
 */
@Log4j2
public class MirrorNodeProxy {

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
		log.info("Starting Netty server on port {}", port);

		try {
			server.start();
			log.info("Netty server started");
			Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownNetty));
			loadAccountIDHostPort();
			while(true){
				Thread.sleep(5000);
			}

		} catch (Throwable t) {
			log.error("Error starting GRPC server", t);
		}
	}

	public static void loadAccountIDHostPort() {
		accountIDHostPort = new HashMap<>();
		log.info("Loading nodes info from {}", nodeInfoFile);

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
				log.error("Address book file {} is empty or unavailable", ConfigLoader.getAddressBookFile());
			}
		} catch (IOException ex) {
			log.warn("Failed to load account IDs from {}", ConfigLoader.getAddressBookFile(), ex);
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
			log.info("Netty server shutting down");
			this.server.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			log.error("Error shutting down netty", ex);
		}
	}
}
