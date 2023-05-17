package com.hedera.mirror.web3.evm.store.contract.precompile;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.contracts.precompile.MirrorHTSPrecompiledContract;
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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
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
    private MessageFrame parentMessageFrame;

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
    private static final String ERROR_MESSAGE = "Precompile not supported for non-static frames";

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
        given(messageFrame.getMessageFrameStack()).willReturn(stack);
        given(stack.iterator()).willReturn(iterator);
        given(iterator.hasNext()).willReturn(false);

        final var result = Pair.of(1000L, Bytes.EMPTY);
        given(viewExecutor.computeCosted()).willReturn(result);

        final var precompileResult = subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor);
        assertThat(result).isEqualTo(precompileResult);
    }

    @Test
    void throwExceptionOnNonStaticFrame() {
        //isTokenAddress signature
        final var functionHash = Bytes.fromHexString("0x19f37361");

        given(messageFrame.isStatic()).willReturn(false);

        assertThatThrownBy(() -> subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor)).hasMessage(ERROR_MESSAGE);
    }

    @Test
    void throwExceptionOnNonStaticParentFrame() {
        //name signature
        final var functionHash = Bytes.fromHexString("0x06fdde03");

        given(messageFrame.isStatic()).willReturn(true);
        given(messageFrame.getMessageFrameStack()).willReturn(stack);
        given(stack.iterator()).willReturn(iterator);
        given(iterator.hasNext()).willReturn(true);
        given(iterator.next()).willReturn(parentMessageFrame);
        given(parentMessageFrame.isStatic()).willReturn(false);

        assertThatThrownBy(() -> subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor)).hasMessage(ERROR_MESSAGE);
    }
}
