/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.pricing;

import static com.hedera.mirror.common.domain.transaction.TransactionType.FILEAPPEND;
import static com.hedera.mirror.common.domain.transaction.TransactionType.FILECREATE;
import static com.hedera.mirror.common.domain.transaction.TransactionType.FILEUPDATE;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import jakarta.annotation.Resource;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@RequiredArgsConstructor
class RatesAndFeesLoaderIntegrationTest extends Web3IntegrationTest {

    @Resource
    private RatesAndFeesLoader subject;

    private static final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(1)
                    .setHbarEquiv(12)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(200L))
                    .build())
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(2)
                    .setHbarEquiv(31)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                    .build())
            .build();

    private static final ExchangeRateSet exchangeRatesSet2 = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(3)
                    .setHbarEquiv(14)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(300))
                    .build())
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(4)
                    .setHbarEquiv(33)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_893L))
                    .build())
            .build();

    private static final CurrentAndNextFeeSchedule feeSchedules = CurrentAndNextFeeSchedule.newBuilder()
            .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(200L))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(ContractCall)
                            .addFees(FeeData.newBuilder().build())))
            .setNextFeeSchedule(FeeSchedule.newBuilder()
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(ContractCall)
                            .addFees(FeeData.newBuilder().build())))
            .build();

    private static final CurrentAndNextFeeSchedule feeSchedules2 = CurrentAndNextFeeSchedule.newBuilder()
            .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(300L))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(ContractCall)
                            .addFees(FeeData.newBuilder().build())))
            .setNextFeeSchedule(FeeSchedule.newBuilder()
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(ContractCall)
                            .addFees(FeeData.newBuilder().build())))
            .build();

    private static final EntityId FEE_SCHEDULE_ENTITY_ID = EntityId.of(0L, 0L, 111L);
    private static final EntityId EXCHANGE_RATE_ENTITY_ID = EntityId.of(0L, 0L, 112L);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getFileForExchangeRateFallback(boolean corrupt) {
        var exchangeSetBytes = exchangeRatesSet.toByteArray();
        var exchangeSetPart1 = Arrays.copyOfRange(exchangeSetBytes, 0, 10);
        var exchangeSetPart2 = Arrays.copyOfRange(exchangeSetBytes, 10, 20);
        var exchangeSetPart3 = Arrays.copyOfRange(exchangeSetBytes, 20, exchangeSetBytes.length);
        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILECREATE.getProtoId())
                        .fileData(exchangeSetPart1)
                        .entityId(EXCHANGE_RATE_ENTITY_ID)
                        .consensusTimestamp(200L))
                .persist();
        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILEAPPEND.getProtoId())
                        .fileData(exchangeSetPart2)
                        .entityId(EXCHANGE_RATE_ENTITY_ID)
                        .consensusTimestamp(205L))
                .persist();
        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILEAPPEND.getProtoId())
                        .fileData(exchangeSetPart3)
                        .entityId(EXCHANGE_RATE_ENTITY_ID)
                        .consensusTimestamp(210L))
                .persist();

        var exchangeSet2Bytes = exchangeRatesSet2.toByteArray();
        var exchangeSet2Part1 = Arrays.copyOfRange(exchangeSet2Bytes, 0, 10);
        var exchangeSet2Part2 = Arrays.copyOfRange(exchangeSet2Bytes, 10, 20);
        var exchangeSet2Part3 = Arrays.copyOfRange(exchangeSet2Bytes, 20, exchangeSet2Bytes.length);
        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILEUPDATE.getProtoId())
                        .fileData(exchangeSet2Part1)
                        .entityId(EXCHANGE_RATE_ENTITY_ID)
                        .consensusTimestamp(300L))
                .persist();
        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILEAPPEND.getProtoId())
                        .fileData(exchangeSet2Part2)
                        .entityId(EXCHANGE_RATE_ENTITY_ID)
                        .consensusTimestamp(305L))
                .persist();

        var fileData = corrupt ? "corrupt".getBytes() : exchangeSet2Part3;
        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILEAPPEND.getProtoId())
                        .fileData(fileData)
                        .entityId(EXCHANGE_RATE_ENTITY_ID)
                        .consensusTimestamp(310L))
                .persist();

        var expected = corrupt ? exchangeRatesSet : exchangeRatesSet2;
        var actual = subject.loadExchangeRates(350L);
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getFileForFeeScheduleFallback(boolean corrupt) {
        var feeSchedulesBytes = feeSchedules.toByteArray();
        var feeSchedulesPart1 = Arrays.copyOfRange(feeSchedulesBytes, 0, 10);
        var feeSchedulesPart2 = Arrays.copyOfRange(feeSchedulesBytes, 10, 20);
        var feeSchedulesPart3 = Arrays.copyOfRange(feeSchedulesBytes, 20, feeSchedulesBytes.length);
        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILEUPDATE.getProtoId())
                        .fileData(feeSchedulesPart1)
                        .entityId(FEE_SCHEDULE_ENTITY_ID)
                        .consensusTimestamp(200L))
                .persist();
        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILEAPPEND.getProtoId())
                        .fileData(feeSchedulesPart2)
                        .entityId(FEE_SCHEDULE_ENTITY_ID)
                        .consensusTimestamp(205L))
                .persist();
        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILEAPPEND.getProtoId())
                        .fileData(feeSchedulesPart3)
                        .entityId(FEE_SCHEDULE_ENTITY_ID)
                        .consensusTimestamp(210L))
                .persist();

        var feeSchedules2Bytes = feeSchedules2.toByteArray();
        var feeSchedules2Part1 = Arrays.copyOfRange(feeSchedules2Bytes, 0, 10);
        var feeSchedules2Part2 = Arrays.copyOfRange(feeSchedules2Bytes, 10, 20);
        var feeSchedules2Part3 = Arrays.copyOfRange(feeSchedules2Bytes, 20, feeSchedules2Bytes.length);
        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILEUPDATE.getProtoId())
                        .fileData(feeSchedules2Part1)
                        .entityId(FEE_SCHEDULE_ENTITY_ID)
                        .consensusTimestamp(300L))
                .persist();
        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILEAPPEND.getProtoId())
                        .fileData(feeSchedules2Part2)
                        .entityId(FEE_SCHEDULE_ENTITY_ID)
                        .consensusTimestamp(305L))
                .persist();

        var fileData = corrupt ? "corrupt".getBytes() : feeSchedules2Part3;
        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILEAPPEND.getProtoId())
                        .fileData(fileData)
                        .entityId(FEE_SCHEDULE_ENTITY_ID)
                        .consensusTimestamp(310L))
                .persist();

        var expected = corrupt ? feeSchedules : feeSchedules2;
        var actual = subject.loadFeeSchedules(350L);
        assertThat(actual).isEqualTo(expected);
    }
}
