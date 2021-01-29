package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.codec.binary.Hex;

import com.hedera.mirror.importer.domain.EntityTypeEnum;

class UnknownDataTransactionHandlerTest extends AbstractTransactionHandlerTest {
    // TransactionBody containing an unknown field with a field id = 9999
    private static final String TRANSACTION_BODY_BYTES_HEX =
            "0a120a0c08eb88d6ee0510e8eff7ab01120218021202180318c280de1922020878321043727970746f2074657374206d656d6ffaf004050a03666f6f";

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new UnknownDataTransactionHandler();
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        try {
            return TransactionBody.parseFrom(Hex.decodeHex(TRANSACTION_BODY_BYTES_HEX)).toBuilder();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected EntityTypeEnum getExpectedEntityIdType() {
        return null;
    }
}
