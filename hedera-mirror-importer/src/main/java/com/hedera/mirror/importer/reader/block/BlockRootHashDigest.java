/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.reader.block;

import static com.hedera.mirror.common.domain.DigestAlgorithm.SHA_384;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.exception.StreamFileReaderException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.NonFinal;

/**
 * Calculates a block's root hash per the algorithm defined in HIP-1056. Note both the input merkle tree and the output
 * merkle tree are padded with SHA2-384 hash of an empty bytearray to be perfect binary trees.
 */
@NoArgsConstructor
@Value
class BlockRootHashDigest {

    private static final byte[] EMPTY_HASH = createMessageDigest().digest(new byte[0]);
    private static final String PREVIOUSHASH = "previousHash";
    private static final String STARTOFBLOCKSTATEHASH = "startOfBlockStateHash";

    @NonFinal
    private boolean finalized;

    private List<byte[]> inputHashes = new ArrayList<>();

    private List<byte[]> outputHashes = new ArrayList<>();

    @NonFinal
    private byte[] previousHash;

    @NonFinal
    private byte[] startOfBlockStateHash;

    public void addInputBlockItem(BlockItem blockItem) {
        inputHashes.add(createMessageDigest().digest(blockItem.toByteArray()));
    }

    public void addOutputBlockItem(BlockItem blockItem) {
        outputHashes.add(createMessageDigest().digest(blockItem.toByteArray()));
    }

    public String digest() {
        if (finalized) {
            throw new IllegalStateException("Block root hash is already calculated");
        }

        validateHash(previousHash, PREVIOUSHASH);
        validateHash(startOfBlockStateHash, STARTOFBLOCKSTATEHASH);

        List<byte[]> leaves = new ArrayList<>();
        leaves.add(previousHash);
        leaves.add(getRootHash(inputHashes));
        leaves.add(getRootHash(outputHashes));
        leaves.add(startOfBlockStateHash);

        byte[] rootHash = getRootHash(leaves);
        finalized = true;

        return DomainUtils.bytesToHex(rootHash);
    }

    public void setPreviousHash(byte[] previousHash) {
        validateHash(previousHash, PREVIOUSHASH);
        this.previousHash = previousHash;
    }

    public void setStartOfBlockStateHash(byte[] startOfBlockStateHash) {
        validateHash(startOfBlockStateHash, STARTOFBLOCKSTATEHASH);
        this.startOfBlockStateHash = startOfBlockStateHash;
    }

    @SneakyThrows
    private static MessageDigest createMessageDigest() {
        try {
            return MessageDigest.getInstance(SHA_384.getName());
        } catch (NoSuchAlgorithmException ex) {
            throw new StreamFileReaderException(ex);
        }
    }

    private static byte[] getRootHash(List<byte[]> leaves) {
        if (leaves.isEmpty()) {
            return EMPTY_HASH;
        }

        // Pad leaves with EMPTY_HASH to the next 2^n to form a perfect binary tree
        int size = leaves.size();
        if ((size & (size - 1)) != 0) {
            size = Integer.highestOneBit(size) << 1;
            while (leaves.size() < size) {
                leaves.add(EMPTY_HASH);
            }
        }

        // Iteratively calculate the parent node hash as h(left | right) to get the root hash in bottom-up fashion
        while (size > 1) {
            for (int i = 0; i < size; i += 2) {
                var digest = createMessageDigest();
                byte[] left = leaves.get(i);
                byte[] right = leaves.get(i + 1);
                digest.update(left);
                digest.update(right);
                leaves.set(i >> 1, digest.digest());
            }

            size >>= 1;
        }

        return leaves.getFirst();
    }

    private static void validateHash(byte[] hash, String name) {
        if (Objects.requireNonNull(hash, "Null " + name).length != SHA_384.getSize()) {
            throw new IllegalArgumentException(String.format("%s is not %d bytes", name, SHA_384.getSize()));
        }
    }
}
