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

import static org.hyperledger.besu.evm.MainnetEVMs.registerLondonOperations;
import static org.hyperledger.besu.evm.MainnetEVMs.registerParisOperations;
import static org.hyperledger.besu.evm.MainnetEVMs.registerShanghaiOperations;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.hedera.mirror.web3.evm.contracts.execution.ContractCreationProcessorV30;
import com.hedera.mirror.web3.evm.contracts.execution.ContractCreationProcessorV34;
import com.hedera.mirror.web3.evm.contracts.execution.ContractCreationProcessorV38;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmMessageCallProcessorV30;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmMessageCallProcessorV34;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmMessageCallProcessorV38;
import com.hedera.mirror.web3.evm.contracts.operations.HederaBlockHashOperation;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.repository.properties.CacheProperties;
import com.hedera.node.app.service.evm.contracts.operations.CreateOperationExternalizer;
import com.hedera.node.app.service.evm.contracts.operations.HederaBalanceOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaDelegateCallOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmChainIdOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmCreate2Operation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmCreateOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaEvmSLoadOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeCopyOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeHashOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeSizeOperation;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
import com.hedera.services.evm.contracts.operations.HederaPrngSeedOperation;
import com.hedera.services.txns.util.PrngLogic;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import javax.inject.Provider;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
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

    public static final String CACHE_MANAGER_ENTITY = "entity";
    public static final String CACHE_MANAGER_RECORD_FILE_LATEST = "recordFileLatest";
    public static final String CACHE_MANAGER_RECORD_FILE_EARLIEST = "recordFileEarliest";
    public static final String CACHE_MANAGER_RECORD_FILE_INDEX = "recordFileIndex";
    public static final String CACHE_MANAGER_CONTRACT_STATE = "contractState";
    public static final String CACHE_MANAGER_SYSTEM_FILE = "systemFile";
    public static final String CACHE_MANAGER_TOKEN = "token";
    public static final String CACHE_NAME = "default";
    public static final String CACHE_NAME_EXCHANGE_RATE = "exchangeRate";
    public static final String CACHE_NAME_FEE_SCHEDULE = "fee_schedule";
    public static final String CACHE_NAME_NFT = "nft";
    public static final String CACHE_NAME_NFT_ALLOWANCE = "nftAllowance";
    public static final String CACHE_NAME_RECORD_FILE_LATEST = "latest";
    public static final String CACHE_NAME_RECORD_FILE_LATEST_INDEX = "latestIndex";
    public static final String CACHE_NAME_TOKEN = "token";
    public static final String CACHE_NAME_TOKEN_ACCOUNT = "tokenAccount";
    public static final String CACHE_NAME_TOKEN_ALLOWANCE = "tokenAllowance";
    public static final String EVM_VERSION_0_30 = "v0.30";
    public static final String EVM_VERSION_0_34 = "v0.34";
    public static final String EVM_VERSION_0_38 = "v0.38";

    public static final String EVM_VERSION = EVM_VERSION_0_38;
    private final CacheProperties cacheProperties;

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
    Map<String, Provider<ContractCreationProcessor>> contractCreationProcessorProvider(
            final ContractCreationProcessorV30 contractCreationProcessorV30,
            final ContractCreationProcessorV34 contractCreationProcessorV34,
            final ContractCreationProcessorV38 contractCreationProcessorV38) {
        Map<String, Provider<ContractCreationProcessor>> processorsMap = new HashMap<>();
        processorsMap.put(EVM_VERSION_0_30, () -> contractCreationProcessorV30);
        processorsMap.put(EVM_VERSION_0_34, () -> contractCreationProcessorV34);
        processorsMap.put(EVM_VERSION_0_38, () -> contractCreationProcessorV38);
        return processorsMap;
    }

    @Bean
    Map<String, Provider<MessageCallProcessor>> messageCallProcessors(
            MirrorEvmMessageCallProcessorV30 mirrorEvmMessageCallProcessorV30,
            MirrorEvmMessageCallProcessorV34 mirrorEvmMessageCallProcessorV34,
            MirrorEvmMessageCallProcessorV38 mirrorEvmMessageCallProcessorV38) {
        Map<String, Provider<MessageCallProcessor>> processorsMap = new HashMap<>();
        processorsMap.put(EVM_VERSION_0_30, () -> mirrorEvmMessageCallProcessorV30);
        processorsMap.put(EVM_VERSION_0_34, () -> mirrorEvmMessageCallProcessorV34);
        processorsMap.put(EVM_VERSION_0_38, () -> mirrorEvmMessageCallProcessorV38);

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
    EVM v38(
            final GasCalculatorHederaV22 gasCalculator,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final HederaPrngSeedOperation prngSeedOperation,
            final HederaBlockHashOperation hederaBlockHashOperation) {
        final var operationRegistry = new OperationRegistry();
        final BiPredicate<Address, MessageFrame> validator = (Address x, MessageFrame y) -> true;

        registerShanghaiOperations(
                operationRegistry,
                gasCalculator,
                mirrorNodeEvmProperties.chainIdBytes32().toBigInteger());
        Set.of(
                        new HederaBalanceOperation(gasCalculator, validator),
                        new HederaDelegateCallOperation(gasCalculator, validator),
                        new HederaEvmChainIdOperation(gasCalculator, mirrorNodeEvmProperties),
                        new HederaEvmCreate2Operation(
                                gasCalculator, mirrorNodeEvmProperties, createOperationExternalizer()),
                        new HederaEvmCreateOperation(gasCalculator, createOperationExternalizer()),
                        new HederaEvmSLoadOperation(gasCalculator),
                        new HederaExtCodeCopyOperation(gasCalculator, validator),
                        new HederaExtCodeHashOperation(gasCalculator, validator),
                        new HederaExtCodeSizeOperation(gasCalculator, validator),
                        prngSeedOperation,
                        hederaBlockHashOperation)
                .forEach(operationRegistry::put);

        return new EVM(
                operationRegistry,
                gasCalculator,
                org.hyperledger.besu.evm.internal.EvmConfiguration.DEFAULT,
                EvmSpecVersion.SHANGHAI);
    }

    @Bean
    EVM v34(
            final GasCalculatorHederaV22 gasCalculator,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final HederaPrngSeedOperation prngSeedOperation,
            final HederaBlockHashOperation hederaBlockHashOperation) {
        final var operationRegistry = new OperationRegistry();
        final BiPredicate<Address, MessageFrame> validator = (Address x, MessageFrame y) -> true;

        registerParisOperations(
                operationRegistry,
                gasCalculator,
                mirrorNodeEvmProperties.chainIdBytes32().toBigInteger());
        Set.of(
                        new HederaBalanceOperation(gasCalculator, validator),
                        new HederaDelegateCallOperation(gasCalculator, validator),
                        new HederaEvmChainIdOperation(gasCalculator, mirrorNodeEvmProperties),
                        new HederaEvmCreate2Operation(
                                gasCalculator, mirrorNodeEvmProperties, createOperationExternalizer()),
                        new HederaEvmCreateOperation(gasCalculator, createOperationExternalizer()),
                        new HederaEvmSLoadOperation(gasCalculator),
                        new HederaExtCodeCopyOperation(gasCalculator, validator),
                        new HederaExtCodeHashOperation(gasCalculator, validator),
                        new HederaExtCodeSizeOperation(gasCalculator, validator),
                        prngSeedOperation,
                        hederaBlockHashOperation)
                .forEach(operationRegistry::put);

        return new EVM(
                operationRegistry,
                gasCalculator,
                org.hyperledger.besu.evm.internal.EvmConfiguration.DEFAULT,
                EvmSpecVersion.PARIS);
    }

    @Bean
    EVM v30(
            final GasCalculatorHederaV22 gasCalculator,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final HederaPrngSeedOperation prngSeedOperation,
            final HederaBlockHashOperation hederaBlockHashOperation) {
        final var operationRegistry = new OperationRegistry();
        final BiPredicate<Address, MessageFrame> validator = (Address x, MessageFrame y) -> true;

        registerLondonOperations(
                operationRegistry,
                gasCalculator,
                mirrorNodeEvmProperties.chainIdBytes32().toBigInteger());
        Set.of(
                        new HederaBalanceOperation(gasCalculator, validator),
                        new HederaDelegateCallOperation(gasCalculator, validator),
                        new HederaEvmChainIdOperation(gasCalculator, mirrorNodeEvmProperties),
                        new HederaEvmCreate2Operation(
                                gasCalculator, mirrorNodeEvmProperties, createOperationExternalizer()),
                        new HederaEvmCreateOperation(gasCalculator, createOperationExternalizer()),
                        new HederaEvmSLoadOperation(gasCalculator),
                        new HederaExtCodeCopyOperation(gasCalculator, validator),
                        new HederaExtCodeHashOperation(gasCalculator, validator),
                        new HederaExtCodeSizeOperation(gasCalculator, validator),
                        prngSeedOperation,
                        hederaBlockHashOperation)
                .forEach(operationRegistry::put);

        return new EVM(
                operationRegistry,
                gasCalculator,
                org.hyperledger.besu.evm.internal.EvmConfiguration.DEFAULT,
                EvmSpecVersion.LONDON);
    }

    @Bean
    HederaPrngSeedOperation hederaPrngSeedOperation(final GasCalculator gasCalculator, final PrngLogic prngLogic) {
        return new HederaPrngSeedOperation(gasCalculator, prngLogic);
    }

    @Bean
    PrecompileContractRegistry precompileContractRegistry() {
        return new PrecompileContractRegistry();
    }
}
