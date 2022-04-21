package com.hedera.mirror.importer.parser.record.ethereum;

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

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import javax.inject.Named;

import com.hedera.mirror.common.domain.transaction.EthereumTransaction;

@Named
public class Eip1559EthereumTransactionParser implements EthereumTransactionParser {
    @Override
    public EthereumTransaction parse(byte[] transactionBytes) {
        var decoder = RLPDecoder.RLP_STRICT.sequenceIterator(
                transactionBytes);
        var legacyRlpItem = decoder.next();
        var legacyTypeByte = legacyRlpItem.asByte();
        if (legacyTypeByte != 2) {
            return null;
        }

        var eip1559RlpItem = decoder.next();
        if (!eip1559RlpItem.isList()) {
            return null;
        }

        var rlpItems = eip1559RlpItem.asRLPList().elements();
        if (rlpItems.size() != 12) {
            return null;
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
                .type(2);

        return ethereumTransaction.build();
    }
}
