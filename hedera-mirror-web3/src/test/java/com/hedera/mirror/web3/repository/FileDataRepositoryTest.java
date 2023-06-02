/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import com.hedera.mirror.common.domain.entity.EntityType;
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
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class FileDataRepositoryTest extends Web3IntegrationTest {

    @Resource
    private final FileDataRepository fileDataRepository;

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

    private static final EntityId FEE_SCHEDULE_ENTITY_ID = new EntityId(0L, 0L, 111L, EntityType.FILE);
    private static final EntityId EXCHANGE_RATE_ENTITY_ID = new EntityId(0L, 0L, 112L, EntityType.FILE);

    @Test
    void getFileForExchangeRates() throws InvalidProtocolBufferException {
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(exchangeRatesSet.toByteArray())
                        .entityId(EXCHANGE_RATE_ENTITY_ID)
                        .consensusTimestamp(expiry))
                .persist();

        final var actualBytes = fileDataRepository.getFileAtTimestamp(EXCHANGE_RATE_ENTITY_ID.getId(), expiry);
        assertThat(ExchangeRateSet.parseFrom(actualBytes)).isEqualTo(exchangeRatesSet);
    }

    @Test
    void getFileForFeeSchedules() throws InvalidProtocolBufferException {
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(feeSchedules.toByteArray())
                        .entityId(FEE_SCHEDULE_ENTITY_ID)
                        .consensusTimestamp(expiry))
                .persist();

        final var actualBytes = fileDataRepository.getFileAtTimestamp(FEE_SCHEDULE_ENTITY_ID.getId(), expiry);
        assertThat(CurrentAndNextFeeSchedule.parseFrom(actualBytes)).isEqualTo(feeSchedules);
    }
}
