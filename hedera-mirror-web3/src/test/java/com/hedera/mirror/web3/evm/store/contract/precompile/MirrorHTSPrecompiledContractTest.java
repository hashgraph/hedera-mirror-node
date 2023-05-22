package com.hedera.mirror.web3.evm.store.contract.precompile;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Deque;
import java.util.Iterator;


import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private Deque<MessageFrame> stack;

    @Mock
    private Iterator<MessageFrame> iterator;

    @Mock
    private ViewGasCalculator gasCalculator;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private ViewExecutor viewExecutor;

    @Mock
    private StackedStateFrames<Object> stackedStateFrames;

    private MirrorHTSPrecompiledContract subject;

    @BeforeEach
    void setUp() {
        subject = new MirrorHTSPrecompiledContract(
                evmInfrastructureFactory, mirrorNodeEvmProperties, stackedStateFrames);
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
}
