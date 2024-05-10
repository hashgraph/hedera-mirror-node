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

/**
 * A class used to parse and store an input parameter for transaction ID or hash.
 *
 * @author vyanev
 * @param transactionID this will be populated if the input is a valid transaction ID
 * @param hash this will be populated if the input is a valid transaction hash
 */
public record TransactionIdOrHashParameter(TransactionID transactionID, ByteString hash) {

    /**
     * Parses the input parameter and returns a TransactionIdOrHashParameter object.
     * <li>
     *     If the input is a string representing an ethereum transaction hash,
     *     it will be parsed and stored in the {@code hash} field.
     * </li>
     * <li>
     *     If the input is a string representing a transaction ID,
     *     it will be parsed and stored in the {@code transactionID} field.
     * </li>
     *
     * @param transactionIdOrHash The input string to be parsed
     * @return {@link TransactionIdOrHashParameter} holding the parsed transaction ID or hash
     * @throws IllegalArgumentException if the input string is empty or has an invalid format
     * @see TransactionUtils#isValidEthHash
     * @see TransactionUtils#isValidTransactionId
     */
    @SneakyThrows(NumberFormatException.class)
    public static TransactionIdOrHashParameter valueOf(String transactionIdOrHash) {
        if (!StringUtils.hasText(transactionIdOrHash)) {
            throw new IllegalArgumentException("Transaction ID or hash is required");
        }

        if (TransactionUtils.isValidEthHash(transactionIdOrHash)) {
            final var sanitizedHash = transactionIdOrHash.replace("0x", "");
            return new TransactionIdOrHashParameter(null, ByteString.fromHex(sanitizedHash));
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
