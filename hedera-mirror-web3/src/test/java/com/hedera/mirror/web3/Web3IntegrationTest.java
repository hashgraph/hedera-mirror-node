/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;

import com.hedera.mirror.common.config.CommonIntegrationTest;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.store.Store;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.mock.mockito.SpyBean;

@ExtendWith(ContextExtension.class)
public abstract class Web3IntegrationTest extends CommonIntegrationTest {

    @SpyBean
    protected MirrorEvmTxProcessor processor;

    @Resource
    protected Store store;

    protected static final byte[] FEE_SCHEDULES = CurrentAndNextFeeSchedule.newBuilder()
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
            .build()
            .toByteArray();
    protected static final byte[] EXCHANGE_RATES_SET = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(12)
                    .setHbarEquiv(1)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(4102444800L))
                    .build())
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(15)
                    .setHbarEquiv(1)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(4102444800L))
                    .build())
            .build()
            .toByteArray();

    @BeforeEach
    protected void setup() {
        domainBuilder
                .entity()
                .customize(e -> e.id(2L).num(2L).balance(5000000000000000000L))
                .persist();
        domainBuilder.entity().customize(e -> e.id(98L).num(98L)).persist();
        domainBuilder
                .fileData()
                .customize(f -> f.entityId(EntityId.of(111)).fileData(FEE_SCHEDULES))
                .persist();
        domainBuilder
                .fileData()
                .customize(f -> f.entityId(EntityId.of(112)).fileData(EXCHANGE_RATES_SET))
                .persist();
    }
}
