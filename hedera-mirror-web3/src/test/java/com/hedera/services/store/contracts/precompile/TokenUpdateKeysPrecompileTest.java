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

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.failResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungible;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.impl.TokenUpdateKeysPrecompile.decodeUpdateTokenKeys;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateKeysWrapper;
import com.hedera.services.store.contracts.precompile.impl.TokenUpdateKeysPrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenUpdateKeysPrecompileTest {
    @Mock
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private MessageFrame frame;

    @Mock
    private TokenUpdateLogic updateLogic;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private HederaEvmContractAliases hederaEvmContractAliases;

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
    private AccessorFactory accessorFactory;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    @Mock
    private OptionValidator optionValidator;

    @Mock
    private TransactionBody.Builder transactionBodyBuilder;

    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final Bytes UPDATE_FUNGIBLE_TOKEN_KEYS = Bytes.fromHexString("0x6fc3cbaf"
            + "0000000000000000000000000000000000000000000000000000000000001065"
            + "0000000000000000000000000000000000000000000000000000000000000040"
            + "0000000000000000000000000000000000000000000000000000000000000005"
            + "00000000000000000000000000000000000000000000000000000000000000a0"
            + "00000000000000000000000000000000000000000000000000000000000001e0"
            + "0000000000000000000000000000000000000000000000000000000000000340"
            + "0000000000000000000000000000000000000000000000000000000000000460"
            + "0000000000000000000000000000000000000000000000000000000000000580"
            + "0000000000000000000000000000000000000000000000000000000000000003"
            + "0000000000000000000000000000000000000000000000000000000000000040"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "00000000000000000000000000000000000000000000000000000000000000a0"
            + "00000000000000000000000000000000000000000000000000000000000000e0"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000020"
            + "1aeca66efce3b1c581d865197a41880b6c05c3115cfeac97f2832c2198f49f57"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000000000c"
            + "0000000000000000000000000000000000000000000000000000000000000040"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "00000000000000000000000000000000000000000000000000000000000000a0"
            + "00000000000000000000000000000000000000000000000000000000000000c0"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000021"
            + "031929ec5ff0aeef191aff1a4f0775470da849d92fc5eaed6e22b4c829ca5e99"
            + "b400000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000010"
            + "0000000000000000000000000000000000000000000000000000000000000040"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000001064"
            + "00000000000000000000000000000000000000000000000000000000000000a0"
            + "00000000000000000000000000000000000000000000000000000000000000c0"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000040"
            + "0000000000000000000000000000000000000000000000000000000000000040"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000001064"
            + "00000000000000000000000000000000000000000000000000000000000000a0"
            + "00000000000000000000000000000000000000000000000000000000000000c0"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000020"
            + "0000000000000000000000000000000000000000000000000000000000000040"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "00000000000000000000000000000000000000000000000000000000000000a0"
            + "00000000000000000000000000000000000000000000000000000000000000c0"
            + "0000000000000000000000000000000000000000000000000000000000001064"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000");

    private HTSPrecompiledContract subject;

    @BeforeEach
    void setUp() {
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        SyntheticTxnFactory syntheticTxnFactory = new SyntheticTxnFactory();
        final var tokenUpdateKeysPrecompile = new TokenUpdateKeysPrecompile(
                syntheticTxnFactory, precompilePricingUtils, updateLogic, optionValidator, evmProperties);
        PrecompileMapper precompileMapper = new PrecompileMapper(Set.of(tokenUpdateKeysPrecompile));

        subject = new HTSPrecompiledContract(
                infrastructureFactory, evmProperties, precompileMapper, evmHTSPrecompiledContract);
    }

    @Test
    void computeCallsSuccessfullyUpdateKeysForFungibleToken() {
        // given
        givenFrameContext();
        given(frame.getInputData()).willReturn(UPDATE_FUNGIBLE_TOKEN_KEYS);
        givenMinimalContextForSuccessfulCall();
        givenPricingUtilsContext();
        given(updateLogic.validate(any())).willReturn(OK);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(UPDATE_FUNGIBLE_TOKEN_KEYS, a -> a);
        subject.getPrecompile().getMinimumFeeInTinybars(Timestamp.getDefaultInstance(), transactionBodyBuilder.build());
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(successResult, result);
    }

    @Test
    void failsWithWrongValidityForUpdateFungibleToken() {
        // given
        givenFrameContext();
        givenMinimalContextForSuccessfulCall();
        given(worldUpdater.aliases()).willReturn(hederaEvmContractAliases);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(updateLogic.validate(any())).willReturn(FAIL_INVALID);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(UPDATE_FUNGIBLE_TOKEN_KEYS, a -> a);
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(failResult, result);
    }

    @Test
    void decodeUpdateTokenKeysForFungible() {
        final var decodedInput = decodeUpdateTokenKeys(UPDATE_FUNGIBLE_TOKEN_KEYS, identity());
        assertTrue(decodedInput.tokenID().getTokenNum() > 0);
        assertFalse(decodedInput.tokenKeys().isEmpty());
    }

    private void givenFrameContext() {
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
    }

    private void givenMinimalContextForSuccessfulCall() {
        given(worldUpdater.aliases()).willReturn(hederaEvmContractAliases);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
        given(worldUpdater.aliases()).willReturn(hederaEvmContractAliases);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    private TokenUpdateKeysWrapper getUpdateWrapper() {
        final var adminKey = new KeyValueWrapper(
                false, null, new byte[] {}, new byte[] {}, EntityIdUtils.contractIdFromEvmAddress(contractAddress));
        final var multiKey = new KeyValueWrapper(
                false, EntityIdUtils.contractIdFromEvmAddress(contractAddress), new byte[] {}, new byte[] {}, null);
        return new TokenUpdateKeysWrapper(
                fungible, List.of(new TokenKeyWrapper(112, multiKey), new TokenKeyWrapper(1, adminKey)));
    }
}
