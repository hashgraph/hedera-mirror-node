/*
 * (c) 2016-2018 Swirlds, Inc.
 *
 * This software is the confidential and proprietary information of
 * Swirlds, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Swirlds.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

package com.hedera.platform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Encapsulates a cryptographic signature along with the public key to use during verification. In order to maintain the
 * overall throughput and latency profiles of the hashgraph implementation, this class is an immutable representation of
 * a cryptographic signature. Multiple overloaded constructors have been provided to facilitate ease of use when copying
 * an existing signature.
 */
public class Signature {

	/** Pointer to the transaction contents */
	private byte[] contents;

	/** (Optional) Pointer to actual public key */
	private byte[] expandedPublicKey;

	/** The offset of the message contained in the contents array */
	private int messageOffset;

	/** The length of the message contained in the contents array */
	private int messageLength;

	/** The offset of the public key contained in the contents array */
	private int publicKeyOffset;

	/** The length of the public key contained in the contents array */
	private int publicKeyLength;

	/** The offset of the signature contained in the contents array */
	private int signatureOffset;

	/** The length of the signature contained in the contents array */
	private int signatureLength;

	/** The type of cryptographic algorithm used to create the signature */
	private SignatureType signatureType;


	/**
	 * Constructs an immutable Ed25519 signature using the provided signature pointer, public key pointer, and original
	 * message pointer.
	 *
	 * @param contents
	 * 		a pointer to a byte buffer containing the message, signature, and public key
	 * @param signatureOffset
	 * 		the index where the signature begins in the contents array
	 * @param signatureLength
	 * 		the length of the signature (in bytes)
	 * @param publicKeyOffset
	 * 		the index where the public key begins in the contents array
	 * @param publicKeyLength
	 * 		the length of the public key (in bytes)
	 * @param messageOffset
	 * 		the index where the message begins in the contents array
	 * @param messageLength
	 * 		the length of the message (in bytes)
	 * @throws NullPointerException
	 * 		if the {@code contents} array is null or zero length
	 * @throws IllegalArgumentException
	 * 		if any of the offsets or lengths fall outside the bounds of the {@code contents}
	 * 		array
	 */
	public Signature(final byte[] contents, final int signatureOffset, final int signatureLength,
			final int publicKeyOffset, final int publicKeyLength, final int messageOffset, final int messageLength) {
		this(contents, signatureOffset, signatureLength, publicKeyOffset, publicKeyLength, messageOffset, messageLength,
				SignatureType.ED25519);

	}

	/**
	 * Constructs an immutable Ed25519 signature using the provided signature pointer, public key pointer, and original
	 * message pointer.
	 *
	 * @param contents
	 * 		a pointer to a byte buffer containing the message, signature, and public key
	 * @param signatureOffset
	 * 		the index where the signature begins in the contents array
	 * @param signatureLength
	 * 		the length of the signature (in bytes)
	 * @param expandedPublicKey
	 * 		an optional byte array from which retrieve the public key
	 * @param publicKeyOffset
	 * 		the index where the public key begins in the contents array
	 * @param publicKeyLength
	 * 		the length of the public key (in bytes)
	 * @param messageOffset
	 * 		the index where the message begins in the contents array
	 * @param messageLength
	 * 		the length of the message (in bytes)
	 * @throws NullPointerException
	 * 		if the {@code contents} array is null or zero length
	 * @throws IllegalArgumentException
	 * 		if any of the offsets or lengths fall outside the bounds of the {@code contents}
	 * 		array
	 */
	public Signature(final byte[] contents, final int signatureOffset, final int signatureLength,
			final byte[] expandedPublicKey, final int publicKeyOffset, final int publicKeyLength,
			final int messageOffset, final int messageLength) {
		this(contents, signatureOffset, signatureLength, expandedPublicKey, publicKeyOffset, publicKeyLength,
				messageOffset, messageLength, SignatureType.ED25519);

	}

	/**
	 * Constructs an immutable signature of the given cryptographic algorithm using the provided signature pointer,
	 * public key pointer, and original message pointer.
	 *
	 * @param contents
	 * 		a pointer to a byte buffer containing the message, signature, and public key
	 * @param signatureOffset
	 * 		the index where the signature begins in the contents array
	 * @param signatureLength
	 * 		the length of the signature (in bytes)
	 * @param publicKeyOffset
	 * 		the index where the public key begins in the contents array
	 * @param publicKeyLength
	 * 		the length of the public key (in bytes)
	 * @param messageOffset
	 * 		the index where the message begins in the contents array
	 * @param messageLength
	 * 		the length of the message (in bytes)
	 * @param signatureType
	 * 		the cryptographic algorithm used to create the signature
	 * @throws NullPointerException
	 * 		if the {@code contents} array is null or zero length
	 * @throws IllegalArgumentException
	 * 		if any of the offsets or lengths fall outside the bounds of the {@code contents}
	 * 		array
	 */
	public Signature(final byte[] contents, final int signatureOffset, final int signatureLength,
			final int publicKeyOffset, final int publicKeyLength, final int messageOffset, final int messageLength,
			final SignatureType signatureType) {
		this(contents, signatureOffset, signatureLength, null, publicKeyOffset, publicKeyLength, messageOffset,
				messageLength, signatureType);
	}

	/**
	 * Constructs an immutable signature of the given cryptographic algorithm using the provided signature pointer,
	 * public key pointer, and original message pointer.
	 *
	 * @param contents
	 * 		a pointer to a byte buffer containing the message, signature, and public key
	 * @param signatureOffset
	 * 		the index where the signature begins in the contents array
	 * @param signatureLength
	 * 		the length of the signature (in bytes)
	 * @param expandedPublicKey
	 * 		an optional byte array from which retrieve the public key
	 * @param publicKeyOffset
	 * 		the index where the public key begins in the contents array
	 * @param publicKeyLength
	 * 		the length of the public key (in bytes)
	 * @param messageOffset
	 * 		the index where the message begins in the contents array
	 * @param messageLength
	 * 		the length of the message (in bytes)
	 * @param signatureType
	 * 		the cryptographic algorithm used to create the signature
	 * @throws NullPointerException
	 * 		if the {@code contents} array is null or zero length
	 * @throws IllegalArgumentException
	 * 		if any of the offsets or lengths fall outside the bounds of the {@code contents}
	 * 		array
	 */
	public Signature(final byte[] contents, final int signatureOffset, final int signatureLength,
			final byte[] expandedPublicKey, final int publicKeyOffset, final int publicKeyLength,
			final int messageOffset, final int messageLength, final SignatureType signatureType) {
		if (contents == null || contents.length == 0) {
			throw new NullPointerException("contents");
		}

		final byte[] publicKeySource = (expandedPublicKey != null) ? expandedPublicKey : contents;

		if (signatureOffset < 0 || signatureOffset > contents.length) {
			throw new IllegalArgumentException("signatureOffset");
		}

		if (signatureLength < 0 || signatureLength > contents.length
				|| signatureLength + signatureOffset > contents.length) {
			throw new IllegalArgumentException("signatureLength");
		}

		if (publicKeyOffset < 0 || publicKeyOffset > publicKeySource.length) {
			throw new IllegalArgumentException("publicKeyOffset");
		}

		if (publicKeyLength < 0 || publicKeyLength > publicKeySource.length
				|| publicKeyLength + publicKeyOffset > publicKeySource.length) {
			throw new IllegalArgumentException("publicKeyLength");
		}

		if (messageOffset < 0 || messageOffset > contents.length) {
			throw new IllegalArgumentException("messageOffset");
		}

		if (messageLength < 0 || messageLength > contents.length || messageLength + messageOffset > contents.length) {
			throw new IllegalArgumentException("messageLength");
		}

		this.contents = contents;
		this.expandedPublicKey = expandedPublicKey;

		this.signatureOffset = signatureOffset;
		this.signatureLength = signatureLength;

		this.publicKeyOffset = publicKeyOffset;
		this.publicKeyLength = publicKeyLength;

		this.messageOffset = messageOffset;
		this.messageLength = messageLength;

		this.signatureType = signatureType;
	}

	/**
	 * Constructs a shallow copy of an existing signature replacing the public key indices and original message indices
	 * with the provided values.
	 *
	 * @param other
	 * 		the Signature to be copied
	 * @param publicKeyOffset
	 * 		an updated public key offset
	 * @param publicKeyLength
	 * 		an updated public key length
	 * @param messageOffset
	 * 		an updated message offset
	 * @param messageLength
	 * 		an updated message length
	 * @throws NullPointerException
	 * 		if the {@code other} parameter is null
	 * @throws IllegalArgumentException
	 * 		if any of the offsets or lengths fall outside the bounds of the {@code contents}
	 * 		array
	 */
	public Signature(final Signature other, final int publicKeyOffset, final int publicKeyLength,
			final int messageOffset, final int messageLength) {
		this(other, null, publicKeyOffset, publicKeyLength, messageOffset, messageLength);
	}

	/**
	 * Constructs a shallow copy of an existing signature replacing the public key source, public key indices and
	 * original message indices with the provided values.
	 *
	 * @param other
	 * 		the Signature to be copied
	 * @param expandedPublicKey
	 * 		an optional byte array from which retrieve the public key
	 * @param publicKeyOffset
	 * 		an updated public key offset
	 * @param publicKeyLength
	 * 		an updated public key length
	 * @param messageOffset
	 * 		an updated message offset
	 * @param messageLength
	 * 		an updated message length
	 * @throws NullPointerException
	 * 		if the {@code other} parameter is null
	 * @throws IllegalArgumentException
	 * 		if any of the offsets or lengths fall outside the bounds of the {@code contents}
	 * 		array
	 */
	public Signature(final Signature other, final byte[] expandedPublicKey, final int publicKeyOffset,
			final int publicKeyLength, final int messageOffset, final int messageLength) {
		if (other == null) {
			throw new NullPointerException("other");
		}

		final byte[] publicKeySource = (expandedPublicKey != null) ? expandedPublicKey : other.contents;

		if (publicKeyOffset < 0 || publicKeyOffset > publicKeySource.length) {
			throw new IllegalArgumentException("publicKeyOffset");
		}

		if (publicKeyLength < 0 || publicKeyLength > publicKeySource.length
				|| publicKeyLength + publicKeyOffset > publicKeySource.length) {
			throw new IllegalArgumentException("publicKeyLength");
		}

		if (messageOffset < 0 || messageOffset > other.contents.length) {
			throw new IllegalArgumentException("messageOffset");
		}

		if (messageLength < 0 || messageLength > other.contents.length
				|| messageLength + messageOffset > other.contents.length) {
			throw new IllegalArgumentException("messageLength");
		}

		this.contents = other.contents;
		this.signatureOffset = other.signatureOffset;
		this.signatureLength = other.signatureLength;
		this.expandedPublicKey = expandedPublicKey;
		this.publicKeyOffset = publicKeyOffset;
		this.publicKeyLength = publicKeyLength;
		this.messageOffset = messageOffset;
		this.messageLength = messageLength;
		this.signatureType = other.signatureType;
	}

	/**
	 * Reconstructs a {@link Signature} object from a binary representation read from a {@link DataInputStream}.
	 *
	 * @param dis
	 * 		the {@link DataInputStream} from which to read
	 * @param byteCount
	 * 		returns the number of bytes written as the first element in the array or increments the existing
	 * 		value by the number of bytes written
	 * @return the {@link Signature} that was read from the input stream
	 * @throws IOException
	 * 		if any error occurs while reading from the {@link DataInputStream}
	 * @throws NullPointerException
	 * 		if the {@code dis} parameter is null
	 * @throws IOException
	 * 		if the internal checksum cannot be
	 * 		validated
	 */
	static Signature deserialize(final DataInputStream dis, final int[] byteCount, MessageDigest md) throws IOException {
		if (dis == null) {
			throw new NullPointerException("dis");
		}

		final int[] totalBytes = new int[] { 7 * Integer.BYTES };

		// Read Signature Length w/ Simple Prime Number Checksum
		final int sigLen = dis.readInt();
		final int sigChecksum = dis.readInt();

		if (sigLen < 0 || sigChecksum != (439 - sigLen)) {
			throw new IOException("Signature.deserialize tried to create signature array of length "
					+ sigLen + " with wrong checksum.");
		}

		// Read Signature
		final SignatureType sigType = SignatureType.from(dis.readInt(), SignatureType.ED25519);
		final byte[] sig = new byte[sigLen];
		dis.readFully(sig);
		totalBytes[0] += sig.length;

		// Read Public Key Length w/ Simple Prime Number Checksum
		final int pkLen = dis.readInt();
		final int pkChecksum = dis.readInt();

		if (pkLen < 0 || pkChecksum != (541 - pkLen)) {
			throw new IOException("Signature.deserialize tried to create public key array of length "
					+ pkLen + " with wrong checksum.");
		}

		// Read Public Key
		final byte[] pk = new byte[pkLen];
		if (pkLen > 0) {
			dis.readFully(pk);
			totalBytes[0] += pk.length;
		}

		// Read Message Length w/ Simple Prime Number Checksum
		final int msgLen = dis.readInt();
		final int msgChecksum = dis.readInt();

		if (msgLen < 0 || msgChecksum != (647 - msgLen)) {
			throw new IOException(
					"Signature.deserialize tried to create message array of length " + pkLen + " with wrong checksum.");
		}

		// Read Message
		final byte[] msg = new byte[msgLen];
		if (msgLen > 0) {
			dis.readFully(msg);
			totalBytes[0] += msg.length;
		}

		if (byteCount != null && byteCount.length > 0) {
			byteCount[0] += totalBytes[0];
		}

		final ByteBuffer buffer = ByteBuffer.allocate(msgLen + pkLen + sigLen);

		buffer.put(msg);
		buffer.put(pk);
		buffer.put(sig);

		return new Signature(buffer.array(), msg.length + pk.length, sig.length, msg.length, pk.length, 0, msg.length,
				sigType);
	}

	/**
	 * Returns the transaction payload. This method returns a copy of the original payload.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @return the transaction payload
	 */
	public byte[] getContents() {
		return (contents != null) ? contents.clone() : null;
	}

	/**
	 * Internal use accessor that returns a direct (mutable) reference to the transaction contents/payload. Care must be
	 * taken to never modify the array returned by this accessor. Modifying the array will result in undefined behaviors
	 * and will result in a violation of the immutability contract provided by the {@link Signature} object.
	 *
	 * This method exists solely to allow direct access by the platform for performance reasons.
	 *
	 * @return a direct reference to the transaction content/payload
	 */
	byte[] getContentsDirect() {
		return contents;
	}

	/**
	 * Returns a copy of the optional expanded public key or {@code null} if not provided.
	 *
	 * @return the optional expanded public key if provided, otherwise {@code null}
	 */
	public byte[] getExpandedPublicKey() {
		return (expandedPublicKey != null) ? expandedPublicKey.clone() : null;
	}

	/**
	 * Internal use accessor that returns a direct (mutable) reference to the expanded public key. Care must be taken to
	 * never modify the array returned by this accessor. Modifying the array will result in undefined behaviors and will
	 * result in a violation of the immutability contract provided by the {@link Signature} object.
	 *
	 * This method exists solely to allow direct access by the platform for performance reasons.
	 *
	 * @return a direct reference to the transaction content/payload
	 */
	byte[] getExpandedPublicKeyDirect() {
		return expandedPublicKey;
	}

	/**
	 * Returns the offset in the {@link #getContents()} array where the message begins.
	 *
	 * @return the offset to the beginning of the message
	 */
	public int getMessageOffset() {
		return messageOffset;
	}

	/**
	 * Returns the length in bytes of the message.
	 *
	 * @return the length in bytes
	 */
	public int getMessageLength() {
		return messageLength;
	}

	/**
	 * Returns the offset where the public key begins. By default this is an index into the {@link #getContents()}
	 * array. If the {@link #getExpandedPublicKey()} is provided, then this is an index in the
	 * {@link #getExpandedPublicKey()} array.
	 *
	 * @return the offset to the beginning of the public key
	 */
	public int getPublicKeyOffset() {
		return publicKeyOffset;
	}

	/**
	 * Returns the length in bytes of the public key.
	 *
	 * @return the length in bytes
	 */
	public int getPublicKeyLength() {
		return publicKeyLength;
	}

	/**
	 * Returns the offset in the {@link #getContents()} array where the signature begins.
	 *
	 * @return the offset to the beginning of the signature
	 */
	public int getSignatureOffset() {
		return signatureOffset;
	}

	/**
	 * Returns the length in bytes of the signature.
	 *
	 * @return the length in bytes
	 */
	public int getSignatureLength() {
		return signatureLength;
	}

	/**
	 * Returns the type of cryptographic algorithm used to create &amp; verify this signature.
	 *
	 * @return the type of cryptographic algorithm
	 */
	public SignatureType getSignatureType() {
		return signatureType;
	}


	@Override
	public String toString() {
		return "Signature{" +
				"contents=" + Arrays.toString(contents) +
				", expandedPublicKey=" + Arrays.toString(expandedPublicKey) +
				", messageOffset=" + messageOffset +
				", messageLength=" + messageLength +
				", publicKeyOffset=" + publicKeyOffset +
				", publicKeyLength=" + publicKeyLength +
				", signatureOffset=" + signatureOffset +
				", signatureLength=" + signatureLength +
				", signatureType=" + signatureType +
				'}';
	}
}
