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

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_DISSOCIATE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_DISSOCIATE_TOKENS;
import static com.hedera.services.store.contracts.precompile.impl.DissociatePrecompile.decodeDissociate;
import static com.hedera.services.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.FunctionParam;
import com.hedera.services.store.contracts.precompile.codec.HrcParams;
import com.hedera.services.store.contracts.precompile.impl.DissociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiDissociatePrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txn.token.DissociateLogic;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DissociatePrecompileTest {
    private static final Bytes INVALID_INPUT = Bytes.fromHexString("0x00000000");
    private static final Bytes MULTIPLE_DISSOCIATE_INPUT = Bytes.fromHexString(
            "0x78b6391800000000000000000000000000000000000000000000000000000000000004940000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000004920000000000000000000000000000000000000000000000000000000000000492");
    private static final Bytes DISSOCIATE_INPUT = Bytes.fromHexString(
            "0x099794e8000000000000000000000000000000000000000000000000000000000000048e000000000000000000000000000000000000000000000000000000000000048c");
    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / HTSTestsUtil.DEFAULT_GAS_PRICE * 6 / 5;
    private final TokenID tokenID = asToken("0.0.777");

    private DissociatePrecompile dissociatePrecompile;
    private MultiDissociatePrecompile multiDissociatePrecompile;

    @Mock
    private HrcParams hrcParams;

    @Mock
    private DissociateLogic dissociateLogic;

    @Mock
    private Store store;

    @Mock
    private HederaEvmContractAliases hederaEvmContractAliases;

    @Mock
    private EvmInfrastructureFactory evmInfrastructureFactory;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Mock
    private MessageFrame frame;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private ExchangeRate exchangeRate;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private FeeObject mockFeeObject;

    @Mock
    private Account account;

    @Mock
    private Token token;

    @Mock
    private Id id;

    @Mock
    private Account updatedAccount;

    private SyntheticTxnFactory syntheticTxnFactory;
    private PrecompileMapper precompileMapper;
    private HTSPrecompiledContract subject;
    private final Address callerAccountAddress = Address.fromHexString("0x000000000000000000000000000000000000077e");
    private final TransactionBody.Builder transactionBody =
            TransactionBody.newBuilder().setTokenAssociate(TokenAssociateTransactionBody.newBuilder());

    @BeforeEach
    void setup() throws IOException {
        syntheticTxnFactory = new SyntheticTxnFactory();
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(TokenDissociateFromAccount, Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        final PrecompilePricingUtils pricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        dissociatePrecompile = new DissociatePrecompile(pricingUtils, syntheticTxnFactory, dissociateLogic);
        multiDissociatePrecompile = new MultiDissociatePrecompile(pricingUtils, syntheticTxnFactory, dissociateLogic);
        precompileMapper = new PrecompileMapper(Set.of(dissociatePrecompile, multiDissociatePrecompile));
        subject = new HTSPrecompiledContract(
                evmInfrastructureFactory, mirrorNodeEvmProperties, precompileMapper, evmHTSPrecompiledContract);
    }

    @Test
    void testBodyWithHrcParams() {
        final Bytes dissociateToken = Bytes.of(Integers.toBytes(ABI_ID_DISSOCIATE_TOKEN));
        given(hrcParams.token()).willReturn(tokenID);
        given(hrcParams.senderAddress()).willReturn(callerAccountAddress);

        final var accountID =
                EntityIdUtils.accountIdFromEvmAddress(Objects.requireNonNull(callerAccountAddress.toArray()));
        final var expected = syntheticTxnFactory.createDissociate(Dissociation.singleDissociation(accountID, tokenID));
        final var result = dissociatePrecompile.body(dissociateToken, a -> a, hrcParams);

        assertEquals(expected.getTokenDissociate(), result.getTokenDissociate());
    }

    @Test
    void dissociateTokenHappyPathWorks() {
        given(frame.getSenderAddress()).willReturn(HTSTestsUtil.senderAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.getStore()).willReturn(store);
        givenPricingUtilsContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(dissociateLogic.validateSyntax(any())).willReturn(ResponseCodeEnum.OK);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(DISSOCIATE_INPUT, a -> a);
        subject.getPrecompile()
                .getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME, transactionBody, store, hederaEvmContractAliases);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(HTSTestsUtil.successResult, result);
    }

    @Test
    void computeMultiDissociateTokenHappyPathWorks() {
        given(frame.getSenderAddress()).willReturn(HTSTestsUtil.senderAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.getStore()).willReturn(store);
        givenPricingUtilsContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(dissociateLogic.validateSyntax(any())).willReturn(ResponseCodeEnum.OK);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(MULTIPLE_DISSOCIATE_INPUT, a -> a);
        subject.getPrecompile()
                .getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME, transactionBody, store, hederaEvmContractAliases);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(HTSTestsUtil.successResult, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForDissociateTokens() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var builder = TokenDissociateTransactionBody.newBuilder();
        builder.setAccount(HTSTestsUtil.multiDissociateOp.accountId());
        builder.addAllTokens(HTSTestsUtil.multiDissociateOp.tokenIds());
        given(feeCalculator.computeFee(any(), any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(HTSTestsUtil.DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        subject.prepareFields(frame);
        subject.prepareComputation(MULTIPLE_DISSOCIATE_INPUT, a -> a);
        final long result = subject.getPrecompile()
                .getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME, transactionBody, store, hederaEvmContractAliases);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForDissociateToken() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var builder = TokenAssociateTransactionBody.newBuilder();
        builder.setAccount(HTSTestsUtil.dissociateOp.accountId());
        builder.addAllTokens(HTSTestsUtil.dissociateOp.tokenIds());
        given(feeCalculator.computeFee(any(), any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(HTSTestsUtil.DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        subject.prepareFields(frame);
        subject.prepareComputation(DISSOCIATE_INPUT, a -> a);
        final long result = subject.getPrecompile()
                .getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME, transactionBody, store, hederaEvmContractAliases);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeDissociateToken() {
        final var decodedInput = decodeDissociate(DISSOCIATE_INPUT, identity());

        assertTrue(decodedInput.accountId().getAccountNum() > 0);
        assertTrue(decodedInput.tokenIds().get(0).getTokenNum() > 0);
    }

    @Test
    void decodeMultipleDissociateToken() {
        final var decodedInput = multiDissociatePrecompileDecode(identity(), MULTIPLE_DISSOCIATE_INPUT);
        assertTrue(decodedInput.getTokenDissociate().getAccount().getAccountNum() > 0);
        assertEquals(2, decodedInput.getTokenDissociate().getTokensList().size());
        assertTrue(decodedInput.getTokenDissociate().getTokens(0).getTokenNum() > 0);
        assertTrue(decodedInput.getTokenDissociate().getTokens(1).getTokenNum() > 0);
    }

    @Test
    void decodeMultipleDissociateTokenInvalidInput() {
        final UnaryOperator<byte[]> identity = identity();
        assertThatThrownBy(() -> multiDissociatePrecompileDecode(identity, INVALID_INPUT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TransactionBody.Builder multiDissociatePrecompileDecode(UnaryOperator<byte[]> identity, Bytes input) {
        return multiDissociatePrecompile.body(input, identity, new FunctionParam(ABI_ID_DISSOCIATE_TOKENS));
    }

    private void givenMinFrameContext() {
        given(frame.getSenderAddress()).willReturn(HTSTestsUtil.contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
    }
}
