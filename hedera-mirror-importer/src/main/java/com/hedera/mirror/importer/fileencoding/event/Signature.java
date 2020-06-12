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
import java.nio.ByteBuffer;
import lombok.Value;

/**
 * Encapsulates a cryptographic signature along with the public key to use during verification. In order to maintain the
 * overall throughput and latency profiles of the hashgraph implementation, this class is an immutable representation of
 * a cryptographic signature. Multiple overloaded constructors have been provided to facilitate ease of use when copying
 * an existing signature.
 */
@Value
public class Signature {

    // Contains message, signature, and public key
    private final byte[] contents;

    // Actual public key
    private final byte[] expandedPublicKey;

    // Offset of the message contained in the contents array
    private final int messageOffset;

    // Length of the message contained in the contents array
    private final int messageLength;

    // Offset of the public key contained in the contents array
    private final int publicKeyOffset;

    // Length of the public key contained in the contents array
    private final int publicKeyLength;

    // Offset of the signature contained in the contents array
    private final int signatureOffset;

    // Length of the signature contained in the contents array
    private final int signatureLength;

    // Bytes consumed from input stream to deserialize this signature
    private final int totalBytes;

    // Type of cryptographic algorithm used to create the signature
    private final SignatureType signatureType;

    /**
     * Constructs an immutable signature of the given cryptographic algorithm using the provided signature pointer,
     * public key pointer, and original message pointer.
     *
     * @throws IllegalArgumentException if the {@code contents} array is null or zero length
     * @throws IllegalArgumentException if any of the offsets or lengths fall outside the bounds of the {@code contents}
     *                                  array
     */
    public Signature(byte[] contents, int signatureOffset, int signatureLength,
                     byte[] expandedPublicKey, int publicKeyOffset, int publicKeyLength,
                     int messageOffset, int messageLength, int totalBytes, SignatureType signatureType) {
        if (contents == null || contents.length == 0) {
            throw new IllegalArgumentException("contents cannot be null or empty");
        }

        byte[] publicKeySource = (expandedPublicKey != null) ? expandedPublicKey : contents;

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

        this.totalBytes = totalBytes;
        this.signatureType = signatureType;
    }

    /**
     * Reconstructs a {@link Signature} object from a binary representation read from a {@link DataInputStream}.
     *
     * @param dis       the {@link DataInputStream} from which to read
     * @return the {@link Signature} that was read from the input stream
     * @throws IOException          if any error occurs while reading from the {@link DataInputStream}
     * @throws IOException          if the internal checksum cannot be validated
     */
    static Signature deserialize(DataInputStream dis) throws IOException {
        int totalBytes = 7 * Integer.BYTES;

        // Read Signature Length w/ Simple Prime Number Checksum
        int sigLen = dis.readInt();
        int sigChecksum = dis.readInt();

        if (sigLen < 0 || sigChecksum != (439 - sigLen)) {
            throw new IOException("Signature.deserialize tried to create signature array of length "
                    + sigLen + " with wrong checksum.");
        }

        // Read Signature
        SignatureType sigType = SignatureType.from(dis.readInt(), SignatureType.ED25519);
        byte[] sig = new byte[sigLen];
        dis.readFully(sig);
        totalBytes += sig.length;

        // Read Public Key Length w/ Simple Prime Number Checksum
        int pkLen = dis.readInt();
        int pkChecksum = dis.readInt();

        if (pkLen < 0 || pkChecksum != (541 - pkLen)) {
            throw new IOException("Signature.deserialize tried to create public key array of length "
                    + pkLen + " with wrong checksum.");
        }

        // Read Public Key
        byte[] pk = new byte[pkLen];
        if (pkLen > 0) {
            dis.readFully(pk);
            totalBytes += pk.length;
        }

        // Read Message Length w/ Simple Prime Number Checksum
        int msgLen = dis.readInt();
        int msgChecksum = dis.readInt();

        if (msgLen < 0 || msgChecksum != (647 - msgLen)) {
            throw new IOException(
                    "Signature.deserialize tried to create message array of length " + pkLen + " with wrong checksum.");
        }

        // Read Message
        byte[] msg = new byte[msgLen];
        if (msgLen > 0) {
            dis.readFully(msg);
            totalBytes += msg.length;
        }

        byte[] contents = ByteBuffer.allocate(msgLen + pkLen + sigLen).put(msg).put(pk).put(sig).array();

        return new Signature(contents, msg.length + pk.length, sig.length, null, msg.length, pk.length, 0, msg.length,
                totalBytes, sigType);
    }
}
