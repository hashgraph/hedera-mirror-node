/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.common.PrecompileContext.PRECOMPILE_CONTEXT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_UNFREEZE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.*;
import static com.hedera.services.store.contracts.precompile.impl.UnfreezeTokenPrecompile.decodeUnfreeze;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.mirror.web3.common.PrecompileContext;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.store.contracts.precompile.impl.UnfreezeTokenPrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txn.token.UnfreezeLogic;
import com.hedera.services.utils.IdUtils;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Deque;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnfreezeTokenPrecompileTest {
    @InjectMocks
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private MessageFrame frame;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private ExchangeRate exchangeRate;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private UnfreezeLogic unfreezeLogic;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private EvmInfrastructureFactory infrastructureFactory;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private Store store;

    @Mock
    private MessageFrame lastFrame;

    @Mock
    private Deque<MessageFrame> stack;

    @Mock
    private TokenAccessor tokenAccessor;

    private PrecompileContext precompileContext;

    private UnfreezeTokenPrecompile unfreezeTokenPrecompile;

    private HTSPrecompiledContract subject;
    private MockedStatic<UnfreezeTokenPrecompile> staticUnfreezeTokenPrecompile;

    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;
    public static final Bytes UNFREEZE_INPUT = Bytes.fromHexString(
            "0x52f9138700000000000000000000000000000000000000000000000000000000000005180000000000000000000000000000000000000000000000000000000000000516");

    private final TransactionBody.Builder transactionBody =
            TransactionBody.newBuilder().setTokenUnfreeze(TokenUnfreezeAccountTransactionBody.newBuilder());

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(HederaFunctionality.TokenUnfreezeAccount, Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        unfreezeTokenPrecompile =
                new UnfreezeTokenPrecompile(precompilePricingUtils, syntheticTxnFactory, unfreezeLogic);
        PrecompileMapper precompileMapper = new PrecompileMapper(Set.of(unfreezeTokenPrecompile));
        staticUnfreezeTokenPrecompile = Mockito.mockStatic(UnfreezeTokenPrecompile.class);
        precompileContext = new PrecompileContext();
        precompileContext.setEstimate(false);
        precompileContext.setPrecompile(unfreezeTokenPrecompile);
        precompileContext.setGasRequirement(0L);
        precompileContext.setSenderAddress(senderAddress);
        precompileContext.setTransactionBody(transactionBody);

        subject = new HTSPrecompiledContract(
                infrastructureFactory, evmProperties, precompileMapper, store, tokenAccessor, precompilePricingUtils);
    }

    @AfterEach
    void closeMocks() {
        staticUnfreezeTokenPrecompile.close();
    }

    @Test
    void computeCallsSuccessfullyForUnFreezeFungibleToken() {
        // given
        final var input = Bytes.of(Integers.toBytes(ABI_ID_UNFREEZE));
        givenFrameContext();
        givenMinimalContextForSuccessfulCall();
        givenFreezeUnfreezeContext();
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);

        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a, precompileContext);
        final var result = subject.computeInternal(frame);

        // then
        assertEquals(successResult, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForUnfreezeToken() {
        // given
        final var input = Bytes.of(Integers.toBytes(ABI_ID_UNFREEZE));
        givenMinimalFrameContext();
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        givenPricingUtilsContext();
        given(feeCalculator.computeFee(any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);

        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a, precompileContext);
        final var result = subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, sender);
        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeTokenUnFreezeWithValidInput() {
        staticUnfreezeTokenPrecompile
                .when(() -> decodeUnfreeze(UNFREEZE_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeUnfreeze(UNFREEZE_INPUT, identity());

        assertEquals(TokenID.newBuilder().setTokenNum(1304).build(), decodedInput.token());
    }

    private void givenMinimalFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
    }

    private void givenFrameContext() {
        givenMinimalFrameContext();
        given(frame.getRemainingGas()).willReturn(30000000L);
        given(frame.getValue()).willReturn(Wei.ZERO);
    }

    private void givenMinimalContextForSuccessfulCall() {
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.getStore()).willReturn(store);
    }

    private void givenFreezeUnfreezeContext() {
        given(unfreezeLogic.validate(any())).willReturn(OK);
        staticUnfreezeTokenPrecompile.when(() -> decodeUnfreeze(any(), any())).thenReturn(tokenFreezeUnFreezeWrapper);
        given(syntheticTxnFactory.createUnfreeze(tokenFreezeUnFreezeWrapper))
                .willReturn(TransactionBody.newBuilder()
                        .setTokenUnfreeze(TokenUnfreezeAccountTransactionBody.newBuilder()
                                .setToken(IdUtils.asToken("1.2.3"))
                                .setAccount(IdUtils.asAccount("1.2.4"))));
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
    }
}
