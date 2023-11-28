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

import static org.hyperledger.besu.evm.MainnetEVMs.registerShanghaiOperations;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmMessageCallProcessor;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmMessageCallProcessorErc;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmMessageCallProcessorErcHts;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmMessageCallProcessorErcHtsExchangeRate;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import javax.inject.Provider;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
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
    public static final String EVM_VERSION_0_45 = "v0.45";
    public static final String HTS = "HTS";
    public static final String ERC = "ERC";
    public static final String PRNG = "PRNG";
    public static final String EXCHANGE_RATE = "exchangeRate";
    public static final String ERC_HTS = ERC + "," + HTS;
    public static final String HTS_ERC_EXCHANGE_RATE = ERC_HTS + "," + EXCHANGE_RATE;
    public static final String EVM_VERSION_0_30_ERC = EVM_VERSION_0_30 + "-" + ERC;
    public static final String EVM_VERSION_0_30_ERC_HTS= EVM_VERSION_0_30 + "-" + ERC_HTS;
    public static final String EVM_VERSION_0_30_HTS_ERC_EXCHANGE_RATE = EVM_VERSION_0_30 + "-" + HTS_ERC_EXCHANGE_RATE;

    public static final String EVM_VERSION = EVM_VERSION_0_34;
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
    MessageCallProcessor messageCallProcessor(final EVM evm, final PrecompileContractRegistry precompiles) {
        return new MessageCallProcessor(evm, precompiles);
        // we will need more changes here
    }

    @Bean
    Map<String, Provider<ContractCreationProcessor>> contractCreationProcessors(
            final ContractCreationProcessor contractCreationProcessor) {

        Map<String, Provider<ContractCreationProcessor>> processorsMap = new HashMap<>();
        processorsMap.put(EVM_VERSION_0_30_ERC, () -> contractCreationProcessor);
        processorsMap.put(EVM_VERSION_0_30_ERC_HTS, () -> contractCreationProcessor);
        processorsMap.put(EVM_VERSION_0_30_HTS_ERC_EXCHANGE_RATE, () -> contractCreationProcessor);
        processorsMap.put(EVM_VERSION_0_30, () -> contractCreationProcessor);
        processorsMap.put(EVM_VERSION_0_34, () -> contractCreationProcessor);
        processorsMap.put(EVM_VERSION_0_38, () -> contractCreationProcessor);
        processorsMap.put(EVM_VERSION_0_45, () -> contractCreationProcessor);
        return processorsMap;
    }

    @Bean
    Map<String, Provider<MessageCallProcessor>> messageCallProcessors(
            final MessageCallProcessor messageCallProcessor,
            final MirrorEvmMessageCallProcessor mirrorEvmMessageCallProcessor,
            final MirrorEvmMessageCallProcessorErc mirrorEvmMessageCallProcessorErc,
            final MirrorEvmMessageCallProcessorErcHts mirrorEvmMessageCallProcessorErcHts,
            final MirrorEvmMessageCallProcessorErcHtsExchangeRate mirrorEvmMessageCallProcessorErcHtsExchangeRate) {

        Map<String, Provider<MessageCallProcessor>> processorsMap = new HashMap<>();
        processorsMap.put(EVM_VERSION_0_30_ERC, () -> mirrorEvmMessageCallProcessorErc);
        processorsMap.put(EVM_VERSION_0_30_ERC_HTS, () -> mirrorEvmMessageCallProcessorErcHts);
        processorsMap.put(EVM_VERSION_0_30_HTS_ERC_EXCHANGE_RATE, () -> mirrorEvmMessageCallProcessorErcHtsExchangeRate);
        processorsMap.put(EVM_VERSION_0_30, () -> messageCallProcessor);
        processorsMap.put(EVM_VERSION_0_34, () -> mirrorEvmMessageCallProcessor);
        processorsMap.put(EVM_VERSION_0_38, () -> mirrorEvmMessageCallProcessor);
        processorsMap.put(EVM_VERSION_0_45, () -> mirrorEvmMessageCallProcessor);

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
    EVM evm(
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
                mirrorNodeEvmProperties.getEvmSpecVersion());
    }

    // @Bean
    // EVM evmHTS(
    //        final GasCalculatorHederaV22 gasCalculator,
    //        final MirrorNodeEvmProperties mirrorNodeEvmProperties,
    //        final HederaBlockHashOperation hederaBlockHashOperation) {
    //    final var operationRegistry = new OperationRegistry();
    //    final BiPredicate<Address, MessageFrame> validator = (Address x, MessageFrame y) -> true;
    //
    //    registerShanghaiOperations(
    //            operationRegistry,
    //            gasCalculator,
    //            mirrorNodeEvmProperties.chainIdBytes32().toBigInteger());
    //    Set.of(
    //                    new HederaBalanceOperation(gasCalculator, validator),
    //                    new HederaDelegateCallOperation(gasCalculator, validator),
    //                    new HederaEvmChainIdOperation(gasCalculator, mirrorNodeEvmProperties),
    //                    new HederaEvmCreate2Operation(
    //                            gasCalculator, mirrorNodeEvmProperties, createOperationExternalizer()),
    //                    new HederaEvmCreateOperation(gasCalculator, createOperationExternalizer()),
    //                    new HederaEvmSLoadOperation(gasCalculator),
    //                    new HederaExtCodeCopyOperation(gasCalculator, validator),
    //                    new HederaExtCodeHashOperation(gasCalculator, validator),
    //                    new HederaExtCodeSizeOperation(gasCalculator, validator),
    //                    hederaBlockHashOperation)
    //            .forEach(operationRegistry::put);
    //
    //    return new EVM(
    //            operationRegistry,
    //            gasCalculator,
    //            org.hyperledger.besu.evm.internal.EvmConfiguration.DEFAULT,
    //            mirrorNodeEvmProperties.getEvmSpecVersion());
    // }

    @Bean
    HederaPrngSeedOperation hederaPrngSeedOperation(final GasCalculator gasCalculator, final PrngLogic prngLogic) {
        return new HederaPrngSeedOperation(gasCalculator, prngLogic);
    }

    @Bean
    ContractCreationProcessor contractCreationProcessor(final GasCalculatorHederaV22 gasCalculator, EVM evm) {
        return new ContractCreationProcessor(gasCalculator, evm, true, List.of(), 1);
    }

    @Bean
    PrecompileContractRegistry precompileContractRegistry() {
        return new PrecompileContractRegistry();
    }
}
