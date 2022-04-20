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
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import java.math.BigInteger;
import javax.inject.Named;

import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.util.DomainUtils;

@Named
public class LegacyEthereumTransactionParser implements EthereumTransactionParser {
    @Override
    public EthereumTransaction parse(EthereumTransactionBody body) {
        var decoder = RLPDecoder.RLP_STRICT.sequenceIterator(
                DomainUtils.toBytes(body.getEthereumData()));
        var legacyRlpItem = decoder.next();
        var rlpItems = legacyRlpItem.asRLPList().elements();
        if (rlpItems.size() != 9) {
            return null;
        }

        var ethereumTransaction = EthereumTransaction.builder()
                .nonce(rlpItems.get(0).asLong())
                .gasPrice(rlpItems.get(1).asBytes())
                .gasLimit(rlpItems.get(2).asLong())
                .toAddress(rlpItems.get(3).data())
                .value(rlpItems.get(4).asBigInt().toByteArray())
                .callData(rlpItems.get(5).data())
                .type(1)
                .maxGasAllowance(body.getMaxGasAllowance());

        var v = rlpItems.get(6).asBytes();
        BigInteger vBi = new BigInteger(1, v);
        ethereumTransaction
                .signatureV(v)
                .signatureR(rlpItems.get(7).data())
                .signatureS(rlpItems.get(8).data())
                .recoveryId(vBi.testBit(0) ? 0 : 1);

        if (vBi.compareTo(BigInteger.valueOf(34)) > 0) {
            ethereumTransaction.chainId(vBi.subtract(BigInteger.valueOf(35)).shiftRight(1).toByteArray());
        }

        return ethereumTransaction.build();
    }
}
