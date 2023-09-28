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

package com.hedera.mirror.web3.evm.config;

import static com.hedera.mirror.web3.evm.contracts.execution.EvmOperationConstructionUtil.ccps;
import static com.hedera.mirror.web3.evm.contracts.execution.EvmOperationConstructionUtil.mcps;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.hedera.mirror.web3.common.ThreadLocalHolder;
import com.hedera.mirror.web3.evm.account.AccountAccessorImpl;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.MirrorOperationTracer;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.properties.StaticBlockMetaSource;
import com.hedera.mirror.web3.evm.properties.TraceProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmWorldState;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;
import com.hedera.mirror.web3.evm.token.TokenAccessorImpl;
import com.hedera.mirror.web3.repository.properties.CacheProperties;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.services.contracts.execution.LivePricesSource;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
import com.hedera.services.fees.BasicHbarCentExchange;
import com.hedera.services.store.contracts.precompile.PrecompileMapper;
import com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract;
import com.hedera.services.txns.crypto.AbstractAutoCreationLogic;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class EvmConfiguration {

    public static final String CACHE_MANAGER_FEE = "cacheManagerFee";
    public static final String CACHE_MANAGER_10MIN = "cacheManager10Min";
    public static final String CACHE_MANAGER_500MS = "cacheManager500Ms";
    public static final String CACHE_MANAGER_STATE = "cacheManagerState";
    public static final String CACHE_MANAGER_ENTITY = "cacheManagerEntity";
    public static final String CACHE_MANAGER_TOKEN = "cacheManagerToken";
    private final CacheProperties cacheProperties;

    @Bean(CACHE_MANAGER_STATE)
    CacheManager cacheManagerState() {
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheSpecification(cacheProperties.getContractState());
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_ENTITY)
    CacheManager cacheManagerEntity() {
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheSpecification(cacheProperties.getEntity());
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_TOKEN)
    CacheManager cacheManagerToken() {
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheSpecification(cacheProperties.getToken());
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_FEE)
    CacheManager cacheManagerFee() {
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheSpecification(cacheProperties.getFee());
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_10MIN)
    @Primary
    CacheManager cacheManager10Min() {
        final var caffeine =
                Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(10000);
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_500MS)
    CacheManager cacheManager500MS() {
        final var caffeine = Caffeine.newBuilder()
                .expireAfterWrite(500, TimeUnit.MILLISECONDS)
                .maximumSize(1);
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }

    @Bean
    TokenAccessorImpl tokenAccessor(
            final MirrorNodeEvmProperties evmProperties,
            final StoreImpl store,
            final MirrorEvmContractAliases mirrorEvmContractAliases) {
        return new TokenAccessorImpl(evmProperties, store, mirrorEvmContractAliases);
    }

    @Bean
    AccountAccessorImpl accountAccessor(
            final StoreImpl store,
            final MirrorEntityAccess mirrorEntityAccess,
            final MirrorEvmContractAliases mirrorEvmContractAliases) {
        return new AccountAccessorImpl(store, mirrorEntityAccess, mirrorEvmContractAliases);
    }

    @Bean
    AbstractCodeCache abstractCodeCache(
            final MirrorNodeEvmProperties evmProperties, final MirrorEntityAccess mirrorEntityAccess) {
        return new AbstractCodeCache(
                (int) evmProperties.getExpirationCacheTime().toSeconds(), mirrorEntityAccess);
    }

    @Bean
    MirrorOperationTracer mirrorOperationTracer(
            final TraceProperties traceProperties, final MirrorEvmContractAliases mirrorEvmContractAliases) {
        return new MirrorOperationTracer(traceProperties, mirrorEvmContractAliases);
    }

    @Bean
    HederaEvmWorldState hederaEvmWorldState(
            final MirrorEntityAccess mirrorEntityAccess,
            final MirrorNodeEvmProperties evmProperties,
            final AbstractCodeCache abstractCodeCache,
            final AccountAccessorImpl accountAccessor,
            final TokenAccessorImpl tokenAccessor,
            final EntityAddressSequencer entityAddressSequencer,
            final MirrorEvmContractAliases mirrorEvmContractAliases,
            final Store store) {
        return new HederaEvmWorldState(
                mirrorEntityAccess,
                evmProperties,
                abstractCodeCache,
                accountAccessor,
                tokenAccessor,
                entityAddressSequencer,
                mirrorEvmContractAliases,
                store);
    }

    @Bean(name = "mirrorEvmTxProcessor")
    @Scope(value = "prototype")
    MirrorEvmTxProcessor mirrorEvmTxProcessor(
            final HederaEvmWorldState worldState,
            final LivePricesSource pricesAndFees,
            final MirrorNodeEvmProperties evmProperties,
            final GasCalculatorHederaV22 gasCalculator,
            final AbstractAutoCreationLogic autoCreationLogic,
            final PrecompileMapper precompileMapper,
            final EntityAddressSequencer entityAddressSequencer,
            final MirrorEvmContractAliases mirrorEvmContractAliases,
            final StaticBlockMetaSource blockMetaSource,
            final AbstractCodeCache abstractCodeCache,
            final MirrorOperationTracer mirrorOperationTracer,
            final BasicHbarCentExchange basicHbarCentExchange,
            final PrngSystemPrecompiledContract prngSystemPrecompiledContract,
            final Store store) {
        return new MirrorEvmTxProcessor(
                worldState,
                pricesAndFees,
                evmProperties,
                gasCalculator,
                mcps(
                        gasCalculator,
                        autoCreationLogic,
                        entityAddressSequencer,
                        mirrorEvmContractAliases,
                        evmProperties,
                        precompileMapper,
                        basicHbarCentExchange,
                        prngSystemPrecompiledContract,
                        ThreadLocalHolder.isEstimate()),
                ccps(gasCalculator, evmProperties),
                blockMetaSource,
                mirrorEvmContractAliases,
                abstractCodeCache,
                mirrorOperationTracer,
                store);
    }
}
