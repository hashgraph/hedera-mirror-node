package com.hedera.signatureVerifier;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.mirrorNodeProxy.Utility;
import com.hedera.recordFileParser.RecordFileParser;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NodeSignatureVerifier {
	private static final Logger log = LogManager.getLogger("recordStream-log");
	private static final Marker MARKER = MarkerManager.getMarker("NodeSignatureVerifier");

	private static String nodeAddressBookLocation;

	static final byte TYPE_PREV_HASH = 1;       // next 48 bytes are hash384 of previous files
	static final byte TYPE_RECORD = 2;          // next data type is transaction and its record
	static final byte TYPE_SIGNATURE = 3;       // the file content signature, should not be hashed
	static final byte TYPE_FILE_HASH = 4;       // next 48 bytes are hash384 of content of corresponding RecordFile

	Map<String, PublicKey> nodeIDPubKeyMap;

	public NodeSignatureVerifier(ConfigLoader configLoader) {
		nodeAddressBookLocation = configLoader.getAddressBookFile();

		nodeIDPubKeyMap = new HashMap<>();
		//load Node Details
		try {
			NodeAddressBook nodeAddressBook = NodeAddressBook.parseFrom(Utility.getBytes(nodeAddressBookLocation));
			loadNodePubKey(nodeAddressBook);
			//System.out.println(nodeAddressBook);
		} catch (InvalidProtocolBufferException ex) {
			log.error(MARKER, "Fail to parse NodeAddressBook from {}, error {}", nodeAddressBookLocation, ex.getMessage());
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
				log.error(MARKER, "Faild to load PublicKey from: {} for {}", nodeAddress.getRSAPubKey(), accountID);
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
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			log.error(MARKER, " Fail to convert to PublicKey - {}", e.getStackTrace());
		}
		return publicKey;
	}

	private PublicKey loadPublicKey(String rsaPubKeyString) throws DecoderException {
		return bytesToPublicKey(Utility.hexToBytes(rsaPubKeyString));
	}

	private static byte[] integerToBytes(int number) {
		ByteBuffer b = ByteBuffer.allocate(4);
		b.putInt(number);
		return b.array();
	}

	/**
	 * 1. Extract the Hash of the content of corresponding RecordStream file. This Hash is the signed Content of this signature
	 * 2. Extract signature from the file.
	 * @param file
	 * @return
	 */
	public Pair<byte[], byte[]> extractHashAndSigFromFile(File file) {
		FileInputStream stream = null;
		byte[] sig = null;

		if (file.exists() == false) {
			log.info(MARKER, "File does not exist " + file.getPath());
			return null;
		}

		try {
			stream = new FileInputStream(file);
			DataInputStream dis = new DataInputStream(stream);
			byte[] fileHash = new byte[48];

			while (dis.available() != 0) {
				try {
					byte typeDelimiter = dis.readByte();

					switch (typeDelimiter) {
						case TYPE_FILE_HASH:
							dis.read(fileHash);
							// log.info(MARKER, "File Hash = " + Hex.encodeHexString(fileHash));
							break;

						case TYPE_SIGNATURE:
							int sigLength = dis.readInt();
							// log.info(MARKER, "sigLength = " + sigLength);
							byte[] sigBytes = new byte[sigLength];
							dis.readFully(sigBytes);
							// log.info(MARKER, "File {} Signature = {} ", file.getName(), Hex.encodeHexString(sigBytes));
							sig = sigBytes;
							break;
						default:
							log.error(MARKER, "extractHashAndSigFromFile :: Exception Unknown record file delimiter {}", typeDelimiter);
					}

				} catch (Exception e) {
					log.error(MARKER, "extractHashAndSigFromFile :: Exception ", e);
					break;
				}
			}

			return Pair.of(fileHash, sig);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			log.error(MARKER, "extractHashAndSigFromFile :: File Not Found Error");
		} catch (IOException e) {
			log.error(MARKER, "extractHashAndSigFromFile :: IOException Error");
		} catch (Exception e) {
			log.error(MARKER, "extractHashAndSigFromFile :: Parsing Error");
		} finally {
			try {
				if (stream != null)
					stream.close();
			} catch (IOException ex) {
				log.error("extractHashAndSigFromFile :: Exception in close the stream {}", ex);
			}
		}

		return null;
	}

	public boolean verifySignatureFile(File sigFile) {
		Pair<byte[], byte[]> hashAndSig = extractHashAndSigFromFile(sigFile);

		//Signed Data is the Hash of unsigned File
		byte[] signedData = hashAndSig.getLeft();

		String nodeAccountID = Utility.getAccountIDStringFromFilePath(sigFile.getPath());
		byte[] signature = hashAndSig.getRight();

		boolean isValid = verifySignature(signedData, signature, nodeAccountID, sigFile.getPath());
		if (!isValid) {
			log.info(MARKER, "{} contains invalid signature", sigFile.getPath());
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
				byte[] hash = extractHashAndSigFromFile(sigFile).getLeft();
				String hashString = Hex.encodeHexString(hash);
				Set<File> files = hashToSigFiles.getOrDefault(hashString, new HashSet<>());
				files.add(sigFile);
				hashToSigFiles.put(hashString, files);
			} else {
				log.info(MARKER, "{} has invalid signature", sigFile.getName());
			}
		}

		for (String key : hashToSigFiles.keySet()) {
			if (Utility.greaterThanSuperMajorityNum(hashToSigFiles.get(key).size(),
					nodeIDPubKeyMap.size())){
				return new ArrayList<>(hashToSigFiles.get(key));
			}
		}
		return null;
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
			log.debug(MARKER, "verifySignature :: missing PublicKey of node{}", nodeAccountID);
			return false;
		}

		try {
			Signature sig = Signature.getInstance("SHA384withRSA", "SunRsaSign");
			log.debug(MARKER,
					"verifySignature :: signature is being verified, publicKey={}", publicKey);
			sig.initVerify(publicKey);
			sig.update(data);
			if (signature == null) {
				log.error(MARKER, " verifySignature :: signature is null for file {}", filePath);
				return false;
			}
			return sig.verify(signature);
		} catch (NoSuchAlgorithmException | NoSuchProviderException
				| InvalidKeyException | SignatureException e) {
			log.error(MARKER, " verifySignature :: Fail to verify Signature: {}, PublicKey: {}, NodeID: {}, File: {}, Exception: {}", signature, publicKey, nodeAccountID, e.getStackTrace());
		}
		return false;
	}

	/**
	 * Verify if a .rcd file's hash is equal to the hash contained in .rcd_sig file
	 * @return
	 */
	public boolean hashMatch(File sigFile, File rcdFile) {
		byte[] fileHash = RecordFileParser.getFileHash(rcdFile.getPath());
		return Arrays.equals(fileHash, extractHashAndSigFromFile(sigFile).getLeft());
	}

	public List<String> getNodeAccountIDs() {
		List<String> list = new ArrayList<>(nodeIDPubKeyMap.keySet());
		Collections.sort(list);
		return list;
	}

	public static void main(String[] args) {
	}
}
