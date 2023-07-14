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

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.account;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungible;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleWipe;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleWipeMaxAmount;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.impl.WipeFungiblePrecompile.getWipeWrapper;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.store.contracts.precompile.impl.SystemContractAbis;
import com.hedera.services.store.contracts.precompile.impl.WipeFungiblePrecompile;
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
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WipeFungiblePrecompileTest {

    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;
    private static final Bytes FUNGIBLE_WIPE_INPUT = Bytes.fromHexString(
            "0x9790686d00000000000000000000000000000000000000000000000000000000000006aa00000000000000000000000000000000000000000000000000000000000006a8000000000000000000000000000000000000000000000000000000000000000a");
    private static final Bytes FUNGIBLE_WIPE_INPUT_V2 = Bytes.fromHexString(
            "0xefef57f900000000000000000000000000000000000000000000000000000000000006aa00000000000000000000000000000000000000000000000000000000000006a8000000000000000000000000000000000000000000000000000000000000000a");
    private final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE));
    private final TransactionBody.Builder transactionBody = TransactionBody.newBuilder()
            .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder().setToken(fungible));

    @Mock
    private Account acc;

    @Mock
    private Deque<MessageFrame> stack;

    @Mock
    private Iterator<MessageFrame> dequeIterator;

    @Mock
    private MessageFrame frame;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private TransactionBody.Builder mockSynthBodyBuilder;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

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
    private TokenModificationResult tokenModificationResult;

    @InjectMocks
    private MirrorNodeEvmProperties evmProperties;

    private HTSPrecompiledContract subject;
    private MockedStatic<WipeFungiblePrecompile> wipeFungiblePrecompile;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(
                HederaFunctionality.TokenAccountWipe, Map.of(SubType.TOKEN_FUNGIBLE_COMMON, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        WipeLogic wipeLogic = new WipeLogic(evmProperties);
        final var wipePrecompile = new WipeFungiblePrecompile(precompilePricingUtils, syntheticTxnFactory, wipeLogic);
        PrecompileMapper precompileMapper = new PrecompileMapper(Set.of(wipePrecompile));

        subject = new HTSPrecompiledContract(
                infrastructureFactory, evmProperties, precompileMapper, evmHTSPrecompiledContract);

        wipeFungiblePrecompile = Mockito.mockStatic(WipeFungiblePrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        if (!wipeFungiblePrecompile.isClosed()) {
            wipeFungiblePrecompile.close();
        }
    }

    @Test
    void fungibleWipeHappyPathWorks() {
        givenFungibleFrameContext();
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
    void fungibleWipeMissedSpecializedAccessorCausePrecompileFailure() {
        // given:
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);

        wipeFungiblePrecompile
                .when(() -> getWipeWrapper(eq(pretendArguments), any(), eq(SystemContractAbis.WIPE_TOKEN_ACCOUNT_V1)))
                .thenReturn(fungibleWipeMaxAmount);
        given(syntheticTxnFactory.createWipe(fungibleWipeMaxAmount)).willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(frame.getBlockValues()).willReturn(new HederaBlockValues(10L, 123L, Instant.ofEpochSecond(123L)));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenThrow(new IllegalArgumentException("error"));
        givenIfDelegateCall();

        // then:
        assertThrows(IllegalArgumentException.class, () -> subject.computePrecompile(pretendArguments, frame));
    }

    @Test
    void fungibleWipeForMaxAmountWorks() {
        givenFrameContext();
        givenPricingUtilsContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        wipeFungiblePrecompile
                .when(() -> getWipeWrapper(eq(pretendArguments), any(), eq(SystemContractAbis.WIPE_TOKEN_ACCOUNT_V1)))
                .thenReturn(fungibleWipeMaxAmount);
        given(syntheticTxnFactory.createWipe(fungibleWipeMaxAmount)).willReturn(mockSynthBodyBuilder);
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
    void gasRequirementReturnsCorrectValueForWipeFungibleToken() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        wipeFungiblePrecompile
                .when(() -> getWipeWrapper(eq(pretendArguments), any(), eq(SystemContractAbis.WIPE_TOKEN_ACCOUNT_V1)))
                .thenReturn(fungibleWipe);
        given(syntheticTxnFactory.createWipe(fungibleWipe))
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
    void decodeFungibleWipeInput() {
        wipeFungiblePrecompile.close();
        final var decodedInput =
                getWipeWrapper(FUNGIBLE_WIPE_INPUT, identity(), SystemContractAbis.WIPE_TOKEN_ACCOUNT_V1);

        assertTrue(decodedInput.token().getTokenNum() > 0);
        assertTrue(decodedInput.account().getAccountNum() > 0);
        assertEquals(10, decodedInput.amount());
        assertEquals(0, decodedInput.serialNumbers().size());
        assertEquals(FUNGIBLE_COMMON, decodedInput.type());
    }

    @Test
    void decodeFungibleWipeInputV2() {
        wipeFungiblePrecompile.close();
        final var decodedInput =
                getWipeWrapper(FUNGIBLE_WIPE_INPUT_V2, identity(), SystemContractAbis.WIPE_TOKEN_ACCOUNT_V2);

        assertTrue(decodedInput.token().getTokenNum() > 0);
        assertTrue(decodedInput.account().getAccountNum() > 0);
        assertEquals(10, decodedInput.amount());
        assertEquals(0, decodedInput.serialNumbers().size());
        assertEquals(FUNGIBLE_COMMON, decodedInput.type());
    }

    private void givenFungibleFrameContext() {
        givenFrameContext();
        wipeFungiblePrecompile
                .when(() -> getWipeWrapper(eq(pretendArguments), any(), eq(SystemContractAbis.WIPE_TOKEN_ACCOUNT_V1)))
                .thenReturn(fungibleWipe);
        given(syntheticTxnFactory.createWipe(fungibleWipe)).willReturn(mockSynthBodyBuilder);
    }

    private void givenFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(30000L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.getStore()).willReturn(store);
        given(store.getToken(fungibleTokenAddr, OnMissing.THROW)).willReturn(token);
        given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);
        given(token.wipe(any(), anyLong())).willReturn(tokenModificationResult);
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

    private void givenIfDelegateCall() {
        given(frame.getContractAddress()).willReturn(contractAddress);
        given(frame.getRecipientAddress()).willReturn(recipientAddress);
        given(worldUpdater.get(recipientAddress)).willReturn(acc);
        given(acc.getNonce()).willReturn(-1L);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(frame.getMessageFrameStack().iterator()).willReturn(dequeIterator);
        given(frame.getBlockValues()).willReturn(new HederaBlockValues(10L, 123L, Instant.ofEpochSecond(123L)));
    }
}
