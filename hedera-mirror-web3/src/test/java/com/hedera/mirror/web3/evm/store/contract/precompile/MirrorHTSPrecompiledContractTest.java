package com.hedera.mirror.web3.evm.store.contract.precompile;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;


import static com.hedera.services.store.contracts.precompile.codec.EncodingFacade.SUCCESS_RESULT;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MirrorHTSPrecompiledContractTest {

    @Mock
    private EvmInfrastructureFactory evmInfrastructureFactory;

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Mock
    private MessageFrame messageFrame;


    @Mock
    private BlockValues blockValues;

    @Mock
    private ViewGasCalculator gasCalculator;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private ViewExecutor viewExecutor;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    private MirrorHTSPrecompiledContract subject;
    private PrecompileFactory precompileFactory;
    private StackedStateFrames<Object> stackedStateFrames;

    static class BareDatabaseAccessor<K, V> extends DatabaseAccessor<K, V> {
        @NonNull
        @Override
        public Optional<V> get(@NonNull final K key) {
            throw new UnsupportedOperationException("BareGroundTruthAccessor.get");
        }
    }

    @BeforeEach
    void setUp() {
        final var accessors = List.<DatabaseAccessor<Object, ?>>of(
                new BareDatabaseAccessor<Object, Character>() {}, new BareDatabaseAccessor<Object, String>() {});

        precompileFactory = new PrecompileFactory(Set.of(new MockPrecompile()));
        stackedStateFrames = new StackedStateFrames<>(accessors);
        stackedStateFrames.push();
        subject = new MirrorHTSPrecompiledContract(
                evmInfrastructureFactory, mirrorNodeEvmProperties, stackedStateFrames, precompileFactory);
    }

    @Test
    void returnResultForStaticFrame() {
        //isTokenAddress signature
        final var functionHash = Bytes.fromHexString("0x19f37361");

        given(evmInfrastructureFactory.newViewExecutor(any(), any(), any(), any())).willReturn(viewExecutor);
        given(messageFrame.isStatic()).willReturn(true);

        final var expectedResult = Pair.of(1000L, Bytes.EMPTY);
        given(viewExecutor.computeCosted()).willReturn(expectedResult);

        final var precompileResult = subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor);
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void returnResultForNonStaticFrameAndViewFunction() {
        //isTokenAddress signature
        final var functionHash = Bytes.fromHexString("0x19f37361");

        given(evmInfrastructureFactory.newViewExecutor(any(), any(), any(), any())).willReturn(viewExecutor);
        given(viewExecutor.computeCosted()).willReturn(Pair.of(0L, Bytes.EMPTY));
        given(messageFrame.isStatic()).willReturn(false);

        final var precompileResult = subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor);

        final var expectedResult = Pair.of(0L, Bytes.EMPTY);
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void returnResultForNonStaticFrameAndErcViewFunction() {
        //name signature
        final var functionHash = Bytes.fromHexString("0x06fdde03");

        given(messageFrame.isStatic()).willReturn(true);

        final var precompileResult = subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor);

        final var expectedResult = Pair.of(0L, null);
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void prepareComputationForUnsupportedPrecompileThrowsException() {
        //mint signature
        final var functionHash = Bytes.fromHexString("0x278e0b88");

        given(messageFrame.getContractAddress()).willReturn(Address.ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(Address.ALTBN128_ADD);
        given(messageFrame.getSenderAddress()).willReturn(Address.ALTBN128_MUL);
        given(messageFrame.isStatic()).willReturn(false);

        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        assertThatThrownBy(() -> subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Precompile not supported for non-static frames");
    }

    @Test
    void nonStaticCallToPrecompileWorks() {
        //mock precompile signature
        final var functionHash = Bytes.fromHexString("0x00000000");

        given(messageFrame.getContractAddress()).willReturn(Address.ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(Address.ALTBN128_ADD);
        given(messageFrame.getSenderAddress()).willReturn(Address.ALTBN128_MUL);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getValue()).willReturn(Wei.ZERO);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(10L);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var precompileResult = subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor);

        final var expectedResult = Pair.of(0L, SUCCESS_RESULT);
        assertThat(expectedResult).isEqualTo(precompileResult);
    }
}
