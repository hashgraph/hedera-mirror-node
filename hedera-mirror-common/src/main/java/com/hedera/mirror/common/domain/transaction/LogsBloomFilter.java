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

    public LogsBloomFilter insertBytes(final byte[] bytes) {
        if(bytes != null) {
            insertBytes(Bytes.wrap(bytes));
        }
        return this;
    }

    public LogsBloomFilter insertBytes(final Collection<byte[]> bytes) {
        if (bytes != null) {
            bytes.forEach(this::insertBytes);
        }
        return this;
    }

    public byte[] getBloom() {
        return logsBloom.isZero() ? new byte[0] : logsBloom.toArray();
    }

    /**
     * If true the filter could contain the bloom. If false the filter definitely does not contain the bloom.
     *
     * @param bloom that may be contained within this filter.
     * @returns {boolean} if the bloom filter may contain the bloom.
     */
    public boolean couldContain(final byte[] bloom) {
        if (bloom == null) {
            return true;
        }

        final var thisBloomBytes = getBloom();
        final var subsetBytes = new LogsBloomFilter().insertBytes(bloom).getBloom();
        if (subsetBytes.length != thisBloomBytes.length) {
            return false;
        }

        for (int i = 0; i < thisBloomBytes.length; i++) {
            final byte subsetValue = subsetBytes[i];
            if ((thisBloomBytes[i] & subsetValue) != subsetValue) {
                return false;
            }
        }
        return true;
    }

    public void clear() {
        logsBloom.clear();
    }

    private void insertBytes(final Bytes contractResultBloom) {
        setBits(keccak256(contractResultBloom));
    }

    private void setBits(final Bytes hashValue) {
        for (int counter = 0; counter < 6; counter += 2) {
            final var setBloomBit =
                    ((hashValue.get(counter) & LEAST_SIGNIFICANT_THREE_BITS) << BITS_IN_BYTE)
                            + (hashValue.get(counter + 1) & LEAST_SIGNIFICANT_BYTE);
            setBit(setBloomBit);
        }
    }

    private void setBit(final int index) {
        final var byteIndex = BYTE_SIZE - 1 - index / 8;
        final var bitIndex = index % 8;
        // "& 0xff" to prevent bit promotion: https://jira.sonarsource.com/browse/RSPEC-3034
        final byte setBit = (byte) (logsBloom.get(byteIndex) & 0xff | (1 << bitIndex));
        logsBloom.set(byteIndex, setBit);
    }

    private Bytes32 keccak256(final Bytes input) {
        return Bytes32.wrap(keccak256DigestOf(input.toArray()));
    }

    private byte[] keccak256DigestOf(final byte[] msg) {
        return new Keccak.Digest256().digest(msg);
    }
}
