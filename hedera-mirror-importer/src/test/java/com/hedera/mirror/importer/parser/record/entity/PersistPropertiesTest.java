package com.hedera.mirror.importer.parser.record.entity;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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


import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.common.domain.transaction.TransactionType;

class PersistPropertiesTest {

    @ParameterizedTest
    @CsvSource(textBlock = """
            true, CRYPTOTRANSFER, true,
            true, CONSENSUSSUBMITMESSAGE, false,
            false, CRYPTOTRANSFER, false,
            false, CONSENSUSSUBMITMESSAGE, false,
            """)
    void shouldPersistTransactionHash(boolean transactionHash, TransactionType transactionType, boolean expected) {
        var persistProperties = new EntityProperties.PersistProperties();
        persistProperties.setTransactionHash(transactionHash);
        persistProperties.setTransactionHashTypes(Set.of(transactionType));
        assertThat(persistProperties.shouldPersistTransactionHash(TransactionType.CRYPTOTRANSFER)).isEqualTo(expected);
        assertThat(persistProperties.shouldPersistTransactionHash(TransactionType.CONTRACTCALL)).isFalse();
    }
}
