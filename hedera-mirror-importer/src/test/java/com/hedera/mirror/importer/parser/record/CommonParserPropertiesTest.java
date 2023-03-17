package com.hedera.mirror.importer.parser.record;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.domain.TransactionFilterFields;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.parser.CommonParserProperties.TransactionFilter;

@ExtendWith(MockitoExtension.class)
class CommonParserPropertiesTest {

    private final CommonParserProperties commonParserProperties = new CommonParserProperties();

    @DisplayName("Filter empty")
    @Test
    void filterEmpty() {
        assertFalse(commonParserProperties.hasFilter());
        assertTrue(commonParserProperties.getFilter().test(
                new TransactionFilterFields(entities("0.0.1"), TransactionType.CONSENSUSSUBMITMESSAGE)));
        // also test empty filter against a collection of entity ids
        assertTrue(commonParserProperties.getFilter().test(
                new TransactionFilterFields(entities("0.0.1/0.0.2/0.0.3"), TransactionType.CRYPTOCREATEACCOUNT)));
        // explicitly test TransactionsFilterFields.EMPTY
        assertTrue(commonParserProperties.getFilter().test(TransactionFilterFields.EMPTY));
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
            "0.0.1, UNKNOWN, false",
            ", UNKNOWN, false",
            "0.0.1, CRYPTOCREATEACCOUNT, false",
            "0.0.1/0.0.4/0.0.5, CRYPTOCREATEACCOUNT, false",
            "0.0.2/0.0.4/0.0.5, CONSENSUSSUBMITMESSAGE, false",
            "0.0.1/0.0.2/0.0.3, CONSENSUSSUBMITMESSAGE, true",
            "0.0.1/0.0.2/0.0.3, CRYPTOCREATEACCOUNT, true",
            "0.0.3/0.0.4, FILEDELETE, true"
    })
    void filterInclude(String entityId, TransactionType type, boolean result) {
        commonParserProperties.getInclude().add(filter("0.0.1", TransactionType.CONSENSUSSUBMITMESSAGE));
        commonParserProperties.getInclude().add(filter("0.0.2", TransactionType.CRYPTOCREATEACCOUNT));
        commonParserProperties.getInclude().add(filter("0.0.3", null));
        commonParserProperties.getInclude().add(filter(null, TransactionType.FILECREATE));
        assertTrue(commonParserProperties.hasFilter());

        assertEquals(result, commonParserProperties.getFilter().test(
                new TransactionFilterFields(entities(entityId), type)));
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
            "0.0.1, UNKNOWN, true",
            ", UNKNOWN, true",
            "0.0.1/0.0.2/0.0.3, CONSENSUSSUBMITMESSAGE, false",
            "0.0.1/0.0.2/0.0.3, CRYPTOCREATEACCOUNT, false",
            "0.0.1/0.0.2/0.0.3, FREEZE, false",
            "0.0.1/0.0.2/0.0.3, FILECREATE, false",
            "0.0.1/0.0.4/0.0.5, CRYPTOCREATEACCOUNT, true",
            "0.0.2/0.0.4/0.0.5, CONSENSUSSUBMITMESSAGE, true",
            "0.0.1/0.0.4/0.0.5, FREEZE, true",
            "0.0.1/0.0.2/0.0.4, FILEDELETE, true"
    })
    void filterExclude(String entityId, TransactionType type, boolean result) {
        commonParserProperties.getExclude().add(filter("0.0.1", TransactionType.CONSENSUSSUBMITMESSAGE));
        commonParserProperties.getExclude().add(filter("0.0.2", TransactionType.CRYPTOCREATEACCOUNT));
        commonParserProperties.getExclude().add(filter("0.0.3", null));
        commonParserProperties.getExclude().add(filter(null, TransactionType.FILECREATE));

        assertEquals(result, commonParserProperties.getFilter().test(
                new TransactionFilterFields(entities(entityId), type)));
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
            "0.0.1/0.0.2, CONSENSUSSUBMITMESSAGE, true",
            "0.0.2/0.0.4, CRYPTOCREATEACCOUNT, true",
            "0.0.1/0.0.2/0.0.3, CONSENSUSSUBMITMESSAGE, false",
            "0.0.2/0.0.3/0.0.4, CRYPTOCREATEACCOUNT, false",
            "0.0.1/0.0.3, FREEZE, false",
            "0.0.1/0.0.2, FILECREATE, false",
            "0.0.1/0.0.4/0.0.5, CRYPTOCREATEACCOUNT, false",
            "0.0.2/0.0.4/0.0.5, CONSENSUSSUBMITMESSAGE, false",
            "0.0.1/0.0.2/0.0.4, FREEZE, false",
            "0.0.2/0.0.4/0.0.5, CONSENSUSSUBMITMESSAGE, false",
    })
    void filterBoth(String entityId, TransactionType type, boolean result) {
        commonParserProperties.getInclude().add(filter("0.0.1", TransactionType.CONSENSUSSUBMITMESSAGE));
        commonParserProperties.getInclude().add(filter("0.0.2", TransactionType.CRYPTOCREATEACCOUNT));
        commonParserProperties.getInclude().add(filter("0.0.3", TransactionType.FREEZE));
        commonParserProperties.getInclude().add(filter("0.0.4", TransactionType.FILECREATE));
        commonParserProperties.getInclude().add(filter("0.0.5", TransactionType.CONSENSUSCREATETOPIC));

        commonParserProperties.getExclude().add(filter("0.0.2", TransactionType.CRYPTOUPDATEACCOUNT));
        commonParserProperties.getExclude().add(filter("0.0.3", null));
        commonParserProperties.getExclude().add(filter(null, TransactionType.FILECREATE));
        commonParserProperties.getExclude().add(filter("0.0.5", TransactionType.CONSENSUSCREATETOPIC));

        assertEquals(result, commonParserProperties.getFilter().test(
                new TransactionFilterFields(entities(entityId), type)));
    }

    private Collection<EntityId> entities(String entityId) {
        if (StringUtils.isBlank(entityId)) {
            return null;
        }

        if (entityId.indexOf("/") > -1) {
            String[] entityIds = entityId.split("/");
            EntityId[] createdEntities = new EntityId[entityIds.length];
            for (int i = 0; i < entityIds.length; i++) {
                createdEntities[i] = EntityId.of(entityIds[i], EntityType.ACCOUNT);
            }
            return Arrays.asList(createdEntities);
        }

        return Collections.singleton(EntityId.of(entityId, EntityType.ACCOUNT));
    }

    private TransactionFilter filter(String entity, TransactionType type) {
        TransactionFilter transactionFilter = new TransactionFilter();

        if (StringUtils.isNotBlank(entity)) {
            transactionFilter.setEntity(Arrays.asList(EntityId.of(entity, EntityType.ACCOUNT)));
        }

        if (type != null) {
            transactionFilter.setTransaction(Arrays.asList(type));
        }

        return transactionFilter;
    }
}
