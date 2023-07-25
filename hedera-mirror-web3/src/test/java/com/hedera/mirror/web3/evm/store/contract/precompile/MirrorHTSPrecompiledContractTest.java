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

package com.hedera.mirror.web3.evm.store.contract.precompile;

import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_ERC_NAME;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.services.store.contracts.precompile.codec.EncodingFacade.SUCCESS_RESULT;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hyperledger.besu.datatypes.Address.ALTBN128_ADD;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.store.contracts.precompile.HTSPrecompiledContract;
import com.hedera.services.store.contracts.precompile.PrecompileMapper;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MirrorHTSPrecompiledContractTest {

    // mock precompile signature
    private static final Bytes MOCK_PRECOMPILE_FUNCTION_HASH = Bytes.fromHexString("0x00000000");
    private static final Pair<Long, Bytes> FAILURE_RESULT = Pair.of(0L, null);

    private static final String ERROR_MESSAGE = "Precompile not supported for non-static frames";

    @Mock
    private EvmInfrastructureFactory evmInfrastructureFactory;

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private MessageFrame parentMessageFrame;

    @Mock
    private BlockValues blockValues;

    @Mock
    private ViewGasCalculator gasCalculator;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private ViewExecutor viewExecutor;

    @Mock
    private RedirectViewExecutor redirectViewExecutor;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private HederaEvmWorldStateTokenAccount account;

    @Mock
    private MirrorEvmContractAliases mirrorEvmContractAliases;

    @Mock
    private EntityAddressSequencer entityAddressSequencer;

    private MirrorHTSPrecompiledContract subject;
    private Deque<MessageFrame> messageFrameStack;
    private Store store;

    @InjectMocks
    private MockPrecompile mockPrecompile;

    private PrecompileMapper precompileMapper;

    @InjectMocks
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @BeforeEach
    void setUp() {
        final var accessors = List.<DatabaseAccessor<Object, ?>>of(
                new BareDatabaseAccessor<Object, Character>() {}, new BareDatabaseAccessor<Object, String>() {});

        store = new StoreImpl(accessors);
        store.wrap(); // Create top-level RWCachingStateFrame

        messageFrameStack = new ArrayDeque<>();
        messageFrameStack.push(messageFrame);

        precompileMapper = new PrecompileMapper(Set.of(mockPrecompile));
        subject = new MirrorHTSPrecompiledContract(
                evmInfrastructureFactory,
                new HTSPrecompiledContract(
                        evmInfrastructureFactory,
                        mirrorNodeEvmProperties,
                        precompileMapper,
                        new EvmHTSPrecompiledContract(evmInfrastructureFactory)));
    }

    @Test
    void returnResultForStaticFrame() {
        // isTokenAddress signature
        final var functionHash = Bytes.fromHexString("0x19f37361");

        given(evmInfrastructureFactory.newViewExecutor(any(), any(), any(), any()))
                .willReturn(viewExecutor);
        given(messageFrame.isStatic()).willReturn(true);

        final var expectedResult = Pair.of(1000L, Bytes.EMPTY);
        given(viewExecutor.computeCosted()).willReturn(expectedResult);

        final var precompileResult = subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor);
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void returnResultForNonStaticFrameAndViewFunction() {
        // isTokenAddress signature
        final var functionHash = Bytes.fromHexString("0x19f37361");

        given(evmInfrastructureFactory.newViewExecutor(any(), any(), any(), any()))
                .willReturn(viewExecutor);
        given(viewExecutor.computeCosted()).willReturn(Pair.of(0L, Bytes.EMPTY));
        given(messageFrame.isStatic()).willReturn(false);

        final var precompileResult = subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor);

        final var expectedResult = Pair.of(0L, Bytes.EMPTY);
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void returnResultForStaticFrameAndErcViewFunction() {
        // name signature
        final Bytes input = prerequisitesForRedirect(ABI_ID_ERC_NAME, ALTBN128_ADD);

        given(evmInfrastructureFactory.newRedirectExecutor(any(), any(), any(), any()))
                .willReturn(redirectViewExecutor);
        given(redirectViewExecutor.computeCosted()).willReturn(Pair.of(0L, SUCCESS_RESULT));

        given(messageFrame.isStatic()).willReturn(true);

        final var precompileResult = subject.computeCosted(input, messageFrame, gasCalculator, tokenAccessor);

        assertThat(Pair.of(0L, SUCCESS_RESULT)).isEqualTo(precompileResult);
    }

    @Test
    void prepareComputationForUnsupportedPrecompileThrowsException() {
        // updateTokenKeys signature
        final var functionHash = Bytes.fromHexString("0x6fc3cbaf");

        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getSenderAddress()).willReturn(Address.ALTBN128_MUL);
        given(messageFrame.isStatic()).willReturn(false);

        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        assertThatThrownBy(() -> subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage(ERROR_MESSAGE);
    }

    @Test
    void nonStaticCallToPrecompileWorks() {
        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getSenderAddress()).willReturn(Address.ALTBN128_MUL);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getValue()).willReturn(Wei.ZERO);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.getStore()).willReturn(store);
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(10L);
        given(worldUpdater.getStore()).willReturn(store);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var precompileResult =
                subject.computeCosted(MOCK_PRECOMPILE_FUNCTION_HASH, messageFrame, gasCalculator, tokenAccessor);

        final var expectedResult = Pair.of(0L, SUCCESS_RESULT);
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void unqualifiedDelegateCallFails() {
        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(Address.BLS12_G1ADD);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);

        final var precompileResult =
                subject.computeCosted(MOCK_PRECOMPILE_FUNCTION_HASH, messageFrame, gasCalculator, tokenAccessor);

        assertThat(FAILURE_RESULT).isEqualTo(precompileResult);
    }

    @Test
    void invalidFeeSentFails() {
        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(Address.BLS12_G1ADD);
        given(messageFrame.getSenderAddress()).willReturn(Address.ALTBN128_MUL);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(Address.BLS12_G1ADD)).willReturn(account);
        given(account.getNonce()).willReturn(-1L);
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(10L);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var precompileResult =
                subject.computeCosted(MOCK_PRECOMPILE_FUNCTION_HASH, messageFrame, gasCalculator, tokenAccessor);

        final var expectedResult = Pair.of(0L, EncodingFacade.resultFrom(ResponseCodeEnum.FAIL_INVALID));
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void delegateParentCallToTokenRedirectFails() {
        messageFrameStack.push(parentMessageFrame);

        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(Address.BLS12_G1ADD);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(Address.BLS12_G1ADD)).willReturn(account);
        given(account.getNonce()).willReturn(-1L);
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);

        final var precompileResult =
                subject.computeCosted(MOCK_PRECOMPILE_FUNCTION_HASH, messageFrame, gasCalculator, tokenAccessor);

        assertThat(FAILURE_RESULT).isEqualTo(precompileResult);
    }

    @Test
    void callingNonExistingPrecompileHalts() {
        // mock precompile signature
        final var functionHash = Bytes.fromHexString("0x11111111");

        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(Address.BLS12_G1ADD);
        given(messageFrame.getSenderAddress()).willReturn(Address.ALTBN128_MUL);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(Address.BLS12_G1ADD)).willReturn(account);
        given(account.getNonce()).willReturn(-1L);
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        final var precompileResult = subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor);

        assertThat(FAILURE_RESULT).isEqualTo(precompileResult);
    }

    @Test
    void nullPrecompileResponseHalts() {
        // mock precompile signature
        final var functionHash = Bytes.fromHexString("0x000000000000000000000000000000000000000000000000");

        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(Address.BLS12_G1ADD);
        given(messageFrame.getSenderAddress()).willReturn(Address.ECREC);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.getStore()).willReturn(store);
        given(messageFrame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.get(Address.BLS12_G1ADD)).willReturn(account);
        given(account.getNonce()).willReturn(-1L);
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(10L);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.getStore()).willReturn(store);

        final var precompileResult = subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor);

        assertThat(FAILURE_RESULT).isEqualTo(precompileResult);
    }

    @Test
    void delegateCallWithNoParent() {
        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(Address.BLS12_G1ADD);
        given(messageFrame.getSenderAddress()).willReturn(Address.ALTBN128_MUL);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.getStore()).willReturn(store);
        given(worldUpdater.get(Address.BLS12_G1ADD)).willReturn(account);
        given(messageFrame.getValue()).willReturn(Wei.ZERO);
        given(account.getNonce()).willReturn(-1L);
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(10L);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.getStore()).willReturn(store);

        final var precompileResult =
                subject.computeCosted(MOCK_PRECOMPILE_FUNCTION_HASH, messageFrame, gasCalculator, tokenAccessor);

        final var expectedResult = Pair.of(0L, EncodingFacade.resultFrom(ResponseCodeEnum.SUCCESS));
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void invalidTransactionIsHandledProperly() {
        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getSenderAddress()).willReturn(Address.ZERO);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getValue()).willReturn(Wei.ZERO);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(10L);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var precompileResult =
                subject.computeCosted(MOCK_PRECOMPILE_FUNCTION_HASH, messageFrame, gasCalculator, tokenAccessor);

        final var expectedResult = Pair.of(0L, EncodingFacade.resultFrom(ResponseCodeEnum.INVALID_ACCOUNT_ID));
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void redirectForErcViewFunctionWorks() {
        final Bytes input = prerequisitesForRedirect(ABI_ID_ERC_NAME, ALTBN128_ADD);

        given(messageFrame.isStatic()).willReturn(false);
        given(evmInfrastructureFactory.newRedirectExecutor(any(), any(), any(), any()))
                .willReturn(redirectViewExecutor);
        given(redirectViewExecutor.computeCosted()).willReturn(Pair.of(0L, SUCCESS_RESULT));

        final var precompileResult = subject.computeCosted(input, messageFrame, gasCalculator, tokenAccessor);

        final var expectedResult = Pair.of(0L, SUCCESS_RESULT);
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    Bytes prerequisitesForRedirect(final int descriptor, final Address tokenAddress) {
        return Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                tokenAddress,
                Bytes.of(Integers.toBytes(descriptor)));
    }

    static class BareDatabaseAccessor<K, V> extends DatabaseAccessor<K, V> {
        @NonNull
        @Override
        public Optional<V> get(@NonNull final K key) {
            throw new UnsupportedOperationException("BareGroundTruthAccessor.get");
        }
    }
}
