/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
public class Eip2930EthereumTransactionParser implements EthereumTransactionParser {
    public static final int EIP2930_TYPE_BYTE = 1;
    private static final String TRANSACTION_TYPE_NAME = "EIP2930";
    private static final int EIP2930_TYPE_RLP_ITEM_COUNT = 11;

    @Override
    public EthereumTransaction decode(byte[] transactionBytes) {
        var decoder = RLPDecoder.RLP_STRICT.sequenceIterator(transactionBytes);
        var type = decoder.next().asByte();
        if (type != EIP2930_TYPE_BYTE) {
            throw new InvalidEthereumBytesException(
                    TRANSACTION_TYPE_NAME,
                    String.format("First byte was %s but should be %s", type, EIP2930_TYPE_BYTE));
        }

        var eip2930RlpItem = decoder.next();
        if (!eip2930RlpItem.isList()) {
            throw new InvalidEthereumBytesException(TRANSACTION_TYPE_NAME, "Second RLPItem was not a list");
        }

        var rlpItems = eip2930RlpItem.asRLPList().elements();
        if (rlpItems.size() != EIP2930_TYPE_RLP_ITEM_COUNT) {
            throw new InvalidEthereumBytesException(
                    TRANSACTION_TYPE_NAME,
                    String.format(
                            "RLP list size was %d but expected %d", rlpItems.size(), EIP2930_TYPE_RLP_ITEM_COUNT));
        }

        var ethereumTransaction = EthereumTransaction.builder()
                .chainId(rlpItems.get(0).data())
                .nonce(rlpItems.get(1).asLong())
                .gasPrice(rlpItems.get(2).asBytes())
                .gasLimit(rlpItems.get(3).asLong())
                .toAddress(rlpItems.get(4).data())
                .value(rlpItems.get(5).asBigInt().toByteArray())
                .callData(rlpItems.get(6).data())
                .accessList(rlpItems.get(7).data())
                .recoveryId((int) rlpItems.get(8).asByte())
                .signatureR(rlpItems.get(9).data())
                .signatureS(rlpItems.get(10).data())
                .type(EIP2930_TYPE_BYTE);

        return ethereumTransaction.build();
    }
}
