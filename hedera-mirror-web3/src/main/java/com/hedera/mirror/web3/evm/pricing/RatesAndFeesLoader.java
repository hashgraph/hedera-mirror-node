/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SYSTEM_FILE;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_EXCHANGE_RATE;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_FEE_SCHEDULE;
import static com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties.HederaNetwork.OTHER;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.InvalidFileException;
import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import jakarta.inject.Named;
import java.util.concurrent.atomic.AtomicLong;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.support.RetryTemplate;

/**
 * Rates and fees loader, currently working only with current timestamp.
 */
@CacheConfig(cacheManager = CACHE_MANAGER_SYSTEM_FILE)
@Named
@RequiredArgsConstructor
@CustomLog
public class RatesAndFeesLoader {

    public static final EntityId EXCHANGE_RATE_ENTITY_ID = EntityId.of(0L, 0L, 112L);
    public static final EntityId FEE_SCHEDULE_ENTITY_ID = EntityId.of(0L, 0L, 111L);
    public static final CurrentAndNextFeeSchedule DEFAULT_FEE_SCHEDULE = CurrentAndNextFeeSchedule.newBuilder()
            .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(4102444800L))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(ContractCall)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(CryptoTransfer)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(EthereumTransaction)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenAccountWipe)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenMint)
                            .addFees(FeeData.newBuilder()
                                    .setSubType(SubType.TOKEN_NON_FUNGIBLE_UNIQUE)
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setMax(1000000000000000L)
                                            .setMin(0)
                                            .build())
                                    .setNodedata(FeeComponents.newBuilder()
                                            .setBpt(40000000000L)
                                            .setMax(1000000000000000L)
                                            .setMin(0)
                                            .build())
                                    .setNetworkdata(FeeComponents.newBuilder()
                                            .setMax(1000000000000000L)
                                            .setBpt(160000000000L)
                                            .setMin(0)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenBurn)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenAssociateToAccount)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())
                                    .build()))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenCreate)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setConstant(7874923918408L)
                                            .setGas(2331415)
                                            .setBpt(349712319)
                                            .setVpt(874280797002L)
                                            .setBpr(349712319)
                                            .setSbpr(8742808)
                                            .setRbh(233142)
                                            .setSbh(17486)
                                            .setMin(0)
                                            .setMax(1000000000000000L)
                                            .build())
                                    .setNetworkdata(FeeComponents.newBuilder()
                                            .setConstant(7874923918408L)
                                            .setGas(2331415)
                                            .setBpt(349712319)
                                            .setVpt(874280797002L)
                                            .setRbh(233142)
                                            .setSbh(17486)
                                            .setBpr(349712319)
                                            .setSbpr(8742808)
                                            .setMin(0)
                                            .setMax(1000000000000000L)
                                            .build())
                                    .setNodedata(FeeComponents.newBuilder()
                                            .setConstant(393746195920L)
                                            .setGas(116571)
                                            .setRbh(11657)
                                            .setSbh(874)
                                            .setBpt(17485616)
                                            .setSbpr(437140)
                                            .setVpt(43714039850L)
                                            .setBpr(17485616)
                                            .setMin(0)
                                            .setMax(1000000000000000L)
                                            .build())
                                    .build())))
            .setNextFeeSchedule(FeeSchedule.newBuilder()
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenMint)
                            .addFees(FeeData.newBuilder()
                                    .setSubType(SubType.TOKEN_NON_FUNGIBLE_UNIQUE)
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setMax(1000000000000000L)
                                            .setMin(0)
                                            .build())
                                    .setNodedata(FeeComponents.newBuilder()
                                            .setBpt(40000000000L)
                                            .setMax(1000000000000000L)
                                            .setMin(0)
                                            .build())
                                    .setNetworkdata(FeeComponents.newBuilder()
                                            .setMax(1000000000000000L)
                                            .setMin(0)
                                            .setBpt(160000000000L)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(CryptoTransfer)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenAccountWipe)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenBurn)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenAssociateToAccount)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(ContractCall)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build())))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(TokenCreate)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setConstant(7874923918408L)
                                            .setGas(2331415)
                                            .setBpt(349712319)
                                            .setVpt(874280797002L)
                                            .setBpr(349712319)
                                            .setSbpr(8742808)
                                            .setRbh(233142)
                                            .setSbh(17486)
                                            .setMin(0)
                                            .setMax(1000000000000000L)
                                            .build())
                                    .setNetworkdata(FeeComponents.newBuilder()
                                            .setConstant(7874923918408L)
                                            .setGas(2331415)
                                            .setBpt(349712319)
                                            .setVpt(874280797002L)
                                            .setRbh(233142)
                                            .setSbh(17486)
                                            .setBpr(349712319)
                                            .setSbpr(8742808)
                                            .setMin(0)
                                            .setMax(1000000000000000L)
                                            .build())
                                    .setNodedata(FeeComponents.newBuilder()
                                            .setConstant(393746195920L)
                                            .setGas(116571)
                                            .setRbh(11657)
                                            .setSbh(874)
                                            .setBpt(17485616)
                                            .setSbpr(437140)
                                            .setVpt(43714039850L)
                                            .setBpr(17485616)
                                            .setMin(0)
                                            .setMax(1000000000000000L)
                                            .build())
                                    .build()))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(EthereumTransaction)
                            .addFees(FeeData.newBuilder()
                                    .setServicedata(FeeComponents.newBuilder()
                                            .setGas(852000)
                                            .build()))))
            .build();
    static final ExchangeRateSet DEFAULT_EXCHANGE_RATE_SET = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(12)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(4102444800L))
                    .setHbarEquiv(1))
            .build();
    private static final CurrentAndNextFeeSchedule EMPTY_FEE_SCHEDULE = CurrentAndNextFeeSchedule.getDefaultInstance();
    private static final ExchangeRateSet EMPTY_EXCHANGE_RATE_SET = ExchangeRateSet.getDefaultInstance();
    private final RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(10)
            .retryOn(InvalidFileException.class)
            .build();

    private final FileDataRepository fileDataRepository;
    private final MirrorNodeEvmProperties evmProperties;

    /**
     * Loads the exchange rates for a given time. Currently, works only with current timestamp.
     *
     * @param nanoSeconds timestamp
     * @return exchange rates set
     */
    @Cacheable(cacheNames = CACHE_NAME_EXCHANGE_RATE, key = "'now'", unless = "#result == null")
    public ExchangeRateSet loadExchangeRates(final long nanoSeconds) {
        try {
            return getFileData(
                    EXCHANGE_RATE_ENTITY_ID.getId(),
                    new AtomicLong(nanoSeconds),
                    ExchangeRateSet::parseFrom,
                    evmProperties.getNetwork() == OTHER ? DEFAULT_EXCHANGE_RATE_SET : EMPTY_EXCHANGE_RATE_SET);
        } catch (InvalidFileException e) {
            log.warn("Corrupt rate file at {}, may require remediation!", EXCHANGE_RATE_ENTITY_ID);
            throw new IllegalStateException(String.format("Rates %s are corrupt!", EXCHANGE_RATE_ENTITY_ID));
        }
    }

    /**
     * Load the fee schedules for a given time. Currently, works only with current timestamp.
     *
     * @param nanoSeconds timestamp
     * @return current and next fee schedules
     */
    @Cacheable(cacheNames = CACHE_NAME_FEE_SCHEDULE, key = "'now'", unless = "#result == null")
    public CurrentAndNextFeeSchedule loadFeeSchedules(final long nanoSeconds) {
        try {
            return getFileData(
                    FEE_SCHEDULE_ENTITY_ID.getId(),
                    new AtomicLong(nanoSeconds),
                    CurrentAndNextFeeSchedule::parseFrom,
                    evmProperties.getNetwork() == OTHER ? DEFAULT_FEE_SCHEDULE : EMPTY_FEE_SCHEDULE);
        } catch (InvalidFileException e) {
            log.warn("Corrupt fee schedules file at {}, may require remediation!", FEE_SCHEDULE_ENTITY_ID);
            throw new IllegalStateException(String.format("Fee schedule %s is corrupt!", FEE_SCHEDULE_ENTITY_ID));
        }
    }

    private <T> T getFileData(long fileId, final AtomicLong nanoSeconds, FileDataParser<T> parser, T defaultValue) {
        return retryTemplate.execute(context -> fileDataRepository
                .getFileAtTimestamp(fileId, nanoSeconds.get())
                .map(fileData -> {
                    try {
                        return parser.parse(fileData.getFileData());
                    } catch (InvalidProtocolBufferException e) {
                        log.warn(
                                "Failed to load file data for fileId {} at {}, failing back to previous file. Retry attempt {}. Exception: ",
                                fileId,
                                nanoSeconds.get(),
                                context.getRetryCount() + 1,
                                e);
                        nanoSeconds.set(fileData.getConsensusTimestamp() - 1);
                        throw new InvalidFileException(e);
                    }
                })
                .orElse(defaultValue));
    }

    private interface FileDataParser<T> {
        T parse(byte[] bytes) throws InvalidProtocolBufferException;
    }
}
