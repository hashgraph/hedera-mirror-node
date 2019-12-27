package com.hedera.mirror.importer.parser.record;
/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.base.Splitter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.parser.CommonParserProperties.TransactionFilter;

@ExtendWith(MockitoExtension.class)
public class CommonParserPropertiesTest {

    private final CommonParserProperties commonParserProperties = new CommonParserProperties();

    @DisplayName("Filter empty")
    @Test
    void filterEmpty() {
        Transaction transaction = new Transaction();
        transaction.setEntity(entity("0.0.1"));
        transaction.setType(TransactionTypeEnum.CONSENSUSSUBMITMESSAGE.getProtoId());
        assertTrue(commonParserProperties.getFilter().test(transaction));
    }

    @DisplayName("Filter using include")
    @ParameterizedTest(name = "with entity {0} and type {1} resulting in {2}")
    @CsvSource({
            "0.0.1, CONSENSUSSUBMITMESSAGE, true",
            "0.0.2, CRYPTOCREATEACCOUNT, true",
            "0.0.3, FREEZE, true",
            "0.0.4, FILECREATE, true",
            "0.0.1, CRYPTOCREATEACCOUNT, false",
            "0.0.2, CONSENSUSSUBMITMESSAGE, false",
            "0.0.4, FREEZE, false",
            ", CONSENSUSSUBMITMESSAGE, false",
            "0.0.1, , false",
            ", , false"
    })
    void filterInclude(String entityId, TransactionTypeEnum type, boolean result) {
        Transaction transaction = new Transaction();
        transaction.setEntity(entity(entityId));
        transaction.setType(type != null ? type.getProtoId() : -1); // null simulates unknown transaction type

        commonParserProperties.getInclude().add(filter("0.0.1", TransactionTypeEnum.CONSENSUSSUBMITMESSAGE));
        commonParserProperties.getInclude().add(filter("0.0.2", TransactionTypeEnum.CRYPTOCREATEACCOUNT));
        commonParserProperties.getInclude().add(filter("0.0.3", null));
        commonParserProperties.getInclude().add(filter(null, TransactionTypeEnum.FILECREATE));

        assertEquals(result, commonParserProperties.getFilter().test(transaction));
    }

    @DisplayName("Filter using exclude")
    @ParameterizedTest(name = "with entity {0} and type {1} resulting in {2}")
    @CsvSource({
            "0.0.1, CONSENSUSSUBMITMESSAGE, false",
            "0.0.2, CRYPTOCREATEACCOUNT, false",
            "0.0.3, FREEZE, false",
            "0.0.4, FILECREATE, false",
            "0.0.1, CRYPTOCREATEACCOUNT, true",
            "0.0.2, CONSENSUSSUBMITMESSAGE, true",
            "0.0.4, FREEZE, true",
            ", CONSENSUSSUBMITMESSAGE, true",
            "0.0.1, , true",
            ", , true"
    })
    void filterExclude(String entityId, TransactionTypeEnum type, boolean result) {
        Transaction transaction = new Transaction();
        transaction.setEntity(entity(entityId));
        transaction.setType(type != null ? type.getProtoId() : -1);

        commonParserProperties.getExclude().add(filter("0.0.1", TransactionTypeEnum.CONSENSUSSUBMITMESSAGE));
        commonParserProperties.getExclude().add(filter("0.0.2", TransactionTypeEnum.CRYPTOCREATEACCOUNT));
        commonParserProperties.getExclude().add(filter("0.0.3", null));
        commonParserProperties.getExclude().add(filter(null, TransactionTypeEnum.FILECREATE));

        assertEquals(result, commonParserProperties.getFilter().test(transaction));
    }

    @DisplayName("Filter using include and exclude")
    @ParameterizedTest(name = "with entity {0} and type {1} resulting in {2}")
    @CsvSource({
            "0.0.1, CONSENSUSSUBMITMESSAGE, true",
            "0.0.2, CRYPTOCREATEACCOUNT, true",
            "0.0.3, FREEZE, false",
            "0.0.4, FILECREATE, false",
            "0.0.1, CRYPTOCREATEACCOUNT, false",
            "0.0.2, CONSENSUSSUBMITMESSAGE, false",
            "0.0.4, FREEZE, false",
            "0.0.5, CONSENSUSSUBMITMESSAGE, false",
    })
    void filterBoth(String entityId, TransactionTypeEnum type, boolean result) {
        Transaction transaction = new Transaction();
        transaction.setEntity(entity(entityId));
        transaction.setType(type != null ? type.getProtoId() : -1);

        commonParserProperties.getInclude().add(filter("0.0.1", TransactionTypeEnum.CONSENSUSSUBMITMESSAGE));
        commonParserProperties.getInclude().add(filter("0.0.2", TransactionTypeEnum.CRYPTOCREATEACCOUNT));
        commonParserProperties.getInclude().add(filter("0.0.3", TransactionTypeEnum.FREEZE));
        commonParserProperties.getInclude().add(filter("0.0.4", TransactionTypeEnum.FILECREATE));
        commonParserProperties.getInclude().add(filter("0.0.5", TransactionTypeEnum.CONSENSUSCREATETOPIC));

        commonParserProperties.getExclude().add(filter("0.0.2", TransactionTypeEnum.CRYPTOUPDATEACCOUNT));
        commonParserProperties.getExclude().add(filter("0.0.3", null));
        commonParserProperties.getExclude().add(filter(null, TransactionTypeEnum.FILECREATE));
        commonParserProperties.getExclude().add(filter("0.0.5", TransactionTypeEnum.CONSENSUSCREATETOPIC));

        assertEquals(result, commonParserProperties.getFilter().test(transaction));
    }

    private Entities entity(String entityId) {
        if (StringUtils.isBlank(entityId)) {
            return null;
        }

        List<Long> parts = Splitter.on('.')
                .splitToList(entityId)
                .stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        Entities entities = new Entities();
        entities.setEntityShard(parts.get(0));
        entities.setEntityRealm(parts.get(1));
        entities.setEntityNum(parts.get(2));
        return entities;
    }

    private TransactionFilter filter(String entity, TransactionTypeEnum type) {
        TransactionFilter transactionFilter = new TransactionFilter();

        if (StringUtils.isNotBlank(entity)) {
            transactionFilter.setEntity(Arrays.asList(entity));
        }

        if (type != null) {
            transactionFilter.setTransaction(Arrays.asList(type));
        }

        return transactionFilter;
    }
}
