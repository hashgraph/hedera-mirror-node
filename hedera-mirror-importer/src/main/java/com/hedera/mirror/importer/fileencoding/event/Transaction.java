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
package com.hedera.mirror.importer.fileencoding.event;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import java.io.DataInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import lombok.Value;

import com.hedera.mirror.importer.util.Utility;

/**
 * A hashgraph transaction that consists of an array of bytes and a list of immutable {@link Signature} objects.
 */
@Value
public class Transaction {

    // Content (payload) of the transaction
    private final byte[] contents;

    private com.hederahashgraph.api.proto.java.Transaction transaction;

    // List of optional signatures attached to this transaction
    private final List<Signature> signatures;

    // Bytes consumed from input stream to deserialize this transaction
    private final int totalBytes;

    // Flag indicating whether this transaction was originated by the application or the platform
    private final boolean system;

    /**
     * Constructs a new transaction with an optional list of associated signatures.
     *
     * @param contents   the binary content/payload of the Swirld transaction
     * @param system     {@code true} if this is a system transaction; {@code false} if this is an application
     *                   transaction
     * @param signatures an optional list of signatures to be included with this transaction
     * @throws IllegalArgumentException if the {@code contents} parameter is null or a zero-length array
     */
    Transaction(byte[] contents, boolean system, int totalBytes, List<Signature> signatures) {
        if (contents == null || contents.length == 0) {
            throw new IllegalArgumentException("contents cannot be null or empty");
        }
        this.contents = contents.clone();
        this.system = system;
        this.totalBytes = totalBytes;
        this.signatures = signatures;
        this.transaction = null;
        //this.transaction = com.hederahashgraph.api.proto.java.Transaction.parseFrom(contents);
    }

    /**
     * Reconstructs a {@link Transaction} object from a binary representation read from a {@link DataInputStream}.
     *
     * @param dis    the {@link DataInputStream} from which to read
     * @return the {@link Transaction} that was read from the input stream
     * @throws IOException          if any error occurs while reading from the {@link DataInputStream}
     * @throws IOException          if the internal checksum cannot be validated
     */
    private static Transaction deserialize(DataInputStream dis, MessageDigest md) throws IOException {
        int totalBytes = (4 * Integer.BYTES) + Byte.BYTES;

        // Read Content Length w/ Simple Prime Number Checksum
        int txLen = dis.readInt();
        md.update(Utility.integerToBytes(txLen));

        int txChecksum = dis.readInt();
        md.update(Utility.integerToBytes(txChecksum));

        if (txLen < 0 || txChecksum != (277 - txLen)) {
            throw new IOException("Transaction.deserialize tried to create contents array of length "
                    + txLen + " with wrong checksum.");
        }

        // Read Content
        boolean system = dis.readBoolean();
        md.update(booleanToByte(system));

        byte[] contents = new byte[txLen];
        dis.readFully(contents);
        md.update(contents);

        totalBytes += contents.length;

        // Read Signature Length w/ Simple Prime Number Checksum
        int sigLen = dis.readInt();
        md.update(Utility.integerToBytes(sigLen));

        int sigChecksum = dis.readInt();
        md.update(Utility.integerToBytes(sigChecksum));

        if (sigLen < 0 || sigChecksum != (353 - sigLen)) {
            throw new IOException("Transaction.deserialize tried to create signature array of length "
                    + txLen + " with wrong checksum.");
        }

        // Read Signatures
        List<Signature> sigs = null;
        if (sigLen > 0) {
            sigs = new ArrayList<>(sigLen);
            for (int i = 0; i < sigLen; i++) {
                var sig = Signature.deserialize(dis);
                sigs.add(sig);
                totalBytes += sig.getTotalBytes();
            }
        }
        //add number of bytes in current Transaction into counts[0]
        return new Transaction(contents, system, totalBytes, sigs);
    }

    /**
     * Reconstructs an array of {@link Transaction} objects from a {@link DataInputStream}.
     *
     * @throws IOException  if any error occurs while reading from the {@link DataInputStream}
     * @throws IOException  if the internal checksum cannot be validated
     */
    public static List<Transaction> readArray(DataInputStream dis, MessageDigest md) throws IOException {
        int txLen = dis.readInt();
        md.update(Utility.integerToBytes(txLen));

        int txChecksum = dis.readInt();
        md.update(Utility.integerToBytes(txChecksum));

        if (txLen < 0 || txChecksum != (1873 - txLen)) {
            throw new IOException("Transaction.readArray tried to create transaction array of length "
                    + txLen + " with wrong checksum.");
        }

        List<Transaction> transactions = new ArrayList<>(txLen);
        for (int i = 0; i < txLen; i++) {
            transactions.add(deserialize(dis, md));
        }
        return transactions;
    }

    private static byte booleanToByte(boolean value) {
        return value ? (byte) 1 : (byte) 0;
    }
}
