package com.hedera.signatureverifier;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.configloader.ConfigLoader;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Log4j2
public class NodeSignatureVerifier {

	private static String nodeAddressBookLocation;

	Map<String, PublicKey> nodeIDPubKeyMap;

	public NodeSignatureVerifier() {
		nodeAddressBookLocation = ConfigLoader.getAddressBookFile();

		nodeIDPubKeyMap = new HashMap<>();
		//load Node Details
		try {
			byte[] addressBookBytes = Utility.getBytes(nodeAddressBookLocation);
			if (addressBookBytes != null) {
				NodeAddressBook nodeAddressBook = NodeAddressBook.parseFrom(addressBookBytes);
				loadNodePubKey(nodeAddressBook);
			} else {
				log.error("Address book file {} is empty or unavailable", nodeAddressBookLocation);
			}
		} catch (InvalidProtocolBufferException ex) {
			log.error("Failed to parse NodeAddressBook from {}", nodeAddressBookLocation, ex);
		}
	}

	public void loadNodePubKey(NodeAddressBook nodeAddressBook) {
		List<NodeAddress> nodeAddressList = nodeAddressBook.getNodeAddressList();
		for (NodeAddress nodeAddress : nodeAddressList) {
			// memo contains node's accountID string
			String accountID = new String(nodeAddress.getMemo().toByteArray());
			try {
				PublicKey publicKey = loadPublicKey(nodeAddress.getRSAPubKey());
				nodeIDPubKeyMap.put(accountID, publicKey);
			} catch(DecoderException ex) {
				log.error("Failed to load PublicKey from {} for account {}", nodeAddress.getRSAPubKey(), accountID, ex);
			}
		}
	}

	/**
	 * Convert the given public key into a byte array, in a format that bytesToPublicKey can read.
	 *
	 * @param key
	 * 		the public key to convert
	 * @return a byte array representation of the public key
	 */
	static byte[] publicKeyToBytes(PublicKey key) {
		return key.getEncoded();
	}

	/**
	 * Read a byte array created by publicKeyToBytes, and return the public key it represents
	 *
	 * @param bytes
	 * 		the byte array from publicKeyToBytes
	 * @return the public key represented by that byte array
	 */
	static PublicKey bytesToPublicKey(byte[] bytes) {
		PublicKey publicKey = null;
		try {
			EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bytes);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			publicKey = keyFactory.generatePublic(publicKeySpec);
		} catch (Exception e) {
			log.error("Failed to convert to PublicKey", e);
		}
		return publicKey;
	}

	private PublicKey loadPublicKey(String rsaPubKeyString) throws DecoderException {
		return bytesToPublicKey(Utility.hexToBytes(rsaPubKeyString));
	}

	public boolean verifySignatureFile(File sigFile) {
		Pair<byte[], byte[]> hashAndSig = Utility.extractHashAndSigFromFile(sigFile);

		//Signed Data is the Hash of unsigned File
		byte[] signedData = hashAndSig.getLeft();

		String nodeAccountID = Utility.getAccountIDStringFromFilePath(sigFile.getPath());
		byte[] signature = hashAndSig.getRight();

		boolean isValid = verifySignature(signedData, signature, nodeAccountID, sigFile.getPath());
		if (!isValid) {
			log.error("Invalid signature in file {}", sigFile.getPath());
		}
		return isValid;
	}

	/**
	 * Input: a list of a sig files which has the same timestamp
	 * Output: a list of valid sig files - being valid means the signature is valid and the Hash is agreed by super-majority nodes.
	 * 1. Verify that the signature files are signed by corresponding node's PublicKey;
	 * For invalid signature files, we will log them;
	 * 2. For valid signature files, we compare their Hashes to see if more than 2/3 Hashes matches. If more than 2/3 Hashes matches, we return a List of Files which contains this Hash
	 * @param sigFiles
	 * @return
	 */
	public List<File> verifySignatureFiles(List<File> sigFiles) {
		// If a signature is valid, we put the Hash in its content and its File to the map, to see if more than 2/3 valid signatures have the same Hash
		Map<String, Set<File>> hashToSigFiles = new HashMap<>();
		for (File sigFile : sigFiles) {
			if (verifySignatureFile(sigFile)) {
				byte[] hash = Utility.extractHashAndSigFromFile(sigFile).getLeft();
				String hashString = Hex.encodeHexString(hash);
				Set<File> files = hashToSigFiles.getOrDefault(hashString, new HashSet<>());
				files.add(sigFile);
				hashToSigFiles.put(hashString, files);
			}
		}

		for (String key : hashToSigFiles.keySet()) {
			if (Utility.greaterThanSuperMajorityNum(hashToSigFiles.get(key).size(),
					nodeIDPubKeyMap.size())){
				return new ArrayList<>(hashToSigFiles.get(key));
			}
		}
		return new ArrayList<>();
	}

	/**
	 * check whether the given signature is valid
	 *
	 * @param data
	 * 		the data that was signed
	 * @param signature
	 * 		the claimed signature of that data
	 * @param nodeAccountID
	 * 		the node's accountID string
	 * @return true if the signature is valid
	 */
	public boolean verifySignature(byte[] data, byte[] signature,
			String nodeAccountID, String filePath) {
		PublicKey publicKey = nodeIDPubKeyMap.get(nodeAccountID);
		if (publicKey == null) {
			log.warn("Missing PublicKey for node {}", nodeAccountID);
			return false;
		}

		if (signature == null) {
			log.error("Missing signature for file {}", filePath);
			return false;
		}

		try {
			log.trace("Verifying signature of file {} with public key of node {}", filePath, nodeAccountID);
			Signature sig = Signature.getInstance("SHA384withRSA", "SunRsaSign");
			sig.initVerify(publicKey);
			sig.update(data);
			return sig.verify(signature);
		} catch (Exception e) {
			log.error("Failed to verify Signature: {}, PublicKey: {}, NodeID: {}, File: {}", signature, publicKey, nodeAccountID, filePath, e);
		}
		return false;
	}

	public List<String> getNodeAccountIDs() {
		List<String> list = new ArrayList<>(nodeIDPubKeyMap.keySet());
		Collections.sort(list);
		return list;
	}
}
