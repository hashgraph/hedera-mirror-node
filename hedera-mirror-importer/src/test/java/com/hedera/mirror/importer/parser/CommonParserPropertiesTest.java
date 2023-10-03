/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.TransactionFilterFields;
import com.hedera.mirror.importer.exception.InvalidConfigurationException;
import com.hedera.mirror.importer.parser.CommonParserProperties.TransactionFilter;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ParseException;

@ExtendWith(MockitoExtension.class)
class CommonParserPropertiesTest {

    private static final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();
    private final CommonParserProperties commonParserProperties = new CommonParserProperties();

    private static Stream<Arguments> filterRecordItemStreamIncludeOrExclude() {
        return Stream.of(
                Arguments.of("0.0.1", recordItemBuilder.consensusSubmitMessage().build(), true),
                Arguments.of("0.0.2", recordItemBuilder.cryptoCreate().build(), true),
                Arguments.of("0.0.3", recordItemBuilder.freeze().build(), true),
                Arguments.of("0.0.4", recordItemBuilder.fileCreate().build(), true),
                Arguments.of("0.0.1", recordItemBuilder.cryptoCreate().build(), false),
                Arguments.of("0.0.2", recordItemBuilder.consensusSubmitMessage().build(), false),
                Arguments.of("0.0.4", recordItemBuilder.freeze().build(), false),
                Arguments.of(null, recordItemBuilder.consensusSubmitMessage().build(), false),
                Arguments.of("0.0.1", recordItemBuilder.unknown().build(), false),
                Arguments.of(null, recordItemBuilder.unknown().build(), false),
                Arguments.of("0.0.1", recordItemBuilder.cryptoCreate().build(), false),
                Arguments.of(
                        "0.0.1/0.0.4/0.0.5", recordItemBuilder.cryptoCreate().build(), false),
                Arguments.of(
                        "0.0.2/0.0.4/0.0.5",
                        recordItemBuilder.consensusSubmitMessage().build(),
                        false),
                Arguments.of(
                        "0.0.1/0.0.2/0.0.3",
                        recordItemBuilder.consensusSubmitMessage().build(),
                        true),
                Arguments.of(
                        "0.0.1/0.0.2/0.0.3", recordItemBuilder.cryptoCreate().build(), true),
                Arguments.of("0.0.3/0.0.4", recordItemBuilder.fileDelete().build(), true));
    }

    private static Stream<Arguments> filterRecordItemStreamIncludeAndExclude() {
        return Stream.of(
                Arguments.of("0.0.1", recordItemBuilder.consensusSubmitMessage().build(), true),
                Arguments.of("0.0.2", recordItemBuilder.cryptoCreate().build(), true),
                Arguments.of("0.0.3", recordItemBuilder.freeze().build(), false),
                Arguments.of("0.0.4", recordItemBuilder.fileCreate().build(), false),
                Arguments.of("0.0.1", recordItemBuilder.cryptoCreate().build(), false),
                Arguments.of("0.0.2", recordItemBuilder.consensusSubmitMessage().build(), false),
                Arguments.of("0.0.4", recordItemBuilder.freeze().build(), false),
                Arguments.of("0.0.5", recordItemBuilder.consensusSubmitMessage().build(), false),
                Arguments.of(
                        "0.0.1/0.0.2",
                        recordItemBuilder.consensusSubmitMessage().build(),
                        true),
                Arguments.of("0.0.2/0.0.4", recordItemBuilder.cryptoCreate().build(), true),
                Arguments.of(
                        "0.0.1/0.0.2/0.0.3",
                        recordItemBuilder.consensusSubmitMessage().build(),
                        false),
                Arguments.of(
                        "0.0.2/0.0.3/0.0.4", recordItemBuilder.cryptoCreate().build(), false),
                Arguments.of("0.0.1/0.0.3", recordItemBuilder.freeze().build(), false),
                Arguments.of("0.0.1/0.0.2", recordItemBuilder.fileCreate().build(), false),
                Arguments.of(
                        "0.0.1/0.0.4/0.0.5", recordItemBuilder.cryptoCreate().build(), false),
                Arguments.of(
                        "0.0.2/0.0.4/0.0.5",
                        recordItemBuilder.consensusSubmitMessage().build(),
                        false),
                Arguments.of("0.0.1/0.0.2/0.0.4", recordItemBuilder.freeze().build(), false),
                Arguments.of(
                        "0.0.2/0.0.4/0.0.5",
                        recordItemBuilder.consensusSubmitMessage().build(),
                        false));
    }

    @DisplayName("Filter empty")
    @Test
    void filterEmpty() {
        assertThat(commonParserProperties.hasFilter()).isFalse();
        assertThat(commonParserProperties
                        .getFilter()
                        .test(new TransactionFilterFields(
                                entities("0.0.1"),
                                recordItemBuilder.consensusSubmitMessage().build())))
                .isTrue();
        // also test empty filter against a collection of entity ids
        assertThat(commonParserProperties
                        .getFilter()
                        .test(new TransactionFilterFields(
                                entities("0.0.1/0.0.2/0.0.3"),
                                recordItemBuilder.cryptoCreate().build())))
                .isTrue();
        // explicitly test TransactionsFilterFields.EMPTY
        assertThat(commonParserProperties.getFilter().test(TransactionFilterFields.EMPTY))
                .isTrue();
    }

    @DisplayName("Filter using include")
    @ParameterizedTest(name = "with entity {0} and recordItem {1} resulting in {2}")
    @MethodSource("filterRecordItemStreamIncludeOrExclude")
    void filterInclude(String entityId, RecordItem recordItem, boolean result) {
        commonParserProperties.getInclude().add(filter("0.0.1", null, TransactionType.CONSENSUSSUBMITMESSAGE));
        commonParserProperties.getInclude().add(filter("0.0.2", null, TransactionType.CRYPTOCREATEACCOUNT));
        commonParserProperties.getInclude().add(filter("0.0.3", null, null));
        commonParserProperties.getInclude().add(filter(null, null, TransactionType.FILECREATE));
        assertThat(commonParserProperties.hasFilter()).isTrue();

        assertThat(commonParserProperties.getFilter().test(new TransactionFilterFields(entities(entityId), recordItem)))
                .isEqualTo(result);
    }

    //    @DisplayName("Filter expression using include")
    //    @ParameterizedTest(name = "with expression {0} resulting in {1}")
    //    @CsvSource({
    //            "'transactionBody.transactionID.accountID.accountNum == 800', false",
    //            "'transactionBody.cryptoTransfer.transfers.accountAmountsList.size >= 2', true",
    //            "'', true",
    //            "'transactionBody.transactionID.accountID.accountNum > 0', true",
    //            "'transactionRecord.consensusTimestamp.seconds > 0', true",
    //            "'transactionBody.transactionFee > 50', true",
    //    })
    //    void filterExpressionInclude(String expression, boolean result) {
    //        var recordItem = recordItemBuilder.cryptoTransfer().build();
    //
    //        commonParserProperties.getInclude().add(filter(expression));
    //        assertTrue(commonParserProperties.hasFilter());
    //
    //        assertEquals(
    //                result, commonParserProperties.getFilter().test(new TransactionFilterFields(null, recordItem,
    // null)));
    //    }

    @DisplayName("Filter using exclude")
    @ParameterizedTest(name = "with entity {0} and recordItem {1} resulting in !{2}")
    @MethodSource("filterRecordItemStreamIncludeOrExclude")
    void filterExclude(String entityId, RecordItem recordItem, boolean notResult) {
        commonParserProperties.getExclude().add(filter("0.0.1", null, TransactionType.CONSENSUSSUBMITMESSAGE));
        commonParserProperties.getExclude().add(filter("0.0.2", null, TransactionType.CRYPTOCREATEACCOUNT));
        commonParserProperties.getExclude().add(filter("0.0.3", null, null));
        commonParserProperties.getExclude().add(filter(null, null, TransactionType.FILECREATE));

        assertThat(commonParserProperties.getFilter().test(new TransactionFilterFields(entities(entityId), recordItem)))
                .isNotEqualTo(notResult);
    }

    @DisplayName("Filter using include and exclude")
    @ParameterizedTest(name = "with entity {0} and recordItem {1} resulting in {2}")
    @MethodSource("filterRecordItemStreamIncludeAndExclude")
    void filterBoth(String entityId, RecordItem recordItem, boolean result) {
        commonParserProperties.getInclude().add(filter("0.0.1", null, TransactionType.CONSENSUSSUBMITMESSAGE));
        commonParserProperties.getInclude().add(filter("0.0.2", null, TransactionType.CRYPTOCREATEACCOUNT));
        commonParserProperties.getInclude().add(filter("0.0.3", null, TransactionType.FREEZE));
        commonParserProperties.getInclude().add(filter("0.0.4", null, TransactionType.FILECREATE));
        commonParserProperties.getInclude().add(filter("0.0.5", null, TransactionType.CONSENSUSCREATETOPIC));

        commonParserProperties.getExclude().add(filter("0.0.2", null, TransactionType.CRYPTOUPDATEACCOUNT));
        commonParserProperties.getExclude().add(filter("0.0.3", null, null));
        commonParserProperties.getExclude().add(filter(null, null, TransactionType.FILECREATE));
        commonParserProperties.getExclude().add(filter("0.0.5", null, TransactionType.CONSENSUSCREATETOPIC));

        assertThat(commonParserProperties.getFilter().test(new TransactionFilterFields(entities(entityId), recordItem)))
                .isEqualTo(result);
    }

    @DisplayName("Invalid filter expression parse exception handling")
    @ParameterizedTest(name = "with expression {0}")
    @CsvSource({"sld&#$$", "transactionBody|consensusTimeStamp ge 32"})
    void filterExpressionParseErrors(String expression) {
        assertThatThrownBy(() -> filter(null, expression, null))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasCauseInstanceOf(ParseException.class);
    }

    @DisplayName("Invalid filter expression evaluation exception handling")
    @ParameterizedTest(name = "with expression {0} resulting in cause {1}")
    @CsvSource({
        // Disallowed root RecordItem access
        "transactionIndex > 12",
        // Disallowed nested RecordItem access
        "'transaction.bodyBytes.size > 24'",
        "'transactionRecord.noSuchProperty != null'",
        "'transactionBody.memo.badStringMethod()'",
        // Does not evaluate to a boolean
        "'transactionBody.cryptoApproveAllowance.cryptoAllowances'"
    })
    void filterExpressionErrors(String expression) {
        var recordItem = recordItemBuilder.cryptoTransfer().build();

        commonParserProperties.getInclude().add(filter(null, expression, null));
        assertThat(commonParserProperties.hasFilter()).isTrue();

        var transactionFilterFields = new TransactionFilterFields(null, recordItem);
        var filter = commonParserProperties.getFilter();
        assertThatThrownBy(() -> filter.test(transactionFilterFields))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasCauseInstanceOf(EvaluationException.class);
    }

    private Collection<EntityId> entities(String entityId) {
        if (StringUtils.isBlank(entityId)) {
            return null;
        }

        if (entityId.indexOf("/") > -1) {
            String[] entityIds = entityId.split("/");
            EntityId[] createdEntities = new EntityId[entityIds.length];
            for (int i = 0; i < entityIds.length; i++) {
                createdEntities[i] = EntityId.of(entityIds[i]);
            }
            return Arrays.asList(createdEntities);
        }

        return Collections.singleton(EntityId.of(entityId));
    }

    private TransactionFilter filter(String entity, String expression, TransactionType transaction) {
        return new TransactionFilter(
                StringUtils.isNotBlank(entity) ? List.of(EntityId.of(entity)) : null,
                expression,
                transaction != null ? List.of(transaction) : null);
    }
}
