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

package com.hedera.mirror.web3.evm.config;

import static org.hyperledger.besu.evm.internal.EvmConfiguration.WorldUpdaterMode.JOURNALED;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmMessageCallProcessor;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmMessageCallProcessorV30;
import com.hedera.mirror.web3.evm.contracts.operations.HederaBlockHashOperation;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.repository.properties.CacheProperties;
import com.hedera.node.app.service.evm.contracts.operations.CreateOperationExternalizer;
import com.hedera.node.app.service.evm.contracts.operations.HederaBalanceOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaBalanceOperationV038;
import com.hedera.node.app.service.evm.contracts.operations.HederaDelegateCallOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmChainIdOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmCreate2Operation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmCreateOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmSLoadOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeCopyOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeHashOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeHashOperationV038;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeSizeOperation;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
import com.hedera.services.evm.contracts.operations.HederaPrngSeedOperation;
import com.hedera.services.evm.contracts.operations.HederaSelfDestructOperation;
import com.hedera.services.evm.contracts.operations.HederaSelfDestructOperationV038;
import com.hedera.services.evm.contracts.operations.HederaSelfDestructOperationV046;
import com.hedera.services.txns.crypto.AbstractAutoCreationLogic;
import com.hedera.services.txns.util.PrngLogic;
import com.swirlds.common.utility.SemanticVersion;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import javax.inject.Provider;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.BalanceOperation;
import org.hyperledger.besu.evm.operation.ExtCodeHashOperation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.operation.SelfDestructOperation;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
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

    public static final String CACHE_MANAGER_CONTRACT = "contract";
    public static final String CACHE_MANAGER_CONTRACT_STATE = "contractState";
    public static final String CACHE_MANAGER_ENTITY = "entity";
    public static final String CACHE_MANAGER_RECORD_FILE_LATEST = "recordFileLatest";
    public static final String CACHE_MANAGER_RECORD_FILE_EARLIEST = "recordFileEarliest";
    public static final String CACHE_MANAGER_RECORD_FILE_INDEX = "recordFileIndex";
    public static final String CACHE_MANAGER_SYSTEM_FILE = "systemFile";
    public static final String CACHE_MANAGER_TOKEN = "token";
    public static final String CACHE_NAME = "default";
    public static final String CACHE_NAME_CONTRACT = "contract";
    public static final String CACHE_NAME_EXCHANGE_RATE = "exchangeRate";
    public static final String CACHE_NAME_FEE_SCHEDULE = "fee_schedule";
    public static final String CACHE_NAME_NFT = "nft";
    public static final String CACHE_NAME_NFT_ALLOWANCE = "nftAllowance";
    public static final String CACHE_NAME_RECORD_FILE_LATEST = "latest";
    public static final String CACHE_NAME_RECORD_FILE_LATEST_INDEX = "latestIndex";
    public static final String CACHE_NAME_TOKEN = "token";
    public static final String CACHE_NAME_TOKEN_ACCOUNT = "tokenAccount";
    public static final String CACHE_NAME_TOKEN_ALLOWANCE = "tokenAllowance";
    public static final SemanticVersion EVM_VERSION_0_30 = SemanticVersion.parse("0.30.0");
    public static final SemanticVersion EVM_VERSION_0_34 = SemanticVersion.parse("0.34.0");
    public static final SemanticVersion EVM_VERSION_0_38 = SemanticVersion.parse("0.38.0");
    public static final SemanticVersion EVM_VERSION_0_46 = SemanticVersion.parse("0.46.0");
    public static final SemanticVersion EVM_VERSION = EVM_VERSION_0_46;
    private final CacheProperties cacheProperties;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final GasCalculatorHederaV22 gasCalculator;
    private final HederaBlockHashOperation hederaBlockHashOperation;
    private final HederaExtCodeHashOperation hederaExtCodeHashOperation;
    private final HederaExtCodeHashOperationV038 hederaExtCodeHashOperationV038;
    private final AbstractAutoCreationLogic autoCreationLogic;
    private final EntityAddressSequencer entityAddressSequencer;
    private final PrecompiledContractProvider precompilesHolder;
    private final BiPredicate<Address, MessageFrame> addressValidator;
    private final Predicate<Address> systemAccountDetector;

    @Bean(CACHE_MANAGER_CONTRACT)
    CacheManager cacheManagerContract() {
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheNames(Set.of(CACHE_NAME_CONTRACT));
        caffeineCacheManager.setCacheSpecification(cacheProperties.getContract());
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_CONTRACT_STATE)
    CacheManager cacheManagerState() {
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheNames(Set.of(CACHE_NAME));
        caffeineCacheManager.setCacheSpecification(cacheProperties.getContractState());
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_ENTITY)
    CacheManager cacheManagerEntity() {
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheNames(Set.of(CACHE_NAME));
        caffeineCacheManager.setCacheSpecification(cacheProperties.getEntity());
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_TOKEN)
    CacheManager cacheManagerToken() {
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheNames(Set.of(
                CACHE_NAME_NFT,
                CACHE_NAME_NFT_ALLOWANCE,
                CACHE_NAME_TOKEN,
                CACHE_NAME_TOKEN_ACCOUNT,
                CACHE_NAME_TOKEN_ALLOWANCE));
        caffeineCacheManager.setCacheSpecification(cacheProperties.getToken());
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_SYSTEM_FILE)
    CacheManager cacheManagerSystemFile() {
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheNames(Set.of(CACHE_NAME_EXCHANGE_RATE, CACHE_NAME_FEE_SCHEDULE));
        caffeineCacheManager.setCacheSpecification(cacheProperties.getFee());
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_RECORD_FILE_INDEX)
    @Primary
    CacheManager cacheManagerRecordFileIndex() {
        final var caffeine = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(10000)
                .recordStats();
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheNames(Set.of(CACHE_NAME));
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_RECORD_FILE_LATEST)
    CacheManager cacheManagerRecordFileLatest() {
        final var caffeine = Caffeine.newBuilder()
                .expireAfterWrite(500, TimeUnit.MILLISECONDS)
                .maximumSize(1)
                .recordStats();
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheNames(Set.of(CACHE_NAME_RECORD_FILE_LATEST, CACHE_NAME_RECORD_FILE_LATEST_INDEX));
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }

    @Bean(CACHE_MANAGER_RECORD_FILE_EARLIEST)
    CacheManager cacheManagerRecordFileEarliest() {
        final var caffeine = Caffeine.newBuilder().maximumSize(1).recordStats();
        final CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheNames(Set.of(CACHE_NAME));
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }

    @Bean
    Map<SemanticVersion, Provider<ContractCreationProcessor>> contractCreationProcessorProvider(
            final ContractCreationProcessor contractCreationProcessor30,
            final ContractCreationProcessor contractCreationProcessor34,
            final ContractCreationProcessor contractCreationProcessor38,
            final ContractCreationProcessor contractCreationProcessor46) {
        Map<SemanticVersion, Provider<ContractCreationProcessor>> processorsMap = new HashMap<>();
        processorsMap.put(EVM_VERSION_0_30, () -> contractCreationProcessor30);
        processorsMap.put(EVM_VERSION_0_34, () -> contractCreationProcessor34);
        processorsMap.put(EVM_VERSION_0_38, () -> contractCreationProcessor38);
        processorsMap.put(EVM_VERSION_0_46, () -> contractCreationProcessor46);
        return processorsMap;
    }

    @Bean
    Map<SemanticVersion, Provider<MessageCallProcessor>> messageCallProcessors(
            MirrorEvmMessageCallProcessorV30 mirrorEvmMessageCallProcessor30,
            MirrorEvmMessageCallProcessor mirrorEvmMessageCallProcessor34,
            MirrorEvmMessageCallProcessor mirrorEvmMessageCallProcessor38,
            MirrorEvmMessageCallProcessor mirrorEvmMessageCallProcessor46) {
        Map<SemanticVersion, Provider<MessageCallProcessor>> processorsMap = new HashMap<>();
        processorsMap.put(EVM_VERSION_0_30, () -> mirrorEvmMessageCallProcessor30);
        processorsMap.put(EVM_VERSION_0_34, () -> mirrorEvmMessageCallProcessor34);
        processorsMap.put(EVM_VERSION_0_38, () -> mirrorEvmMessageCallProcessor38);
        processorsMap.put(EVM_VERSION_0_46, () -> mirrorEvmMessageCallProcessor46);

        return processorsMap;
    }

    @Bean
    CreateOperationExternalizer createOperationExternalizer() {
        return new CreateOperationExternalizer() {
            @Override
            public void externalize(final MessageFrame frame, final MessageFrame childFrame) {
                // do nothing
            }

            @Override
            public boolean shouldFailBasedOnLazyCreation(final MessageFrame frame, final Address contractAddress) {
                return false;
            }
        };
    }

    @Bean
    org.hyperledger.besu.evm.internal.EvmConfiguration provideEvmConfiguration() {
        return new org.hyperledger.besu.evm.internal.EvmConfiguration(
                org.hyperledger.besu.evm.internal.EvmConfiguration.DEFAULT.jumpDestCacheWeightKB(), JOURNALED);
    }

    @Bean
    EVM evm030(
            final HederaPrngSeedOperation prngSeedOperation,
            final HederaSelfDestructOperation hederaSelfDestructOperation,
            final HederaBalanceOperation hederaBalanceOperation) {
        return evm(
                gasCalculator,
                mirrorNodeEvmProperties,
                prngSeedOperation,
                hederaBlockHashOperation,
                hederaExtCodeHashOperation,
                hederaSelfDestructOperation,
                hederaBalanceOperation,
                EvmSpecVersion.LONDON,
                MainnetEVMs::registerLondonOperations);
    }

    @Bean
    EVM evm034(
            final HederaPrngSeedOperation prngSeedOperation,
            final HederaSelfDestructOperation hederaSelfDestructOperation,
            final HederaBalanceOperation hederaBalanceOperation) {
        return evm(
                gasCalculator,
                mirrorNodeEvmProperties,
                prngSeedOperation,
                hederaBlockHashOperation,
                hederaExtCodeHashOperation,
                hederaSelfDestructOperation,
                hederaBalanceOperation,
                EvmSpecVersion.PARIS,
                MainnetEVMs::registerParisOperations);
    }

    @Bean
    EVM evm038(
            final HederaPrngSeedOperation prngSeedOperation,
            final HederaSelfDestructOperationV038 hederaSelfDestructOperationV038,
            final HederaBalanceOperationV038 hederaBalanceOperationV038) {
        return evm(
                gasCalculator,
                mirrorNodeEvmProperties,
                prngSeedOperation,
                hederaBlockHashOperation,
                hederaExtCodeHashOperationV038,
                hederaSelfDestructOperationV038,
                hederaBalanceOperationV038,
                EvmSpecVersion.SHANGHAI,
                MainnetEVMs::registerShanghaiOperations);
    }

    @Bean
    EVM evm046(
            final HederaPrngSeedOperation prngSeedOperation,
            final HederaSelfDestructOperationV046 hederaSelfDestructOperationV046,
            final HederaBalanceOperationV038 hederaBalanceOperationV038) {
        return evm(
                gasCalculator,
                mirrorNodeEvmProperties,
                prngSeedOperation,
                hederaBlockHashOperation,
                hederaExtCodeHashOperationV038,
                hederaSelfDestructOperationV046,
                hederaBalanceOperationV038,
                EvmSpecVersion.SHANGHAI,
                MainnetEVMs::registerShanghaiOperations);
    }

    @Bean
    HederaPrngSeedOperation hederaPrngSeedOperation(final GasCalculator gasCalculator, final PrngLogic prngLogic) {
        return new HederaPrngSeedOperation(gasCalculator, prngLogic);
    }

    @Bean
    HederaSelfDestructOperation hederaSelfDestructOperation(final GasCalculator gasCalculator) {
        return new HederaSelfDestructOperation(gasCalculator, addressValidator);
    }

    @Bean
    HederaSelfDestructOperationV038 hederaSelfDestructOperationV038(final GasCalculator gasCalculator) {
        return new HederaSelfDestructOperationV038(gasCalculator, addressValidator, systemAccountDetector);
    }

    @Bean
    HederaBalanceOperation hederaBalanceOperation(final GasCalculator gasCalculator) {
        return new HederaBalanceOperation(gasCalculator, addressValidator);
    }

    @Bean
    HederaBalanceOperationV038 hederaBalanceOperationV038(final GasCalculator gasCalculator) {
        return new HederaBalanceOperationV038(
                gasCalculator, addressValidator, systemAccountDetector, mirrorNodeEvmProperties);
    }

    @Bean
    HederaSelfDestructOperationV046 hederaSelfDestructOperationV046(final GasCalculator gasCalculator) {
        return new HederaSelfDestructOperationV046(gasCalculator, addressValidator, systemAccountDetector);
    }

    @Bean
    PrecompileContractRegistry precompileContractRegistry() {
        return new PrecompileContractRegistry();
    }

    @Bean
    public ContractCreationProcessor contractCreationProcessor30(@Qualifier("evm030") EVM evm) {
        return contractCreationProcessor(evm);
    }

    @Bean
    public ContractCreationProcessor contractCreationProcessor34(@Qualifier("evm034") EVM evm) {
        return contractCreationProcessor(evm);
    }

    @Bean
    public ContractCreationProcessor contractCreationProcessor38(@Qualifier("evm038") EVM evm) {
        return contractCreationProcessor(evm);
    }

    @Bean
    public ContractCreationProcessor contractCreationProcessor46(@Qualifier("evm046") EVM evm) {
        return contractCreationProcessor(evm);
    }

    @Bean
    public MirrorEvmMessageCallProcessor mirrorEvmMessageCallProcessor34(@Qualifier("evm034") EVM evm) {
        return mirrorEvmMessageCallProcessor(evm);
    }

    @Bean
    public MirrorEvmMessageCallProcessor mirrorEvmMessageCallProcessor38(@Qualifier("evm038") EVM evm) {
        return mirrorEvmMessageCallProcessor(evm);
    }

    @Bean
    public MirrorEvmMessageCallProcessor mirrorEvmMessageCallProcessor46(@Qualifier("evm046") EVM evm) {
        return mirrorEvmMessageCallProcessor(evm);
    }

    @SuppressWarnings("java:S107")
    private EVM evm(
            final GasCalculator gasCalculator,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final HederaPrngSeedOperation prngSeedOperation,
            final HederaBlockHashOperation hederaBlockHashOperation,
            final ExtCodeHashOperation extCodeHashOperation,
            final SelfDestructOperation selfDestructOperation,
            final BalanceOperation hederaBalanceOperation,
            EvmSpecVersion specVersion,
            OperationRegistryCallback callback) {
        final var operationRegistry = new OperationRegistry();
        final BiPredicate<Address, MessageFrame> validator = (Address x, MessageFrame y) -> true;

        callback.register(
                operationRegistry,
                gasCalculator,
                mirrorNodeEvmProperties.chainIdBytes32().toBigInteger());
        Set.of(
                        new HederaDelegateCallOperation(gasCalculator, validator),
                        new HederaEvmChainIdOperation(gasCalculator, mirrorNodeEvmProperties),
                        new HederaEvmCreate2Operation(
                                gasCalculator, mirrorNodeEvmProperties, createOperationExternalizer()),
                        new HederaEvmCreateOperation(gasCalculator, createOperationExternalizer()),
                        new HederaEvmSLoadOperation(gasCalculator),
                        new HederaExtCodeCopyOperation(gasCalculator, validator),
                        new HederaExtCodeSizeOperation(gasCalculator, validator),
                        prngSeedOperation,
                        hederaBlockHashOperation,
                        extCodeHashOperation,
                        selfDestructOperation,
                        hederaBalanceOperation)
                .forEach(operationRegistry::put);

        return new EVM(operationRegistry, gasCalculator, provideEvmConfiguration(), specVersion);
    }

    private ContractCreationProcessor contractCreationProcessor(EVM evm) {
        return new ContractCreationProcessor(gasCalculator, evm, true, List.of(), 1);
    }

    private MirrorEvmMessageCallProcessor mirrorEvmMessageCallProcessor(EVM evm) {
        return new MirrorEvmMessageCallProcessor(
                autoCreationLogic,
                entityAddressSequencer,
                evm,
                precompileContractRegistry(),
                precompilesHolder,
                gasCalculator);
    }
}
