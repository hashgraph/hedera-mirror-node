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
import java.math.BigInteger;

@Named
public class LegacyEthereumTransactionParser implements EthereumTransactionParser {
    private static final int LEGACY_TYPE_BYTE = 0;
    private static final int LEGACY_TYPE_RLP_ITEM_COUNT = 9;
    private static final String TRANSACTION_TYPE_NAME = "Legacy";

    @Override
    public EthereumTransaction decode(byte[] transactionBytes) {
        var decoder = RLPDecoder.RLP_STRICT.sequenceIterator(transactionBytes);
        var legacyRlpItem = decoder.next();
        var rlpItems = legacyRlpItem.asRLPList().elements();
        if (rlpItems.size() != LEGACY_TYPE_RLP_ITEM_COUNT) {
            throw new InvalidEthereumBytesException(
                    TRANSACTION_TYPE_NAME,
                    String.format(
                            "RLPItem list size was %s " + "but should be %s",
                            rlpItems.size(), LEGACY_TYPE_RLP_ITEM_COUNT));
        }

        var ethereumTransaction = EthereumTransaction.builder()
                .nonce(rlpItems.get(0).asLong())
                .gasPrice(rlpItems.get(1).asBytes())
                .gasLimit(rlpItems.get(2).asLong())
                .toAddress(rlpItems.get(3).data())
                .value(rlpItems.get(4).asBigInt().toByteArray())
                .callData(rlpItems.get(5).data())
                .type(LEGACY_TYPE_BYTE);

        var v = rlpItems.get(6).asBytes();
        BigInteger vBi = new BigInteger(1, v);
        ethereumTransaction
                .signatureV(v)
                .signatureR(rlpItems.get(7).data())
                .signatureS(rlpItems.get(8).data())
                .recoveryId(vBi.testBit(0) ? 0 : 1);

        if (vBi.compareTo(BigInteger.valueOf(34)) > 0) {
            ethereumTransaction.chainId(
                    vBi.subtract(BigInteger.valueOf(35)).shiftRight(1).toByteArray());
        }

        return ethereumTransaction.build();
    }
}
