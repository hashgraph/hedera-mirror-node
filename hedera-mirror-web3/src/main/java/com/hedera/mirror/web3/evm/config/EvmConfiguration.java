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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.repository.properties.CacheProperties;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
import com.hedera.services.fees.BasicHbarCentExchange;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calc.OverflowCheckingCalc;
import com.hedera.services.fees.calculation.BasicFcfsUsagePrices;
import com.hedera.services.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.calculation.utils.AccessorBasedUsages;
import com.hedera.services.fees.calculation.utils.PricedUsageCalculator;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.PrecompileMapper;
import com.hedera.services.store.contracts.precompile.impl.AssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiAssociatePrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txn.token.AssociateLogic;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.utils.accessors.AccessorFactory;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
    GasCalculatorHederaV22 gasCalculatorHederaV22(
            BasicFcfsUsagePrices usagePricesProvider, BasicHbarCentExchange hbarCentExchange) {
        return new GasCalculatorHederaV22(usagePricesProvider, hbarCentExchange);
    }

    @Bean
    BasicFcfsUsagePrices basicFcfsUsagePrices(RatesAndFeesLoader ratesAndFeesLoader) {
        return new BasicFcfsUsagePrices(ratesAndFeesLoader);
    }

    @Bean
    AccessorBasedUsages accessorBasedUsages() {
        return new AccessorBasedUsages();
    }

    @Bean
    OverflowCheckingCalc overflowCheckingCalc() {
        return new OverflowCheckingCalc();
    }

    @Bean
    PricedUsageCalculator pricedUsageCalculator(
            AccessorBasedUsages accessorBasedUsages, OverflowCheckingCalc overflowCheckingCalc) {
        return new PricedUsageCalculator(accessorBasedUsages, overflowCheckingCalc);
    }

    @Bean
    UsageBasedFeeCalculator usageBasedFeeCalculator(
            HbarCentExchange hbarCentExchange,
            UsagePricesProvider usagePricesProvider,
            PricedUsageCalculator pricedUsageCalculator) {
        // queryUsageEstimators and txnResourceUsegaEstimator will be implemented in separate PR
        return new UsageBasedFeeCalculator(
                hbarCentExchange,
                usagePricesProvider,
                pricedUsageCalculator,
                Collections.emptySet(),
                Collections.emptyMap());
    }

    @Bean
    AssetsLoader assetsLoader() {
        return new AssetsLoader();
    }

    @Bean
    AccessorFactory accessorFactory() {
        return new AccessorFactory();
    }

    @Bean
    PrecompilePricingUtils precompilePricingUtils(
            final AssetsLoader assetsLoader,
            final BasicHbarCentExchange exchange,
            final FeeCalculator feeCalculator,
            final BasicFcfsUsagePrices resourceCosts,
            final AccessorFactory accessorFactory) {
        return new PrecompilePricingUtils(assetsLoader, exchange, feeCalculator, resourceCosts, accessorFactory);
    }

    @Bean
    BasicHbarCentExchange basicHbarCentExchange(RatesAndFeesLoader ratesAndFeesLoader) {
        return new BasicHbarCentExchange(ratesAndFeesLoader);
    }

    @Bean
    AssociatePrecompile associatePrecompile(
            final PrecompilePricingUtils precompilePricingUtils, final MirrorNodeEvmProperties properties) {
        return new AssociatePrecompile(precompilePricingUtils, properties);
    }

    @Bean
    MultiAssociatePrecompile multiAssociatePrecompile(
            final PrecompilePricingUtils precompilePricingUtils, final MirrorNodeEvmProperties properties) {
        return new MultiAssociatePrecompile(precompilePricingUtils, properties);
    }

    @Bean
    PrecompileMapper precompileMapper(final Set<Precompile> precompiles) {
        return new PrecompileMapper(precompiles);
    }

    @Bean
    AssociateLogic associateLogic(MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        return new AssociateLogic(mirrorNodeEvmProperties);
    }

    @Bean
    AutoCreationLogic autocreationLogic(FeeCalculator feeCalculator, MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        return new AutoCreationLogic(feeCalculator, mirrorNodeEvmProperties);
    }
}
