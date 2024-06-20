/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader.FEE_SCHEDULE_ENTITY_ID;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.expiry;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.feeSchedules;

import com.hedera.mirror.common.config.CommonTestConfiguration;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.config.Web3jTestConfiguration;
import com.hedera.mirror.web3.service.resources.ContractDeployer;
import com.hedera.mirror.web3.service.resources.PrecompileTestContract;
import com.hedera.mirror.web3.utils.TestWeb3jService;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import java.math.BigInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

@Component
@Import({CommonTestConfiguration.class, Web3jTestConfiguration.class})
public class Web3jArgumentsProvider implements ArgumentsProvider {
    protected static RecordFile recordFileBeforeEvm34;
    protected static RecordFile recordFileAfterEvm34;
    protected static RecordFile recordFileEvm38;
    protected static RecordFile recordFileEvm46;
    protected static RecordFile recordFileEvm46Latest;
    // The block numbers lower than EVM v0.34 are considered part of EVM v0.30 which includes all precompiles
    public static final long EVM_V_34_BLOCK = 50L;
    protected static final long EVM_V_38_BLOCK = 100L;
    protected static final long EVM_V_46_BLOCK = 150L;

    @Autowired
    private ContractDeployer contractDeployer;

    @Autowired
    private DomainBuilder domainBuilder;

    @Autowired
    private TestWeb3jService testWeb3jService;

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
        // Test setup
        historicalBlocksPersist();
        fileDataPersist();
        feeSchedulesPersist();
        final var tokenEntity = fungibleTokenPersist();
        final var tokenAddress =
                toAddress(EntityId.of(0, 0, tokenEntity.getNum())).toHexString();
        final var senderEntity = senderEntityPersistDynamic();
        final var senderAddress =
                toAddress(EntityId.of(0, 0, senderEntity.getNum())).toHexString();
        tokenAccountPersist(senderEntity.toEntityId(), tokenEntity.toEntityId(), TokenFreezeStatusEnum.FROZEN);

        // Deploy Contract
        var contract = contractDeployer.deploy(PrecompileTestContract.class);
        // Override sender in the parameters
        testWeb3jService.setSender(senderAddress);

        return Stream.of(Arguments.of(contract.isTokenFrozen(tokenAddress, senderAddress), BigInteger.ONE));
    }

    private void historicalBlocksPersist() {
        recordFileBeforeEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK - 1))
                .persist();
        recordFileAfterEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK))
                .persist();
        recordFileEvm38 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_38_BLOCK))
                .persist();
        recordFileEvm46 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_46_BLOCK))
                .persist();
        recordFileEvm46Latest = domainBuilder.recordFile().persist();
    }

    private void fileDataPersist() {
        final long nanos = 1_234_567_890L;
        final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
                .setCurrentRate(ExchangeRate.newBuilder()
                        .setCentEquiv(1)
                        .setHbarEquiv(12)
                        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(nanos))
                        .build())
                .setNextRate(ExchangeRate.newBuilder()
                        .setCentEquiv(2)
                        .setHbarEquiv(31)
                        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                        .build())
                .build();
        final var timeStamp = System.currentTimeMillis();
        final var entityId = EntityId.of(0L, 0L, 112L);
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(exchangeRatesSet.toByteArray())
                        .entityId(entityId)
                        .consensusTimestamp(timeStamp))
                .persist();
    }

    private void feeSchedulesPersist() {
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(feeSchedules.toByteArray())
                        .entityId(FEE_SCHEDULE_ENTITY_ID)
                        .consensusTimestamp(expiry + 1))
                .persist();
    }

    public Entity fungibleTokenPersist() {
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(TOKEN).balance(1500L).memo("TestMemo"))
                .persist();

        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .supplyType(TokenSupplyTypeEnum.INFINITE))
                .persist();

        return tokenEntity;
    }

    public Entity senderEntityPersistDynamic() {
        final var senderEntity = domainBuilder
                .entity()
                .customize(e -> e.deleted(false).balance(10000 * 100_000_000L))
                .persist();
        return senderEntity;
    }

    // Account persist
    public void tokenAccountPersist(
            final EntityId senderEntityId, final EntityId tokenEntityId, final TokenFreezeStatusEnum freezeStatus) {
        domainBuilder
                .tokenAccount()
                .customize(e -> e.freezeStatus(freezeStatus)
                        .accountId(senderEntityId.getId())
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true)
                        .balance(12L))
                .persist();
    }
}
