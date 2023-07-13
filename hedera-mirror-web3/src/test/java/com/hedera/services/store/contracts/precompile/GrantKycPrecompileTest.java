/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.impl.GrantKycPrecompile.decodeGrantTokenKyc;
import static java.util.function.UnaryOperator.identity;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.store.contracts.precompile.impl.GrantKycPrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txn.token.GrantKycLogic;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GrantKycPrecompileTest {

    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;
    public static final Bytes GRANT_TOKEN_KYC_INPUT = Bytes.fromHexString(
            "0x8f8d7f9900000000000000000000000000000000000000000000000000000000000004b200000000000000000000000000000000000000000000000000000000000004b0");

    private final TransactionBody.Builder transactionBody =
            TransactionBody.newBuilder().setTokenGrantKyc(TokenGrantKycTransactionBody.newBuilder());

    @Mock
    private MessageFrame frame;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private ExchangeRate exchangeRate;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private FeeObject mockFeeObject;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    @Mock
    private Store store;

    @Mock
    private TokenRelationship tokenRelationship;

    @Mock
    private EvmInfrastructureFactory infrastructureFactory;

    private HTSPrecompiledContract subject;
    private GrantKycLogic grantKycLogic;
    private GrantKycPrecompile grantKycPrecompile;
    private PrecompileMapper precompileMapper;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(HederaFunctionality.TokenGrantKycToAccount, Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);

        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        syntheticTxnFactory = new SyntheticTxnFactory();
        grantKycLogic = new GrantKycLogic();
        grantKycPrecompile = new GrantKycPrecompile(grantKycLogic, syntheticTxnFactory, precompilePricingUtils);
        precompileMapper = new PrecompileMapper(Set.of(grantKycPrecompile));

        subject = new HTSPrecompiledContract(
                infrastructureFactory, evmProperties, precompileMapper, evmHTSPrecompiledContract);
    }

    @Test
    void GrantKyc() {
        // given
        givenFrameContext();
        givenPricingUtilsContext();

        given(worldUpdater.getStore()).willReturn(store);
        given(store.getTokenRelationship(any(), any())).willReturn(tokenRelationship);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        // when
        subject.prepareFields(frame);
        subject.prepareComputation(GRANT_TOKEN_KYC_INPUT, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME, transactionBody, store);
        final var result = subject.computeInternal(frame);

        // then
        Assertions.assertEquals(successResult, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForGrantKyc() {
        // given
        givenMinimalFrameContext();
        givenPricingUtilsContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);

        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(GRANT_TOKEN_KYC_INPUT, a -> a);
        final var result =
                subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME, transactionBody, store);
        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeGrantKycToken() {
        final var decodedInput = decodeGrantTokenKyc(GRANT_TOKEN_KYC_INPUT, identity());

        assertTrue(decodedInput.account().getAccountNum() > 0);
        assertTrue(decodedInput.token().getTokenNum() > 0);
    }

    private void givenMinimalFrameContext() {
        given(frame.getSenderAddress()).willReturn(HTSTestsUtil.contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
    }

    private void givenFrameContext() {
        givenMinimalFrameContext();
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
    }
}
