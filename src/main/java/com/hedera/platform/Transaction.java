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

import javax.swing.text.Utilities;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A hashgraph transaction that consists of an array of bytes and a list of immutable {@link Signature} objects. The
 * contents of the transaction is completely immutable; however, the list of signatures features controlled mutability
 * with a thread-safe and atomic implementation. The transaction internally uses a {@link ReadWriteLock} to provide
 * atomic reads and writes to the underlying list of signatures.
 * <p>
 * Selectively combining controlled mutability for certain aspects while using immutability for the rest grants a
 * significant performance improvement over a completely mutable or completely immutable object.
 * </p>
 */
public class Transaction {

	/** A per-transaction read/write lock to ensure thread safety of the signature list */
	private final ReadWriteLock readWriteLock;

	/** The content (payload) of the transaction */
	private byte[] contents;

	/** The list of optional signatures attached to this transaction */
	private List<Signature> signatures;

	/** A flag indicating whether this transaction was originated by the application or the platform */
	private boolean system;

	/**
	 * Constructs a new application transaction with no associated signatures.
	 *
	 * @param contents
	 * 		the binary content/payload of the Swirld transaction
	 * @throws NullPointerException
	 * 		if the {@code contents} parameter is null or a zero-length array
	 */
	public Transaction(final byte[] contents) {
		this(contents, false, (List<Signature>) null);
	}

	/**
	 * Constructs a new application transaction with an optional list of signatures.
	 *
	 * @param contents
	 * 		the binary content/payload of the Swirld transaction
	 * @param signatures
	 * 		an optional list of signatures to be included with this transaction
	 * @throws NullPointerException
	 * 		if the {@code contents} parameter is null or a zero-length array
	 */
	Transaction(final byte[] contents, final Signature... signatures) {
		this(contents, false, signatures);
	}

	/**
	 * Constructs a new application transaction with an optional list of signatures.
	 *
	 * @param contents
	 * 		the binary content/payload of the Swirld transaction
	 * @param signatures
	 * 		an optional list of signatures to be included with this transaction
	 * @throws NullPointerException
	 * 		if the {@code contents} parameter is null or a zero-length array
	 */
	Transaction(final byte[] contents, final List<Signature> signatures) {
		this(contents, false, signatures);
	}

	/**
	 * Constructs a new transaction with no associated signatures.
	 *
	 * @param contents
	 * 		the binary content/payload of the Swirld transaction
	 * @param system
	 *        {@code true} if this is a system transaction; {@code false} if this is an application transaction
	 * @throws NullPointerException
	 * 		if the {@code contents} parameter is null or a zero-length array
	 */
	Transaction(final byte[] contents, final boolean system) {
		this(contents, system, (List<Signature>) null);
	}

	/**
	 * Constructs a new transaction with an optional list of associated signatures.
	 *
	 * @param contents
	 * 		the binary content/payload of the Swirld transaction
	 * @param system
	 *        {@code true} if this is a system transaction; {@code false} if this is an application
	 * 		transaction
	 * @param signatures
	 * 		an optional list of signatures to be included with this transaction
	 * @throws NullPointerException
	 * 		if the {@code contents} parameter is null or a zero-length array
	 */
	Transaction(final byte[] contents, final boolean system, final Signature... signatures) {
		this(contents, system, (signatures != null && signatures.length > 0) ? Arrays.asList(signatures) : null);
	}

	/**
	 * Constructs a new transaction with an optional list of associated signatures.
	 *
	 * @param contents
	 * 		the binary content/payload of the Swirld transaction
	 * @param system
	 *        {@code true} if this is a system transaction; {@code false} if this is an application
	 * 		transaction
	 * @param signatures
	 * 		an optional list of signatures to be included with this transaction
	 * @throws NullPointerException
	 * 		if the {@code contents} parameter is null or a zero-length array
	 */
	Transaction(final byte[] contents, final boolean system, final List<Signature> signatures) {
		if (contents == null || contents.length == 0) {
			throw new NullPointerException("contents");
		}

		this.contents = contents.clone();
		this.system = system;

		if (signatures != null && !signatures.isEmpty()) {
			this.signatures = new ArrayList<>(signatures);
		}

		this.readWriteLock = new ReentrantReadWriteLock(false);
	}

	/**
	 * Reconstructs a {@link Transaction} object from a binary representation read from a {@link DataInputStream}.
	 *
	 * @param dis
	 * 		the {@link DataInputStream} from which to read
	 * @return the {@link Transaction} that was read from the input stream
	 * @throws IOException
	 * 		if any error occurs while reading from the {@link DataInputStream}
	 * @throws NullPointerException
	 * 		if the {@code dis} parameter is null
	 * @throws IOException
	 * 		if the internal checksum cannot be
	 * 		validated
	 */
	static Transaction deserialize(final DataInputStream dis) throws IOException {
		if (dis == null) {
			throw new NullPointerException("dis");
		}

		final int[] totalBytes = new int[] { (4 * Integer.BYTES) + Byte.BYTES };

		// Read Content Length w/ Simple Prime Number Checksum
		final int txLen = dis.readInt();
		final int txChecksum = dis.readInt();

		if (txLen < 0 || txChecksum != (277 - txLen)) {
			throw new IOException("Transaction.deserialize tried to create contents array of length "
					+ txLen + " with wrong checksum.");
		}

		// Read Content
		final boolean system = dis.readBoolean();
		final byte[] contents = new byte[txLen];
		dis.readFully(contents);
		totalBytes[0] += contents.length;

		// Read Signature Length w/ Simple Prime Number Checksum
		final int sigLen = dis.readInt();
		final int sigChecksum = dis.readInt();

		if (sigLen < 0 || sigChecksum != (353 - sigLen)) {
			throw new IOException("Transaction.deserialize tried to create signature array of length "
					+ txLen + " with wrong checksum.");
		}

		// Read Signatures
		final Signature[] sigs = (sigLen > 0) ? new Signature[sigLen] : null;

		if (sigLen > 0) {
			for (int i = 0; i < sigs.length; i++) {
				sigs[i] = Signature.deserialize(dis, totalBytes);
			}
		}
		return new Transaction(contents, system, sigs);
	}


	/**
	 * Reconstructs an array of {@link Transaction} objects from a {@link DataInputStream}.
	 *
	 * @param dis
	 * 		the {@link DataInputStream} from which to read
	 * @return the array of {@link Transaction} objects that was read from the input stream
	 * @throws IOException
	 * 		if any error occurs while reading from the {@link DataInputStream}
	 * @throws NullPointerException
	 * 		if the {@code dis} parameter is null
	 * @throws IOException
	 * 		if the internal checksum cannot be
	 * 		validated
	 */
	public static Transaction[] readArray(final DataInputStream dis) throws IOException {
		if (dis == null) {
			throw new NullPointerException("dis");
		}

		final int txLen = dis.readInt();
		final int txChecksum = dis.readInt();

		if (txLen < 0 || txChecksum != (1873 - txLen)) {
			throw new IOException("Transaction.readArray tried to create transaction array of length "
					+ txLen + " with wrong checksum.");
		}

		final Transaction[] trans = new Transaction[txLen];

		for (int i = 0; i < trans.length; i++) {
			trans[i] = deserialize(dis);
		}

		return trans;
	}

	/**
	 * Returns the transaction content (payload). This method returns a copy of the original content.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @return the transaction content/payload
	 */
	public byte[] getContents() {
		if (contents == null || contents.length == 0) {
			throw new NullPointerException("contents");
		}

		return contents.clone();
	}

	/**
	 * Returns the byte located at {@code index} position from the transaction content/payload.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @param index
	 * 		the index of the byte to be returned
	 * @return the byte located at {@code index} position
	 * @throws NullPointerException
	 * 		if the underlying transaction content is null or a zero-length array
	 * @throws ArrayIndexOutOfBoundsException
	 * 		if the {@code index} parameter is less than zero or greater than the
	 * 		maximum length of the contents
	 */
	public byte getContents(final int index) {
		if (contents == null || contents.length == 0) {
			throw new NullPointerException("contents");
		}

		if (index < 0 || index >= contents.length) {
			throw new ArrayIndexOutOfBoundsException();
		}

		return contents[index];
	}

	/**
	 * Internal use accessor that returns a direct (mutable) reference to the transaction contents/payload. Care must be
	 * taken to never modify the array returned by this accessor. Modifying the array will result in undefined behaviors
	 * and will result in a violation of the immutability contract provided by the {@link Transaction} object.
	 *
	 * This method exists solely to allow direct access by the platform for performance reasons.
	 *
	 * @return a direct reference to the transaction content/payload
	 */
	byte[] getContentsDirect() {
		return contents;
	}

	/**
	 * Returns the size of the transaction content/payload.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @return the length of the transaction content
	 */
	public int getLength() {
		return (contents != null) ? contents.length : 0;
	}

	/**
	 * Returns the size of the transaction content/payload.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @return the length of the transaction content
	 */
	public int size() {
		return getLength();
	}

	/**
	 * Returns a {@link List} of {@link Signature} objects associated with this transaction. This method returns a
	 * shallow copy of the original list.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @return a shallow copy of the original signature list
	 */
	public List<Signature> getSignatures() {
		final Lock readLock = readWriteLock.readLock();

		try {
			readLock.lock();
			return (signatures != null) ? new ArrayList<>(signatures) : new ArrayList<>(1);
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * Internal use accessor that returns a flag indicating whether this is a system transaction.
	 *
	 * @return {@code true} if this is a system transaction; otherwise {@code false} if this is an application
	 * 		transaction
	 */
	boolean isSystem() {
		return system;
	}

	/**
	 * Efficiently extracts and adds a new signature to this transaction bypassing the need to make copies of the
	 * underlying byte arrays.
	 *
	 * @param signatureOffset
	 * 		the offset in the transaction payload where the signature begins
	 * @param signatureLength
	 * 		the length in bytes of the signature
	 * @param publicKeyOffset
	 * 		the offset in the transaction payload where the public key begins
	 * @param publicKeyLength
	 * 		the length in bytes of the public key
	 * @param messageOffset
	 * 		the offset in the transaction payload where the message begins
	 * @param messageLength
	 * 		the length of the message in bytes
	 * @throws IllegalArgumentException
	 * 		if any of the provided offsets or lengths falls outside the array bounds
	 * @throws NullPointerException
	 * 		if the internal payload of this transaction is null or a zero length array
	 */
	public void extractSignature(final int signatureOffset, final int signatureLength, final int publicKeyOffset,
			final int publicKeyLength, final int messageOffset, final int messageLength) {
		add(new Signature(contents, signatureOffset, signatureLength, publicKeyOffset, publicKeyLength, messageOffset,
				messageLength));
	}

	/**
	 * Efficiently extracts and adds a new signature to this transaction bypassing the need to make copies of the
	 * underlying byte arrays. If the optional expanded public key is provided then the public key offset and length are
	 * indices into this array instead of the transaction payload.
	 *
	 * @param signatureOffset
	 * 		the offset in the transaction payload where the signature begins
	 * @param signatureLength
	 * 		the length in bytes of the signature
	 * @param expandedPublicKey
	 * 		an optional expanded form of the public key
	 * @param publicKeyOffset
	 * 		the offset where the public key begins
	 * @param publicKeyLength
	 * 		the length in bytes of the public key
	 * @param messageOffset
	 * 		the offset in the transaction payload where the message begins
	 * @param messageLength
	 * 		the length of the message in bytes
	 * @throws IllegalArgumentException
	 * 		if any of the provided offsets or lengths falls outside the array bounds
	 * @throws NullPointerException
	 * 		if the internal payload of this transaction is null or a zero length array
	 */
	public void extractSignature(final int signatureOffset, final int signatureLength, final byte[] expandedPublicKey,
			final int publicKeyOffset, final int publicKeyLength, final int messageOffset, final int messageLength) {
		add(new Signature(contents, signatureOffset, signatureLength, expandedPublicKey, publicKeyOffset,
				publicKeyLength, messageOffset, messageLength));
	}

	/**
	 * Adds a new {@link Signature} to this transaction.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @param signature
	 * 		the signature to be added
	 * @throws NullPointerException
	 * 		if the {@code signature} parameter is null
	 */
	public void add(final Signature signature) {
		if (signature == null) {
			throw new NullPointerException("signature");
		}

		final Lock writeLock = readWriteLock.writeLock();

		try {
			writeLock.lock();
			if (signatures == null) {
				signatures = new ArrayList<>(5);
			}

			signatures.add(signature);
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Adds a list of new {@link Signature} objects to this transaction.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @param signatures
	 * 		the list of signatures to be added
	 */
	public void addAll(final Signature... signatures) {
		if (signatures == null || signatures.length == 0) {
			return;
		}

		final Lock writeLock = readWriteLock.writeLock();

		try {
			writeLock.lock();
			if (this.signatures == null) {
				this.signatures = new ArrayList<>(signatures.length);
			}

			this.signatures.addAll(Arrays.asList(signatures));
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Removes a {@link Signature} from this transaction.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @param signature
	 * 		the signature to be removed
	 * @return {@code true} if the underlying list was modified; {@code false} otherwise
	 */
	public boolean remove(final Signature signature) {
		if (signature == null) {
			return false;
		}

		final Lock writeLock = readWriteLock.writeLock();

		try {
			writeLock.lock();
			if (signatures == null) {
				return false;
			}

			return signatures.remove(signature);
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Removes a list of {@link Signature} objects from this transaction.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @param signatures
	 * 		the list of signatures to be removed
	 * @return {@code true} if the underlying list was modified; {@code false} otherwise
	 */
	public boolean removeAll(final Signature... signatures) {
		if (signatures == null || signatures.length == 0) {
			return false;
		}

		final Lock writeLock = readWriteLock.writeLock();

		try {
			writeLock.lock();
			if (this.signatures == null) {
				return false;
			}

			return this.signatures.removeAll(Arrays.asList(signatures));
		} finally {
			writeLock.unlock();
		}
	}

	@Override
	public String toString() {
		return "Transaction{" +
				"contents=" + Arrays.toString(contents) +
				", signatures=" + signatures +
				", system=" + system +
				'}';
	}
}
