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

import java.io.DataInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.hedera.utilities.Utility;

/**
 * A hashgraph transaction that consists of an array of bytes and a list of immutable {@link Signature} objects.
 * </p>
 */
public class Transaction {

    /**
     * The content (payload) of the transaction
     */
    private byte[] contents;

    private com.hederahashgraph.api.proto.java.Transaction transaction;

    /**
     * The list of optional signatures attached to this transaction
     */
    private List<Signature> signatures;

    /**
     * A flag indicating whether this transaction was originated by the application or the platform
     */
    private boolean system;

    /**
     * Constructs a new application transaction with no associated signatures.
     *
     * @param contents the binary content/payload of the Swirld transaction
     * @throws NullPointerException if the {@code contents} parameter is null or a zero-length array
     */
    public Transaction(final byte[] contents) throws InvalidProtocolBufferException {
        this(contents, false, (List<Signature>) null);
    }

    /**
     * Constructs a new transaction with an optional list of associated signatures.
     *
     * @param contents   the binary content/payload of the Swirld transaction
     * @param system     {@code true} if this is a system transaction; {@code false} if this is an application
     *                   transaction
     * @param signatures an optional list of signatures to be included with this transaction
     * @throws NullPointerException if the {@code contents} parameter is null or a zero-length array
     */
    Transaction(final byte[] contents, final boolean system, final Signature... signatures) throws InvalidProtocolBufferException {
        this(contents, system, (signatures != null && signatures.length > 0) ? Arrays.asList(signatures) : null);
    }

    /**
     * Constructs a new transaction with an optional list of associated signatures.
     *
     * @param contents   the binary content/payload of the Swirld transaction
     * @param system     {@code true} if this is a system transaction; {@code false} if this is an application
     *                   transaction
     * @param signatures an optional list of signatures to be included with this transaction
     * @throws NullPointerException if the {@code contents} parameter is null or a zero-length array
     */
    Transaction(final byte[] contents, final boolean system, final List<Signature> signatures) throws InvalidProtocolBufferException {
        if (contents == null || contents.length == 0) {
            throw new NullPointerException("contents");
        }

        this.contents = contents.clone();
        //this.transaction = com.hederahashgraph.api.proto.java.Transaction.parseFrom(contents);

        this.system = system;

        if (signatures != null && !signatures.isEmpty()) {
            this.signatures = new ArrayList<>(signatures);
        }
    }

    /**
     * Reconstructs a {@link Transaction} object from a binary representation read from a {@link DataInputStream}.
     *
     * @param dis    the {@link DataInputStream} from which to read
     * @param counts counts[0] denotes the number of bytes in the Transaction Array counts[1] denotes the number of
     *               system Transactions counts[2] denotes the number of application Transactions
     * @return the {@link Transaction} that was read from the input stream
     * @throws IOException          if any error occurs while reading from the {@link DataInputStream}
     * @throws NullPointerException if the {@code dis} parameter is null
     * @throws IOException          if the internal checksum cannot be validated
     */
    static Transaction deserialize(final DataInputStream dis, final int[] counts, MessageDigest md) throws IOException {
        if (dis == null) {
            throw new NullPointerException("dis");
        }

        final int[] totalBytes = new int[] {(4 * Integer.BYTES) + Byte.BYTES};

        // Read Content Length w/ Simple Prime Number Checksum
        final int txLen = dis.readInt();
        md.update(Utility.integerToBytes(txLen));

        final int txChecksum = dis.readInt();
        md.update(Utility.integerToBytes(txChecksum));

        if (txLen < 0 || txChecksum != (277 - txLen)) {
            throw new IOException("Transaction.deserialize tried to create contents array of length "
                    + txLen + " with wrong checksum.");
        }

        // Read Content
        final boolean system = dis.readBoolean();
        md.update(Utility.booleanToByte(system));

        final byte[] contents = new byte[txLen];
        dis.readFully(contents);
        md.update(contents);

        totalBytes[0] += contents.length;

        // Read Signature Length w/ Simple Prime Number Checksum
        final int sigLen = dis.readInt();
        md.update(Utility.integerToBytes(sigLen));

        final int sigChecksum = dis.readInt();
        md.update(Utility.integerToBytes(sigChecksum));

        if (sigLen < 0 || sigChecksum != (353 - sigLen)) {
            throw new IOException("Transaction.deserialize tried to create signature array of length "
                    + txLen + " with wrong checksum.");
        }

        // Read Signatures
        final Signature[] sigs = (sigLen > 0) ? new Signature[sigLen] : null;

        if (sigLen > 0) {
            for (int i = 0; i < sigs.length; i++) {
                sigs[i] = Signature.deserialize(dis, totalBytes, md);
            }
        }
        //add number of bytes in current Transaction into counts[0]
        counts[0] += totalBytes[0];
        return new Transaction(contents, system, sigs);
    }

    /**
     * Reconstructs an array of {@link Transaction} objects from a {@link DataInputStream}.
     *
     * @param dis    the {@link DataInputStream} from which to read
     * @param counts counts[0] denotes the number of bytes in the Transaction Array counts[1] denotes the number of
     *               system Transactions counts[2] denotes the number of application Transactions
     * @return the array of {@link Transaction} objects that was read from the input stream
     * @throws IOException          if any error occurs while reading from the {@link DataInputStream}
     * @throws NullPointerException if the {@code dis} parameter is null
     * @throws IOException          if the internal checksum cannot be validated
     */
    public static Transaction[] readArray(final DataInputStream dis, int[] counts, MessageDigest md) throws IOException {
        if (dis == null) {
            throw new NullPointerException("dis");
        }

        final int txLen = dis.readInt();
        md.update(Utility.integerToBytes(txLen));

        final int txChecksum = dis.readInt();
        md.update(Utility.integerToBytes(txChecksum));

        if (txLen < 0 || txChecksum != (1873 - txLen)) {
            throw new IOException("Transaction.readArray tried to create transaction array of length "
                    + txLen + " with wrong checksum.");
        }

        final Transaction[] trans = new Transaction[txLen];
        for (int i = 0; i < trans.length; i++) {
            trans[i] = deserialize(dis, counts, md);
            if (trans[i].isSystem()) {
                counts[1]++;
            } else {
                counts[2]++;
            }
        }

        return trans;
    }

    /**
     * Returns the transaction content (payload). This method returns a copy of the original content.
     * <p>
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
     * <p>
     * This method is thread-safe and guaranteed to be atomic in nature.
     *
     * @param index the index of the byte to be returned
     * @return the byte located at {@code index} position
     * @throws NullPointerException           if the underlying transaction content is null or a zero-length array
     * @throws ArrayIndexOutOfBoundsException if the {@code index} parameter is less than zero or greater than the
     *                                        maximum length of the contents
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
     * <p>
     * This method exists solely to allow direct access by the platform for performance reasons.
     *
     * @return a direct reference to the transaction content/payload
     */
    byte[] getContentsDirect() {
        return contents;
    }

    /**
     * Returns the size of the transaction content/payload.
     * <p>
     * This method is thread-safe and guaranteed to be atomic in nature.
     *
     * @return the length of the transaction content
     */
    public int getLength() {
        return (contents != null) ? contents.length : 0;
    }

    /**
     * Returns the size of the transaction content/payload.
     * <p>
     * This method is thread-safe and guaranteed to be atomic in nature.
     *
     * @return the length of the transaction content
     */
    public int size() {
        return getLength();
    }

    /**
     * Internal use accessor that returns a flag indicating whether this is a system transaction.
     *
     * @return {@code true} if this is a system transaction; otherwise {@code false} if this is an application
     * transaction
     */
    public boolean isSystem() {
        return system;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "contents =" + contents +
                ", signatures=" + signatures +
                ", system=" + system +
                '}';
    }
}
