package com.hedera.mirror.web3.evm.properties;

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

import java.time.Instant;
import java.util.Optional;
import lombok.Value;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;

/**
 * Mirror-node adapted {@link BlockValues}
 */
@Value
public class HederaBlockValues implements BlockValues {

    protected final long gasLimit;
    protected final long blockNo;
    protected final Instant consTimestamp;

    @Override
    public long getGasLimit() {
        return gasLimit;
    }

    @Override
    public long getTimestamp() {
        return consTimestamp.getEpochSecond();
    }

    @Override
    public Optional<Wei> getBaseFee() {
        return Optional.of(Wei.ZERO);
    }

    @Override
    public Bytes getDifficultyBytes() {
        return UInt256.ZERO;
    }

    @Override
    public long getNumber() {
        return blockNo;
    }
}
