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
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.context.annotation.Primary;

@Named
@Primary
@RequiredArgsConstructor
public class CompositeEthereumTransactionParser implements EthereumTransactionParser {
    private final LegacyEthereumTransactionParser legacyEthereumTransactionParser;
    private final Eip1559EthereumTransactionParser eip1559EthereumTransactionParser;

    @Override
    public EthereumTransaction decode(byte[] transactionBytes) {
        var ethereumTransactionParser = getEthereumTransactionParser(transactionBytes);
        return ethereumTransactionParser.decode(transactionBytes);
    }

    private EthereumTransactionParser getEthereumTransactionParser(byte[] transactionBytes) {
        if (ArrayUtils.isEmpty(transactionBytes) || transactionBytes.length < 2) {
            throw new InvalidDatasetException("Ethereum transaction bytes length is less than 2 bytes in length");
        }

        var decoder = RLPDecoder.RLP_STRICT.sequenceIterator(transactionBytes);
        var legacyRlpItem = decoder.next();
        return legacyRlpItem.isList() ? legacyEthereumTransactionParser : eip1559EthereumTransactionParser;
    }
}
