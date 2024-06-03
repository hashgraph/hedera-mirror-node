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

package com.hedera.mirror.web3.repository;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
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
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class FileDataRepositoryTest extends Web3IntegrationTest {

    private static final long expiry = 1_234_567_890L;
    private static final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(1)
                    .setHbarEquiv(12)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(expiry))
                    .build())
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(2)
                    .setHbarEquiv(31)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                    .build())
            .build();

    private static final ExchangeRateSet exchangeRatesSet200 = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(2)
                    .setHbarEquiv(13)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(200))
                    .build())
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(3)
                    .setHbarEquiv(32)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_893L))
                    .build())
            .build();

    private static final ExchangeRateSet exchangeRatesSet300 = ExchangeRateSet.newBuilder()
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
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(expiry))
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

    @Resource
    private final FileDataRepository fileDataRepository;

    @Test
    void getHistoricalFileForExchangeRates() throws InvalidProtocolBufferException {
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(exchangeRatesSet200.toByteArray())
                        .entityId(EXCHANGE_RATE_ENTITY_ID)
                        .consensusTimestamp(200L))
                .persist();
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(exchangeRatesSet300.toByteArray())
                        .entityId(EXCHANGE_RATE_ENTITY_ID)
                        .consensusTimestamp(300L))
                .persist();

        var fileDataCurrent = fileDataRepository.getFileAtTimestamp(EXCHANGE_RATE_ENTITY_ID.getId(), 301);
        var fileData1 = fileDataRepository.getFileAtTimestamp(EXCHANGE_RATE_ENTITY_ID.getId(), 300);
        assertThat(fileDataCurrent).isEqualTo(fileData1);
        assertThat(ExchangeRateSet.parseFrom(fileData1.get(0).getFileData())).isEqualTo(exchangeRatesSet300);

        var fileData2 = fileDataRepository.getFileAtTimestamp(
                EXCHANGE_RATE_ENTITY_ID.getId(), fileData1.get(0).getConsensusTimestamp() - 1);
        assertThat(ExchangeRateSet.parseFrom(fileData2.get(0).getFileData())).isEqualTo(exchangeRatesSet200);

        var fileData3 = fileDataRepository.getFileAtTimestamp(
                EXCHANGE_RATE_ENTITY_ID.getId(), fileData2.get(0).getConsensusTimestamp() - 1);
        assert (fileData3).isEmpty();
    }

    @Test
    void getFileForExchangeRates() throws InvalidProtocolBufferException {
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(exchangeRatesSet.toByteArray())
                        .entityId(EXCHANGE_RATE_ENTITY_ID)
                        .consensusTimestamp(expiry))
                .persist();

        final var fileData = fileDataRepository.getFileAtTimestamp(EXCHANGE_RATE_ENTITY_ID.getId(), expiry);
        assertThat(ExchangeRateSet.parseFrom(fileData.getFirst().getFileData())).isEqualTo(exchangeRatesSet);
    }

    @Test
    void getFileForFeeSchedules() throws InvalidProtocolBufferException {
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(feeSchedules.toByteArray())
                        .entityId(FEE_SCHEDULE_ENTITY_ID)
                        .consensusTimestamp(expiry))
                .persist();

        final var fileData = fileDataRepository.getFileAtTimestamp(FEE_SCHEDULE_ENTITY_ID.getId(), expiry);
        assertThat(CurrentAndNextFeeSchedule.parseFrom(fileData.getFirst().getFileData()))
                .isEqualTo(feeSchedules);
    }
}
