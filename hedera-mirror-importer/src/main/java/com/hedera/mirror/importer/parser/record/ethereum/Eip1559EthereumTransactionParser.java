/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.ethereum;

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.importer.exception.InvalidEthereumBytesException;
import jakarta.inject.Named;

@Named
public class Eip1559EthereumTransactionParser implements EthereumTransactionParser {
    public static final int EIP1559_TYPE_BYTE = 2;
    private static final String TRANSACTION_TYPE_NAME = "EIP1559";
    private static final int EIP1559_TYPE_RLP_ITEM_COUNT = 12;

    @Override
    public EthereumTransaction decode(byte[] transactionBytes) {
        var decoder = RLPDecoder.RLP_STRICT.sequenceIterator(transactionBytes);
        var type = decoder.next().asByte();
        if (type != EIP1559_TYPE_BYTE) {
            throw new InvalidEthereumBytesException(
                    TRANSACTION_TYPE_NAME,
                    String.format("First byte was %s but should be %s", type, EIP1559_TYPE_BYTE));
        }

        var eip1559RlpItem = decoder.next();
        if (!eip1559RlpItem.isList()) {
            throw new InvalidEthereumBytesException(TRANSACTION_TYPE_NAME, "Second RLPItem was not a list");
        }

        var rlpItems = eip1559RlpItem.asRLPList().elements();
        if (rlpItems.size() != EIP1559_TYPE_RLP_ITEM_COUNT) {
            throw new InvalidEthereumBytesException(
                    TRANSACTION_TYPE_NAME,
                    String.format(
                            "RLP list size was %d but expected %d", rlpItems.size(), EIP1559_TYPE_RLP_ITEM_COUNT));
        }

        var ethereumTransaction = EthereumTransaction.builder()
                .chainId(rlpItems.get(0).data())
                .nonce(rlpItems.get(1).asLong())
                .maxPriorityFeePerGas(rlpItems.get(2).data())
                .maxFeePerGas(rlpItems.get(3).data())
                .gasLimit(rlpItems.get(4).asLong())
                .toAddress(rlpItems.get(5).data())
                .value(rlpItems.get(6).asBigInt().toByteArray())
                .callData(rlpItems.get(7).data())
                .accessList(rlpItems.get(8).data())
                .recoveryId((int) rlpItems.get(9).asByte())
                .signatureR(rlpItems.get(10).data())
                .signatureS(rlpItems.get(11).data())
                .type(EIP1559_TYPE_BYTE);

        return ethereumTransaction.build();
    }
}
