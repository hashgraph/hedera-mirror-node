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

import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.context.annotation.Primary;

import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

@Named
@Primary
@RequiredArgsConstructor
public class CompositeEthereumTransactionParser implements EthereumTransactionParser {
    private static final byte[] EIP1559_BYTES_PREFIX = new byte[] {2, -8};
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

        var eip1559StartingBytesMatch = transactionBytes[0] == EIP1559_BYTES_PREFIX[0] &&
                transactionBytes[1] == EIP1559_BYTES_PREFIX[1];
        return eip1559StartingBytesMatch ? eip1559EthereumTransactionParser : legacyEthereumTransactionParser;
    }
}
