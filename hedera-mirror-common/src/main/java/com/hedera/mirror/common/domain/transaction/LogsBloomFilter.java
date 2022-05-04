package com.hedera.mirror.common.domain.transaction;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import lombok.NoArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import java.util.Collection;
import java.util.List;

/**
 * Utility methods used by Hedera adapted from {org.hyperledger.besu.evm.log.LogsBloomFilter}
 */
@NoArgsConstructor
public class LogsBloomFilter {

    public static final int BYTE_SIZE = 256;
    private static final int LEAST_SIGNIFICANT_BYTE = 0xFF;
    private static final int LEAST_SIGNIFICANT_THREE_BITS = 0x7;
    private static final int BITS_IN_BYTE = 8;
    private final MutableBytes logsBloom = MutableBytes.create(BYTE_SIZE);

    public LogsBloomFilter(Collection<byte[]> bytes) {
        if(bytes != null) {
            bytes.forEach(b -> insertBytes(Bytes.wrap(b), logsBloom));
        }
    }

    public void insertBytes(byte[] bytes) {
        if(bytes != null) {
            insertBytes(Bytes.wrap(bytes), logsBloom);
        }
    }

    public byte[] getBloom() {
        return logsBloom.isZero() ? new byte[0] : logsBloom.toArray();
    }

    public boolean couldContain(byte[] bloom) {
        if (bloom == null) {
            return true;
        }

        byte[] logsBloom = getBloom();
        LogsBloomFilter subSetFilter = new LogsBloomFilter(List.of(bloom));
        byte[] subsetBytes = subSetFilter.getBloom();
        if (subsetBytes.length != logsBloom.length) {
            return false;
        }

        for (int i = 0; i < logsBloom.length; i++) {
            final byte subsetValue = subsetBytes[i];
            if ((logsBloom[i] & subsetValue) != subsetValue) {
                return false;
            }
        }
        return true;
    }

    public void clear() {
        logsBloom.clear();
    }

    private void insertBytes(final Bytes contractResultBloom, final MutableBytes recordFileBloom) {
        setBits(keccak256(contractResultBloom), recordFileBloom);
    }

    private void setBits(final Bytes hashValue, final MutableBytes recordFileBloom) {
        for (int counter = 0; counter < 6; counter += 2) {
            final var setBloomBit =
                    ((hashValue.get(counter) & LEAST_SIGNIFICANT_THREE_BITS) << BITS_IN_BYTE)
                            + (hashValue.get(counter + 1) & LEAST_SIGNIFICANT_BYTE);
            setBit(setBloomBit, recordFileBloom);
        }
    }

    private void setBit(final int index, final MutableBytes recordFileBloom) {
        final var byteIndex = BYTE_SIZE - 1 - index / 8;
        final var bitIndex = index % 8;
        recordFileBloom.set(byteIndex, (byte) (recordFileBloom.get(byteIndex) | (1 << bitIndex)));
    }

    private Bytes32 keccak256(final Bytes input) {
        return Bytes32.wrap(keccak256DigestOf(input.toArray()));
    }

    private byte[] keccak256DigestOf(final byte[] msg) {
        return new Keccak.Digest256().digest(msg);
    }
}
