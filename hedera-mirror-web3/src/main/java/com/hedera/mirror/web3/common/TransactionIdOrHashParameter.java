/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.common;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.evm.utils.TransactionUtils;
import com.hederahashgraph.api.proto.java.TransactionID;
import lombok.SneakyThrows;
import org.springframework.util.StringUtils;

public record TransactionIdOrHashParameter(TransactionID transactionID, ByteString hash) {

    @SneakyThrows(NumberFormatException.class)
    public static TransactionIdOrHashParameter valueOf(String transactionIdOrHash) {
        if (!StringUtils.hasText(transactionIdOrHash)) {
            throw new IllegalArgumentException("Transaction ID or hash is required");
        }

        if (TransactionUtils.isValidEthHash(transactionIdOrHash)) {
            return new TransactionIdOrHashParameter(null, ByteString.fromHex(transactionIdOrHash));
        } else if (TransactionUtils.isValidTransactionId(transactionIdOrHash)) {
            return new TransactionIdOrHashParameter(TransactionUtils.parseTransactionId(transactionIdOrHash), null);
        } else {
            throw new IllegalArgumentException("Invalid transaction ID or hash: %s".formatted(transactionIdOrHash));
        }
    }

    public boolean isTransactionId() {
        return transactionID() != null;
    }

    public boolean isHash() {
        return hash() != null;
    }
}
