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
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Primary;

import com.hedera.mirror.common.domain.transaction.EthereumTransaction;

@Log4j2
@Named
@Primary
@RequiredArgsConstructor
public class CompositeEthereumTransactionParser implements EthereumTransactionParser {
    private final LegacyEthereumTransactionParser legacyEthereumTransactionParser;
    private final Eip1559EthereumTransactionParser eip1559EthereumTransactionParser;

    @Override
    public EthereumTransaction parse(EthereumTransactionBody body) {
        var ethereumTransactionParser = getEthereumTransactionParser(body.getEthereumData().toByteArray());
        return ethereumTransactionParser.parse(body);
    }

    private EthereumTransactionParser getEthereumTransactionParser(byte[] data) {
        var decoder = RLPDecoder.RLP_STRICT.sequenceIterator(data);
        var rlpItem = decoder.next();
        return rlpItem.isList() ? legacyEthereumTransactionParser : eip1559EthereumTransactionParser;
    }
}
