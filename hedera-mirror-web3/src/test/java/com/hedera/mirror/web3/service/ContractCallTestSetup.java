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

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.common.ContractCallContext.init;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.AUTO_RENEW_ACCOUNT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.DYNAMIC_ETH_CALLS_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.DYNAMIC_ETH_CALLS_CONTRACT_ALIAS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.ERC_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.ETH_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.ETH_CALL_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.EVM_CODES_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.EXCHANGE_RATE_ENTITY_ID;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FEE_SCHEDULE_ENTITY_ID;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FROZEN_FUNGIBLE_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN_ADDRESS_NOT_ASSOCIATED;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.FUNGIBLE_TOKEN_ADDRESS_WITH_EXPIRY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.KEY_PROTO;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.MODIFICATION_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NESTED_ETH_CALLS_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_TRANSFER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NFT_TRANSFER_ADDRESS_WITHOUT_KYC_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NOT_ASSOCIATED_SPENDER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NOT_ASSOCIATED_SPENDER_ALIAS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NOT_ASSOCIATED_SPENDER_PUBLIC_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.OWNER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.PRECOMPILE_TEST_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.PRNG_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.RECEIVER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.REDIRECT_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.REVERTER_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.SENDER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.SENDER_ALIAS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.SENDER_PUBLIC_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.SPENDER_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.SPENDER_ALIAS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.SPENDER_PUBLIC_KEY;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.STATE_CONTRACT_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.TRANSFRER_FROM_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.TREASURY_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.TREASURY_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.UNPAUSED_FUNGIBLE_TOKEN_ADDRESS;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.exchangeRatesSet;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.expiry;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.feeSchedules;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.keyWithContractId;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.keyWithDelegatableContractId;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.keyWithECDSASecp256K1;
import static com.hedera.mirror.web3.service.ContractCallTestConstants.keyWithEd25519;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.FallbackFee;
import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.common.domain.token.FractionalFee;
import com.hedera.mirror.common.domain.token.RoyaltyFee;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.utils.FunctionEncodeDecoder;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.hapi.utils.ByteStringUtils;
import com.hederahashgraph.api.proto.java.CustomFee.FeeCase;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class ContractCallTestSetup extends Web3IntegrationTest {

    //    protected static final long expiry = 1_234_567_890L;

    protected static RecordFile recordFileForBlockHash;
    protected static RecordFile genesisRecordFileForBlockHash;

    @Autowired
    protected MirrorEvmTxProcessor processor;

    @Autowired
    protected FunctionEncodeDecoder functionEncodeDecoder;

    @Autowired
    protected ContractCallService contractCallService;

    @Autowired
    protected MirrorNodeEvmProperties properties;
    // The contract source `PrecompileTestContract.sol` is in test resources
    @Value("classpath:contracts/PrecompileTestContract/PrecompileTestContract.bin")
    protected Path CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/DynamicEthCalls/DynamicEthCalls.bin")
    protected Path DYNAMIC_ETH_CALLS_BYTES_PATH;

    @Value("classpath:contracts/DynamicEthCalls/DynamicEthCalls.json")
    protected Path DYNAMIC_ETH_CALLS_ABI_PATH;

    @Value("classpath:contracts/PrecompileTestContract/PrecompileTestContract.json")
    protected Path PRECOMPILE_TEST_CONTRACT_ABI_PATH;

    @Value("classpath:contracts/RedirectTestContract/RedirectTestContract.json")
    protected Path REDIRECT_CONTRACT_ABI_PATH;

    @Value("classpath:contracts/RedirectTestContract/RedirectTestContract.bin")
    protected Path REDIRECT_CONTRACT_BYTES_PATH;

    // The contract source `ModificationPrecompileTestContract.sol` is in test resources
    @Value("classpath:contracts/ModificationPrecompileTestContract/ModificationPrecompileTestContract.bin")
    protected Path MODIFICATION_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/ModificationPrecompileTestContract/ModificationPrecompileTestContract.json")
    protected Path MODIFICATION_CONTRACT_ABI_PATH;

    // The contract source `ERCTestContract.sol` is in test resources
    @Value("classpath:contracts/ERCTestContract/ERCTestContract.bin")
    protected Path ERC_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/ERCTestContract/ERCTestContract.json")
    protected Path ERC_ABI_PATH;

    // The contract source `ExchangeRatePrecompile.sol` is in test resources
    @Value("classpath:contracts/ExchangeRatePrecompile/ExchangeRatePrecompile.bin")
    protected Path EXCHANGE_RATE_PRECOMPILE_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/ExchangeRatePrecompile/ExchangeRatePrecompile.json")
    protected Path EXCHANGE_RATE_PRECOMPILE_ABI_PATH;

    // The contract source `PrngSystemContract.sol` is in test resources
    @Value("classpath:contracts/PrngSystemContract/PrngSystemContract.bin")
    protected Path PRNG_PRECOMPILE_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/PrngSystemContract/PrngSystemContract.json")
    protected Path PRNG_PRECOMPILE_ABI_PATH;

    // The contract sources `EthCall.sol` and `Reverter.sol` are in test/resources
    @Value("classpath:contracts/EthCall/EthCall.bin")
    protected Path ETH_CALL_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/EthCall/EthCallInit.bin")
    protected Path ETH_CALL_INIT_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/Reverter/Reverter.bin")
    protected Path REVERTER_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/EthCall/State.bin")
    protected Path STATE_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/EvmCodes/EvmCodes.bin")
    protected Path EVM_CODES_BYTES_PATH;

    @Value("classpath:contracts/EvmCodes/EvmCodes.json")
    protected Path EVM_CODES_ABI_PATH;

    @Value("classpath:contracts/NestedCallsTestContract/NestedCallsTestContract.bin")
    protected Path NESTED_CALLS_CONTRACT_BYTES_PATH;

    @Value("classpath:contracts/NestedCallsTestContract/NestedCallsTestContract.json")
    protected Path NESTED_CALLS_ABI_PATH;

    /**
     * Checks if the *actual* gas usage is within 5-20% greater than the *expected* gas used from the initial call.
     *
     * @param actualGas   The actual gas used.
     * @param expectedGas The expected gas used from the initial call.
     * @return {@code true} if the actual gas usage is within the expected range, otherwise {@code false}.
     */
    protected static boolean isWithinExpectedGasRange(final long actualGas, final long expectedGas) {
        return actualGas >= (expectedGas * 1.05) && actualGas <= (expectedGas * 1.20);
    }

    protected CallServiceParameters serviceParametersForExecution(
            final Bytes callData,
            final Address contractAddress,
            final CallType callType,
            final long value,
            BlockType block) {
        final var sender = new HederaEvmAccount(SENDER_ADDRESS);
        persistEntities();

        return CallServiceParameters.builder()
                .sender(sender)
                .value(value)
                .receiver(contractAddress)
                .callData(callData)
                .gas(15_000_000L)
                .isStatic(false)
                .callType(callType)
                .isEstimate(ETH_ESTIMATE_GAS == callType)
                .block(block)
                .build();
    }

    protected CallServiceParameters serviceParametersForTopLevelContractCreate(
            final Path contractInitCodePath, final CallType callType, final Address senderAddress) {
        final var sender = new HederaEvmAccount(senderAddress);
        persistEntities();

        final var callData = Bytes.wrap(functionEncodeDecoder.getContractBytes(contractInitCodePath));
        return CallServiceParameters.builder()
                .sender(sender)
                .callData(callData)
                .receiver(Address.ZERO)
                .gas(15_000_000L)
                .isStatic(false)
                .callType(callType)
                .isEstimate(ETH_ESTIMATE_GAS == callType)
                .block(BlockType.LATEST)
                .build();
    }

    @SuppressWarnings("try")
    protected long gasUsedAfterExecution(final CallServiceParameters serviceParameters) {
        long result;
        try (ContractCallContext ctx = init()) {
            ctx.initializeStackFrames(store.getStackedStateFrames());
            result = processor
                    .execute(serviceParameters, serviceParameters.getGas())
                    .getGasUsed();

            assertThat(store.getStackedStateFrames().height()).isEqualTo(1);
        }

        return result;
    }

    protected void persistEntities() {
        genesisBlockPersist();
        evmCodesContractPersist();
        ethCallContractPersist();
        reverterContractPersist();
        stateContractPersist();
        precompileContractPersist();
        systemExchangeRateContractPersist();
        pseudoRandomNumberGeneratorContractPersist();
        final var modificationContract = modificationContractPersist();
        final var ercContract = ercContractPersist();
        final var nestedContractId = dynamicEthCallContractPresist();
        nestedEthCallsContractPersist();
        final var redirectContract = redirectContractPersist();
        fileDataPersist();

        receiverPersist();
        final var senderEntityId = senderEntityPersist();
        final var ownerEntityId = ownerEntityPersist();
        final var spenderEntityId = spenderEntityPersist();
        notAssociatedSpenderEntityPersist();
        final var treasuryEntityId = treasureEntityPersist();
        autoRenewAccountPersist();

        fungibleTokenPersist(
                ownerEntityId,
                KEY_PROTO,
                FUNGIBLE_TOKEN_ADDRESS_WITH_EXPIRY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                1000000000000L,
                TokenPauseStatusEnum.PAUSED,
                false);
        fungibleTokenPersist(
                senderEntityId,
                KEY_PROTO,
                UNPAUSED_FUNGIBLE_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        final var tokenEntityId = fungibleTokenPersist(
                ownerEntityId,
                KEY_PROTO,
                FUNGIBLE_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true);
        final var tokenEntityIdNotAssociated = fungibleTokenPersist(
                ownerEntityId,
                KEY_PROTO,
                FUNGIBLE_TOKEN_ADDRESS_NOT_ASSOCIATED,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true);
        final var notFrozenFungibleTokenEntityId = fungibleTokenPersist(
                treasuryEntityId,
                KEY_PROTO,
                NOT_FROZEN_FUNGIBLE_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                0L,
                TokenPauseStatusEnum.PAUSED,
                false);
        final var frozenFungibleTokenEntityId = fungibleTokenPersist(
                spenderEntityId,
                KEY_PROTO,
                FROZEN_FUNGIBLE_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                true);
        final var transferFromTokenTreasuryEntityId = fungibleTokenPersist(
                treasuryEntityId,
                new byte[0],
                TRANSFRER_FROM_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        final var tokenTreasuryEntityId = fungibleTokenPersist(
                treasuryEntityId,
                new byte[0],
                TREASURY_TOKEN_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        final var tokenGetKeyContractAddressEntityId = fungibleTokenPersist(
                senderEntityId,
                keyWithContractId.toByteArray(),
                FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                false);
        final var tokenGetKeyEcdsaEntityId = fungibleTokenPersist(
                senderEntityId,
                keyWithECDSASecp256K1.toByteArray(),
                FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ECDSA_KEY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                false);
        final var tokenGetKeyEd25519EntityId = fungibleTokenPersist(
                senderEntityId,
                keyWithEd25519.toByteArray(),
                FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_ED25519_KEY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                false);
        final var tokenGetKeyDelegatableContractIdEntityId = fungibleTokenPersist(
                senderEntityId,
                keyWithDelegatableContractId.toByteArray(),
                FUNGIBLE_TOKEN_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                9999999999999L,
                TokenPauseStatusEnum.PAUSED,
                false);

        final var nftEntityId = nftPersist(
                NFT_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.PAUSED,
                true);
        final var nftEntityId2 = nftPersist(
                NFT_ADDRESS_WITH_DIFFERENT_OWNER_AND_TREASURY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                senderEntityId,
                spenderEntityId,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        final var nftEntityId3 = nftPersist(
                NFT_TRANSFER_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                KEY_PROTO,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        final var nftEntityId4 = nftPersist(
                NFT_ADDRESS_GET_KEY_WITH_CONTRACT_ADDRESS,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                keyWithContractId.toByteArray(),
                TokenPauseStatusEnum.PAUSED,
                true);
        final var nftEntityId5 = nftPersist(
                NFT_ADDRESS_GET_KEY_WITH_ED25519_KEY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                keyWithEd25519.toByteArray(),
                TokenPauseStatusEnum.PAUSED,
                true);
        final var nftEntityId6 = nftPersist(
                NFT_ADDRESS_GET_KEY_WITH_ECDSA_KEY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                keyWithECDSASecp256K1.toByteArray(),
                TokenPauseStatusEnum.PAUSED,
                true);
        final var nftEntityId7 = nftPersist(
                NFT_ADDRESS_GET_KEY_WITH_DELEGATABLE_CONTRACT_ID,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                ownerEntityId,
                spenderEntityId,
                ownerEntityId,
                keyWithDelegatableContractId.toByteArray(),
                TokenPauseStatusEnum.PAUSED,
                true);
        final var nftEntityId8 = nftPersistWithoutKycKey(
                NFT_TRANSFER_ADDRESS_WITHOUT_KYC_KEY,
                AUTO_RENEW_ACCOUNT_ADDRESS,
                nestedContractId,
                spenderEntityId,
                nestedContractId,
                KEY_PROTO,
                TokenPauseStatusEnum.UNPAUSED,
                false);
        final var ethAccount = ethAccountPersist(358L, ETH_ADDRESS);

        tokenAccountPersist(senderEntityId, tokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(ethAccount, tokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(senderEntityId, transferFromTokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ethAccount, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, frozenFungibleTokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(ethAccount, frozenFungibleTokenEntityId, TokenFreezeStatusEnum.FROZEN);
        tokenAccountPersist(modificationContract, tokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(modificationContract, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ercContract, tokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ercContract, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(redirectContract, tokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(redirectContract, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);

        tokenAccountPersist(treasuryEntityId, notFrozenFungibleTokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(nestedContractId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(nestedContractId, tokenEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(nestedContractId, nftEntityId3, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(nestedContractId, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(nestedContractId, nftEntityId8, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ethAccount, transferFromTokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ethAccount, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, tokenGetKeyContractAddressEntityId, TokenFreezeStatusEnum.UNFROZEN);

        tokenAccountPersist(ownerEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, nftEntityId, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ownerEntityId, nftEntityId3, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, nftEntityId3, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ownerEntityId, nftEntityId2, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(senderEntityId, nftEntityId2, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(ownerEntityId, nftEntityId8, TokenFreezeStatusEnum.UNFROZEN);
        tokenAccountPersist(spenderEntityId, nftEntityId8, TokenFreezeStatusEnum.UNFROZEN);
        ercContractTokenPersist(ERC_CONTRACT_ADDRESS, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        ercContractTokenPersist(REDIRECT_CONTRACT_ADDRESS, tokenTreasuryEntityId, TokenFreezeStatusEnum.UNFROZEN);
        nftCustomFeePersist(senderEntityId, nftEntityId);

        allowancesPersist(senderEntityId, modificationContract, transferFromTokenTreasuryEntityId, nftEntityId);
        allowancesPersist(senderEntityId, spenderEntityId, tokenEntityId, nftEntityId);
        allowancesPersist(ownerEntityId, modificationContract, tokenEntityId, nftEntityId);
        allowancesPersist(ownerEntityId, nestedContractId, tokenEntityId, nftEntityId);
        allowancesPersist(ownerEntityId, ercContract, tokenEntityId, nftEntityId);
        allowancesPersist(ownerEntityId, redirectContract, tokenEntityId, nftEntityId);
        allowancesPersist(senderEntityId, spenderEntityId, tokenTreasuryEntityId, nftEntityId3);
        contractAllowancesPersist(senderEntityId, MODIFICATION_CONTRACT_ADDRESS, tokenTreasuryEntityId, nftEntityId3);
        contractAllowancesPersist(senderEntityId, ERC_CONTRACT_ADDRESS, tokenTreasuryEntityId, nftEntityId3);
        contractAllowancesPersist(senderEntityId, REDIRECT_CONTRACT_ADDRESS, tokenTreasuryEntityId, nftEntityId3);
        exchangeRatesPersist();
        feeSchedulesPersist();
    }

    private void genesisBlockPersist() {
        genesisRecordFileForBlockHash =
                domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
    }

    // Custom fees and rates persist
    protected void customFeePersist(final FeeCase feeCase) {
        final var collectorAccountId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());
        final var tokenEntityId = fromEvmAddress(FUNGIBLE_TOKEN_ADDRESS.toArrayUnsafe());
        switch (feeCase) {
            case ROYALTY_FEE -> {
                final var royaltyFee = RoyaltyFee.builder()
                        .collectorAccountId(collectorAccountId)
                        .denominator(10L)
                        .fallbackFee(FallbackFee.builder()
                                .amount(100L)
                                .denominatingTokenId(tokenEntityId)
                                .build())
                        .numerator(20L)
                        .build();
                domainBuilder
                        .customFee()
                        .customize(f -> f.royaltyFees(List.of(royaltyFee))
                                .fixedFees(List.of())
                                .fractionalFees(List.of())
                                .tokenId(tokenEntityId.getId()))
                        .persist();
            }
            case FRACTIONAL_FEE -> {
                final var fractionalFee = FractionalFee.builder()
                        .collectorAccountId(collectorAccountId)
                        .denominator(10L)
                        .minimumAmount(1L)
                        .maximumAmount(1000L)
                        .netOfTransfers(true)
                        .numerator(100L)
                        .build();
                domainBuilder
                        .customFee()
                        .customize(f -> f.fractionalFees(List.of(fractionalFee))
                                .fixedFees(List.of())
                                .royaltyFees(List.of())
                                .tokenId(tokenEntityId.getId()))
                        .persist();
            }
            case FIXED_FEE -> {
                final var fixedFee = FixedFee.builder()
                        .amount(100L)
                        .collectorAccountId(collectorAccountId)
                        .denominatingTokenId(tokenEntityId)
                        .build();
                domainBuilder
                        .customFee()
                        .customize(f -> f.fixedFees(List.of(fixedFee))
                                .fractionalFees(List.of())
                                .royaltyFees(List.of())
                                .tokenId(tokenEntityId.getId()))
                        .persist();
            }
            default -> domainBuilder
                    .customFee()
                    .customize(f -> f.tokenId(tokenEntityId.getId()))
                    .persist();
        }
    }

    protected void exchangeRatesPersist() {
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(exchangeRatesSet.toByteArray())
                        .entityId(EXCHANGE_RATE_ENTITY_ID)
                        .consensusTimestamp(expiry))
                .persist();
    }

    protected void feeSchedulesPersist() {
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(feeSchedules.toByteArray())
                        .entityId(FEE_SCHEDULE_ENTITY_ID)
                        .consensusTimestamp(expiry + 1))
                .persist();
    }

    private void nftCustomFeePersist(final EntityId senderEntityId, final EntityId nftEntityId) {
        domainBuilder
                .customFee()
                .customize(f -> f.tokenId(nftEntityId.getId())
                        .fractionalFees(List.of(FractionalFee.builder()
                                .collectorAccountId(senderEntityId)
                                .build()))
                        .royaltyFees(List.of())
                        .fixedFees(List.of()))
                .persist();
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

    // Account persist
    private void tokenAccountPersist(
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

    private void tokenAccountPersist(
            final long ethAccount, final EntityId tokenEntityId, final TokenFreezeStatusEnum freezeStatus) {
        domainBuilder
                .tokenAccount()
                .customize(e -> e.freezeStatus(freezeStatus)
                        .accountId(ethAccount)
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .balance(10L))
                .persist();
    }

    private void ercContractTokenPersist(
            final Address contractAddress, final EntityId tokenEntityId, final TokenFreezeStatusEnum freezeStatusEnum) {
        final var contractEntityId = fromEvmAddress(contractAddress.toArrayUnsafe());
        domainBuilder
                .tokenAccount()
                .customize(e -> e.freezeStatus(freezeStatusEnum)
                        .accountId(contractEntityId.getNum())
                        .tokenId(tokenEntityId.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .balance(10L))
                .persist();
    }

    // Entity persist
    @Nullable
    private EntityId notAssociatedSpenderEntityPersist() {
        final var spenderEntityId = fromEvmAddress(NOT_ASSOCIATED_SPENDER_ADDRESS.toArrayUnsafe());

        persistEntity(
                spenderEntityId,
                NOT_ASSOCIATED_SPENDER_ALIAS.toArray(),
                NOT_ASSOCIATED_SPENDER_PUBLIC_KEY.toByteArray(),
                ACCOUNT,
                0L,
                new byte[0]);

        return spenderEntityId;
    }

    @Nullable
    private EntityId spenderEntityPersist() {
        final var spenderEntityId = fromEvmAddress(SPENDER_ADDRESS.toArrayUnsafe());

        persistEntity(
                spenderEntityId, SPENDER_ALIAS.toArray(), SPENDER_PUBLIC_KEY.toByteArray(), ACCOUNT, 0L, new byte[0]);

        return spenderEntityId;
    }

    private long ethAccountPersist(final long ethAccount, final Address evmAddress) {
        persistEntity(
                EntityId.of(0, 0, ethAccount), evmAddress.toArrayUnsafe(), new byte[0], ACCOUNT, 2000L, new byte[0]);
        return ethAccount;
    }

    @Nullable
    private EntityId senderEntityPersist() {
        final var senderEntityId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());

        persistEntity(
                senderEntityId,
                SENDER_ALIAS.toArray(),
                SENDER_PUBLIC_KEY.toByteArray(),
                ACCOUNT,
                10000 * 100_000_000L,
                new byte[0]);
        return senderEntityId;
    }

    @Nullable
    private EntityId ownerEntityPersist() {
        final var ownerEntityId = fromEvmAddress(OWNER_ADDRESS.toArrayUnsafe());

        persistEntity(ownerEntityId, null, toEvmAddress(ownerEntityId), ACCOUNT, 20000L, new byte[0]);

        return ownerEntityId;
    }

    @Nullable
    private EntityId autoRenewAccountPersist() {
        final var autoRenewEntityId = fromEvmAddress(AUTO_RENEW_ACCOUNT_ADDRESS.toArrayUnsafe());

        persistEntity(autoRenewEntityId, null, toEvmAddress(autoRenewEntityId), ACCOUNT, 0L, new byte[0]);
        return autoRenewEntityId;
    }

    @Nullable
    private EntityId treasureEntityPersist() {
        final var treasuryEntityId = fromEvmAddress(TREASURY_ADDRESS.toArrayUnsafe());

        persistEntity(treasuryEntityId, null, toEvmAddress(treasuryEntityId), ACCOUNT, 0L, new byte[0]);

        return treasuryEntityId;
    }

    private void receiverPersist() {
        final var receiverEntityId = fromEvmAddress(RECEIVER_ADDRESS.toArrayUnsafe());
        final var receiverEvmAddress = toEvmAddress(receiverEntityId);
        persistEntity(receiverEntityId, receiverEvmAddress, new byte[0], CONTRACT, 0L, new byte[0]);
    }

    // Token persist
    private EntityId fungibleTokenPersist(
            final EntityId treasuryId,
            final byte[] key,
            final Address tokenAddress,
            final Address autoRenewAddress,
            final long tokenExpiration,
            final TokenPauseStatusEnum pauseStatus,
            final boolean freezeDefault) {
        final var tokenEntityId = fromEvmAddress(tokenAddress.toArrayUnsafe());
        final var autoRenewEntityId = fromEvmAddress(autoRenewAddress.toArrayUnsafe());
        final var tokenEvmAddress = toEvmAddress(tokenEntityId);

        persistEntityToken(tokenEntityId, autoRenewEntityId, tokenEvmAddress, 1500L, tokenExpiration, key);
        persistToken(
                tokenEntityId,
                treasuryId,
                key,
                TokenTypeEnum.FUNGIBLE_COMMON,
                TokenSupplyTypeEnum.INFINITE,
                pauseStatus,
                freezeDefault,
                12345L,
                2525L,
                true);

        return tokenEntityId;
    }

    @Nullable
    private EntityId nftPersist(
            final Address nftAddress,
            final Address autoRenewAddress,
            final EntityId ownerEntityId,
            final EntityId spenderEntityId,
            final EntityId treasuryId,
            final byte[] key,
            final TokenPauseStatusEnum pauseStatus,
            final boolean freezeDefault) {
        final var nftEntityId = fromEvmAddress(nftAddress.toArrayUnsafe());
        final var autoRenewEntityId = fromEvmAddress(autoRenewAddress.toArrayUnsafe());
        final var nftEvmAddress = toEvmAddress(nftEntityId);
        final var ownerEntity = EntityId.of(0, 0, ownerEntityId.getId());

        persistEntityToken(nftEntityId, autoRenewEntityId, nftEvmAddress, 1500L, 9999999999999L, key);
        persistToken(
                nftEntityId,
                treasuryId,
                key,
                TokenTypeEnum.NON_FUNGIBLE_UNIQUE,
                TokenSupplyTypeEnum.FINITE,
                pauseStatus,
                freezeDefault,
                1_000_000_000L,
                2000000000L,
                true);

        domainBuilder
                .nft()
                .customize(n -> n.accountId(spenderEntityId)
                        .createdTimestamp(1475067194949034022L)
                        .serialNumber(1)
                        .spender(spenderEntityId)
                        .metadata("NFT_METADATA_URI".getBytes())
                        .accountId(ownerEntity)
                        .timestampRange(Range.atLeast(1475067194949034022L))
                        .tokenId(nftEntityId.getId()))
                .persist();
        return nftEntityId;
    }

    @Nullable
    private EntityId nftPersistWithoutKycKey(
            final Address nftAddress,
            final Address autoRenewAddress,
            final EntityId ownerEntityId,
            final EntityId spenderEntityId,
            final EntityId treasuryId,
            final byte[] key,
            final TokenPauseStatusEnum pauseStatus,
            final boolean freezeDefault) {
        final var nftEntityId = fromEvmAddress(nftAddress.toArrayUnsafe());
        final var autoRenewEntityId = fromEvmAddress(autoRenewAddress.toArrayUnsafe());
        final var nftEvmAddress = toEvmAddress(nftEntityId);
        final var ownerEntity = EntityId.of(0, 0, ownerEntityId.getId());

        persistEntityToken(nftEntityId, autoRenewEntityId, nftEvmAddress, 1500L, 0L, key);
        persistToken(
                nftEntityId,
                treasuryId,
                key,
                TokenTypeEnum.NON_FUNGIBLE_UNIQUE,
                TokenSupplyTypeEnum.FINITE,
                pauseStatus,
                freezeDefault,
                1000L,
                2000000000L,
                false);

        domainBuilder
                .nft()
                .customize(n -> n.accountId(spenderEntityId)
                        .createdTimestamp(1475067194949034022L)
                        .serialNumber(1)
                        .spender(spenderEntityId)
                        .metadata("NFT_METADATA_URI".getBytes())
                        .accountId(ownerEntity)
                        .timestampRange(Range.atLeast(1475067194949034022L))
                        .tokenId(nftEntityId.getId()))
                .persist();
        return nftEntityId;
    }

    // Allowances persist
    private void allowancesPersist(
            final EntityId senderEntityId,
            final EntityId spenderEntityId,
            final EntityId tokenEntityId,
            final EntityId nftEntityId) {
        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenEntityId.getId())
                        .payerAccountId(senderEntityId)
                        .owner(senderEntityId.getNum())
                        .spender(spenderEntityId.getNum())
                        .amount(13))
                .persist();

        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(nftEntityId.getId())
                        .spender(spenderEntityId.getNum())
                        .owner(senderEntityId.getNum())
                        .approvedForAll(true)
                        .payerAccountId(senderEntityId))
                .persist();
    }

    private void contractAllowancesPersist(
            final EntityId senderEntityId,
            final Address contractAddress,
            final EntityId tokenEntityId,
            final EntityId nftEntityId) {
        final var contractId = fromEvmAddress(contractAddress.toArrayUnsafe());
        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenEntityId.getId())
                        .payerAccountId(senderEntityId)
                        .owner(senderEntityId.getNum())
                        .spender(contractId.getNum())
                        .amount(20))
                .persist();

        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(nftEntityId.getId())
                        .owner(senderEntityId.getNum())
                        .spender(contractId.getNum())
                        .approvedForAll(true)
                        .payerAccountId(senderEntityId))
                .persist();
    }

    // Contracts persist
    private void evmCodesContractPersist() {
        final var evmCodesContractBytes = functionEncodeDecoder.getContractBytes(EVM_CODES_BYTES_PATH);
        final var evmCodesContractEntityId = fromEvmAddress(EVM_CODES_CONTRACT_ADDRESS.toArrayUnsafe());
        final var evmCodesContractEvmAddress = toEvmAddress(evmCodesContractEntityId);

        persistEntity(evmCodesContractEntityId, evmCodesContractEvmAddress, new byte[0], CONTRACT, 1500L, new byte[0]);
        persistContract(evmCodesContractEntityId, evmCodesContractBytes);
        persistRecordFile(evmCodesContractBytes);
    }

    private void ethCallContractPersist() {
        final var ethCallContractBytes = functionEncodeDecoder.getContractBytes(ETH_CALL_CONTRACT_BYTES_PATH);
        final var ethCallContractEntityId = fromEvmAddress(ETH_CALL_CONTRACT_ADDRESS.toArrayUnsafe());
        final var ethCallContractEvmAddress = toEvmAddress(ethCallContractEntityId);

        persistEntity(ethCallContractEntityId, ethCallContractEvmAddress, new byte[0], CONTRACT, 1500L, new byte[0]);
        persistContract(ethCallContractEntityId, ethCallContractBytes);
        persistContractState(
                ethCallContractEntityId,
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "0x4746573740000000000000000000000000000000000000000000000000000000");
        persistRecordFile(ethCallContractBytes);
    }

    private void reverterContractPersist() {
        final var reverterContractEntityId = fromEvmAddress(REVERTER_CONTRACT_ADDRESS.toArrayUnsafe());
        final var reverterContractEvmAddress = toEvmAddress(reverterContractEntityId);
        final var reverterContractBytes = functionEncodeDecoder.getContractBytes(REVERTER_CONTRACT_BYTES_PATH);

        persistEntity(reverterContractEntityId, reverterContractEvmAddress, new byte[0], CONTRACT, 1500L, new byte[0]);
        persistContract(reverterContractEntityId, reverterContractBytes);
    }

    private void stateContractPersist() {
        final var stateContractId = fromEvmAddress(STATE_CONTRACT_ADDRESS.toArrayUnsafe());
        final var stateContractAddress = toEvmAddress(stateContractId);
        final var stateContractBytes = functionEncodeDecoder.getContractBytes(STATE_CONTRACT_BYTES_PATH);

        persistEntity(stateContractId, stateContractAddress, new byte[0], CONTRACT, 1500L, new byte[0]);
        persistContract(stateContractId, stateContractBytes);
    }

    private EntityId dynamicEthCallContractPresist() {
        final var contractBytes = functionEncodeDecoder.getContractBytes(DYNAMIC_ETH_CALLS_BYTES_PATH);
        final var contractEntityId = fromEvmAddress(DYNAMIC_ETH_CALLS_CONTRACT_ADDRESS.toArrayUnsafe());

        persistEntity(
                contractEntityId,
                DYNAMIC_ETH_CALLS_CONTRACT_ALIAS.toArray(),
                ByteStringUtils.wrapUnsafely(SENDER_ALIAS.toArrayUnsafe()).toByteArray(),
                CONTRACT,
                1500L,
                new byte[0]);
        persistContract(contractEntityId, contractBytes);
        persistContractState(
                contractEntityId,
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "0x4746573740000000000000000000000000000000000000000000000000000000");
        persistRecordFile(contractBytes);

        return contractEntityId;
    }

    private void precompileContractPersist() {
        final var contractBytes = functionEncodeDecoder.getContractBytes(CONTRACT_BYTES_PATH);
        final var contractEntityId = fromEvmAddress(PRECOMPILE_TEST_CONTRACT_ADDRESS.toArrayUnsafe());
        final var contractEvmAddress = toEvmAddress(contractEntityId);

        persistEntity(contractEntityId, contractEvmAddress, new byte[0], CONTRACT, 1500L, new byte[0]);
        persistContract(contractEntityId, contractBytes);
        persistContractState(
                contractEntityId,
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "0x4746573740000000000000000000000000000000000000000000000000000000");
        persistRecordFile(contractBytes);
    }

    private EntityId modificationContractPersist() {
        final var modificationContractBytes = functionEncodeDecoder.getContractBytes(MODIFICATION_CONTRACT_BYTES_PATH);
        final var modificationContractEntityId = fromEvmAddress(MODIFICATION_CONTRACT_ADDRESS.toArrayUnsafe());
        final var modificationContractEvmAddress = toEvmAddress(modificationContractEntityId);

        persistEntity(
                modificationContractEntityId,
                modificationContractEvmAddress,
                new byte[0],
                CONTRACT,
                1500L,
                new byte[0]);
        persistContract(modificationContractEntityId, modificationContractBytes);

        return modificationContractEntityId;
    }

    private EntityId ercContractPersist() {
        final var ercContractBytes = functionEncodeDecoder.getContractBytes(ERC_CONTRACT_BYTES_PATH);
        final var ercContractEntityId = fromEvmAddress(ERC_CONTRACT_ADDRESS.toArrayUnsafe());
        final var ercContractEvmAddress = toEvmAddress(ercContractEntityId);

        persistEntity(ercContractEntityId, ercContractEvmAddress, new byte[0], CONTRACT, 1500L, new byte[0]);
        persistContract(ercContractEntityId, ercContractBytes);
        persistContractState(
                ercContractEntityId,
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "0x4746573740000000000000000000000000000000000000000000000000000000");
        persistRecordFile(ercContractBytes);

        return ercContractEntityId;
    }

    private EntityId redirectContractPersist() {
        final var redirectContractBytes = functionEncodeDecoder.getContractBytes(REDIRECT_CONTRACT_BYTES_PATH);
        final var redirectContractEntityId = fromEvmAddress(REDIRECT_CONTRACT_ADDRESS.toArrayUnsafe());
        final var redirectContractEvmAddress = toEvmAddress(redirectContractEntityId);

        persistEntity(redirectContractEntityId, redirectContractEvmAddress, new byte[0], CONTRACT, 1500L, new byte[0]);
        persistContract(redirectContractEntityId, redirectContractBytes);
        persistContractState(
                redirectContractEntityId,
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "0x4746573740000000000000000000000000000000000000000000000000000000");
        persistRecordFile(redirectContractBytes);

        return redirectContractEntityId;
    }

    private EntityId pseudoRandomNumberGeneratorContractPersist() {
        final var randomNumberContractBytes =
                functionEncodeDecoder.getContractBytes(PRNG_PRECOMPILE_CONTRACT_BYTES_PATH);
        final var randomNumberContractEntityId = fromEvmAddress(PRNG_CONTRACT_ADDRESS.toArrayUnsafe());
        final var randomNumberContractEvmAddress = toEvmAddress(randomNumberContractEntityId);

        persistEntity(
                randomNumberContractEntityId,
                randomNumberContractEvmAddress,
                new byte[0],
                CONTRACT,
                1500L,
                new byte[0]);
        persistContract(randomNumberContractEntityId, randomNumberContractBytes);
        persistContractState(
                randomNumberContractEntityId,
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "0x4746573740000000000000000000000000000000000000000000000000000000");
        persistRecordFile(randomNumberContractBytes);

        return randomNumberContractEntityId;
    }

    private void nestedEthCallsContractPersist() {
        final var contractBytes = functionEncodeDecoder.getContractBytes(NESTED_CALLS_CONTRACT_BYTES_PATH);
        final var contractEntityId = fromEvmAddress(NESTED_ETH_CALLS_CONTRACT_ADDRESS.toArrayUnsafe());
        final var contractEvmAddress = toEvmAddress(contractEntityId);

        persistEntity(
                contractEntityId,
                contractEvmAddress,
                new byte[0],
                CONTRACT,
                1500L,
                Key.newBuilder()
                        .setEd25519(ByteString.copyFrom(Arrays.copyOfRange(KEY_PROTO, 3, KEY_PROTO.length)))
                        .build()
                        .toByteArray());
        persistContract(contractEntityId, contractBytes);
        persistContractState(
                contractEntityId,
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "0x4746573740000000000000000000000000000000000000000000000000000000");

        recordFileForBlockHash = persistRecordFile(contractBytes);
    }

    private EntityId systemExchangeRateContractPersist() {
        final var exchangeRateContractBytes =
                functionEncodeDecoder.getContractBytes(EXCHANGE_RATE_PRECOMPILE_CONTRACT_BYTES_PATH);
        final var exchangeRateContractEntityId =
                fromEvmAddress(EXCHANGE_RATE_PRECOMPILE_CONTRACT_ADDRESS.toArrayUnsafe());
        final var exchangeRteContractEvmAddress = toEvmAddress(exchangeRateContractEntityId);

        persistEntity(
                exchangeRateContractEntityId, exchangeRteContractEvmAddress, new byte[0], CONTRACT, 1500L, new byte[0]);
        persistContract(exchangeRateContractEntityId, exchangeRateContractBytes);
        persistContractState(
                exchangeRateContractEntityId,
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "0x4746573740000000000000000000000000000000000000000000000000000000");
        persistRecordFile(exchangeRateContractBytes);

        return exchangeRateContractEntityId;
    }

    private void persistEntity(
            final EntityId entityId, byte[] evmAddress, byte[] alias, EntityType type, long balance, byte[] key) {
        final var entity = domainBuilder.entity().customize(e -> e.id(entityId.getId())
                .num(entityId.getNum())
                .type(type)
                .deleted(false)
                .balance(balance));

        if (evmAddress == null || evmAddress.length > 0) {
            entity.customize(e -> e.evmAddress(evmAddress));
        }

        if (alias.length > 0) {
            entity.customize(e -> e.alias(alias));
        }

        if (key.length > 0) {
            entity.customize(e -> e.key(key));
        }

        entity.persist();
    }

    private void persistEntityToken(
            final EntityId entityId,
            final EntityId autoRenewAccountId,
            final byte[] evmAddress,
            final long balance,
            final long expiration,
            final byte[] key) {
        final var entityToken = domainBuilder.entity().customize(e -> e.id(entityId.getId())
                .autoRenewAccountId(autoRenewAccountId.getId())
                .num(entityId.getNum())
                .evmAddress(evmAddress)
                .type(TOKEN)
                .balance(balance)
                .key(key)
                .memo("TestMemo"));

        if (expiration > 0) {
            entityToken.customize(e -> e.expirationTimestamp(expiration));
        }

        entityToken.persist();
    }

    private void persistToken(
            final EntityId tokenId,
            final EntityId treasuryId,
            final byte[] key,
            final TokenTypeEnum tokenType,
            final TokenSupplyTypeEnum tokenSupplyType,
            final TokenPauseStatusEnum pauseStatus,
            final boolean freezeDefault,
            final long totalSupply,
            final long maxSupply,
            final boolean setKycKey) {
        final var token = domainBuilder.token().customize(t -> t.tokenId(tokenId.getId())
                .treasuryAccountId(treasuryId)
                .type(tokenType)
                .freezeDefault(freezeDefault)
                .feeScheduleKey(key)
                .maxSupply(maxSupply)
                .totalSupply(totalSupply)
                .name("Hbars")
                .supplyType(tokenSupplyType)
                .freezeKey(key)
                .pauseKey(key)
                .pauseStatus(pauseStatus)
                .wipeKey(key)
                .supplyKey(key)
                .symbol("HBAR")
                .wipeKey(key));

        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            token.customize(t -> t.decimals(12));
        }

        if (setKycKey) {
            token.customize(t -> t.kycKey(key));
        } else {
            token.customize(t -> t.kycKey(null));
        }

        token.persist();
    }

    private void persistContractState(final EntityId contractId, final String slot, final String value) {
        domainBuilder
                .contractState()
                .customize(c -> c.contractId(contractId.getId())
                        .slot(Bytes.fromHexString(slot).toArrayUnsafe())
                        .value(Bytes.fromHexString(value).toArrayUnsafe()))
                .persist();
    }

    private void persistContract(final EntityId contractId, final byte[] contractBytes) {
        domainBuilder
                .contract()
                .customize(c -> c.id(contractId.getId()).runtimeBytecode(contractBytes))
                .persist();
    }

    private RecordFile persistRecordFile(final byte[] contractBytes) {
        return domainBuilder.recordFile().customize(f -> f.bytes(contractBytes)).persist();
    }
}
