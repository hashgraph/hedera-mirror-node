/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_NFT;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.account;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungible;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nonFungible;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nonFungibleWipe;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.impl.WipeNonFungiblePrecompile.decodeWipeNFT;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.store.contracts.precompile.impl.WipeNonFungiblePrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenModificationResult;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txn.token.WipeLogic;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WipeNonFungiblePrecompileTest {

    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;
    private static final Bytes NON_FUNGIBLE_WIPE_INPUT = Bytes.fromHexString(
            "0xf7f38e2600000000000000000000000000000000000000000000000000000000000006b000000000000000000000000000000000000000000000000000000000000006ae000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000001");
    private final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_WIPE_TOKEN_ACCOUNT_NFT));
    private final TransactionBody.Builder transactionBody = TransactionBody.newBuilder()
            .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder().setToken(nonFungible));

    @Mock
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private MessageFrame frame;

    @Mock
    private TransactionBody.Builder mockSynthBodyBuilder;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private FeeObject mockFeeObject;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private EvmInfrastructureFactory infrastructureFactory;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private ExchangeRate exchangeRate;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    @Mock
    private Store store;

    @Mock
    private HederaEvmContractAliases hederaEvmContractAliases;

    @Mock
    private Token token;

    @Mock
    private Token updatedToken;

    @Mock
    private TokenRelationship tokenRelationship;

    @Mock
    private com.hedera.services.store.models.Account updatedAccount;

    @Mock
    private EntityAddressSequencer entityAddressSequencer;

    @Mock
    private MirrorEvmContractAliases mirrorEvmContractAliases;

    @Mock
    private TokenModificationResult tokenModificationResult;

    private HTSPrecompiledContract subject;
    private MockedStatic<WipeNonFungiblePrecompile> wipeNonFungiblePrecompile;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(
                HederaFunctionality.TokenAccountWipe, Map.of(SubType.TOKEN_NON_FUNGIBLE_UNIQUE, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);
        WipeLogic wipeLogic = new WipeLogic(evmProperties);
        final var wipePrecompile =
                new WipeNonFungiblePrecompile(precompilePricingUtils, syntheticTxnFactory, wipeLogic);
        PrecompileMapper precompileMapper = new PrecompileMapper(Set.of(wipePrecompile));
        subject = new HTSPrecompiledContract(
                infrastructureFactory,
                evmProperties,
                precompileMapper,
                evmHTSPrecompiledContract,
                entityAddressSequencer,
                mirrorEvmContractAliases);

        wipeNonFungiblePrecompile = Mockito.mockStatic(WipeNonFungiblePrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        wipeNonFungiblePrecompile.close();
    }

    @Test
    void nftWipeHappyPathWorks() {
        givenNonfungibleFrameContext();
        givenPricingUtilsContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder()
                        .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                                .setToken(fungible)
                                .setAccount(account))
                        .build());
        given(feeCalculator.computeFee(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile()
                .getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, store, hederaEvmContractAliases);
        final var result = subject.computeInternal(frame);

        assertEquals(successResult, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForWipeNonFungibleToken() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_WIPE_TOKEN_ACCOUNT_NFT));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        wipeNonFungiblePrecompile
                .when(() -> decodeWipeNFT(eq(pretendArguments), any()))
                .thenReturn(nonFungibleWipe);
        given(syntheticTxnFactory.createWipe(nonFungibleWipe))
                .willReturn(TransactionBody.newBuilder().setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()));
        given(feeCalculator.computeFee(any(), any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final long result = subject.getPrecompile()
                .getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, store, hederaEvmContractAliases);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeNonFungibleWipeInput() {
        wipeNonFungiblePrecompile
                .when(() -> decodeWipeNFT(NON_FUNGIBLE_WIPE_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeWipeNFT(NON_FUNGIBLE_WIPE_INPUT, identity());

        assertTrue(decodedInput.token().getTokenNum() > 0);
        assertTrue(decodedInput.account().getAccountNum() > 0);
        assertEquals(-1, decodedInput.amount());
        assertEquals(1, decodedInput.serialNumbers().size());
        assertEquals(1, decodedInput.serialNumbers().get(0));
        assertEquals(NON_FUNGIBLE_UNIQUE, decodedInput.type());
    }

    private void givenNonfungibleFrameContext() {
        givenFrameContext();
        wipeNonFungiblePrecompile
                .when(() -> decodeWipeNFT(eq(pretendArguments), any()))
                .thenReturn(nonFungibleWipe);
        given(syntheticTxnFactory.createWipe(nonFungibleWipe)).willReturn(mockSynthBodyBuilder);
    }

    private void givenFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.getStore()).willReturn(store);
        given(store.getToken(fungibleTokenAddr, Store.OnMissing.THROW)).willReturn(token);
        given(store.loadUniqueTokens(any(), anyList())).willReturn(token);
        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        given(token.wipe(any(), anyList())).willReturn(tokenModificationResult);
        given(tokenModificationResult.token()).willReturn(updatedToken);
        given(tokenModificationResult.tokenRelationship()).willReturn(tokenRelationship);
        given(tokenRelationship.getAccount()).willReturn(updatedAccount);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
    }

    private void givenMinFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
    }
}
