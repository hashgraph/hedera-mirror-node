/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_IS_FROZEN;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenFreezeUnFreezeWrapper;
import static com.hedera.services.store.contracts.precompile.impl.IsFrozenPrecompile.decodeIsFrozen;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.services.store.contracts.MirrorState;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.IsFrozenPrecompile;

@ExtendWith(MockitoExtension.class)
class IsFrozenPrecompileTest {

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame frame;

    @Mock
    private EncodingFacade encoder;

    @Mock
    private EvmEncodingFacade evmEncoder;

    @Mock
    private MirrorState mirrprState;

    @Mock
    private TransactionBody.Builder mockSynthBodyBuilder;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    public static final Bytes IS_FROZEN_INPUT = Bytes.fromHexString(
            "0x46de0fb1000000000000000000000000000000000000000000000000000000000000050e000000000000000000000000000000000000000000000000000000000000050c");
    private MockedStatic<IsFrozenPrecompile> isFrozenPrecompile;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(HederaFunctionality.TokenUnfreezeAccount, Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
        isFrozenPrecompile = Mockito.mockStatic(IsFrozenPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        isFrozenPrecompile.close();
    }

    @Test
    void computeCallsCorrectImplementationForIsFrozenFungibleToken() {
        // given
        final var output = "0x000000000000000000000000000000000000000000000000000000000000"
                + "00160000000000000000000000000000000000000000000000000000000000000001";
        final var successOutput =
                Bytes.fromHexString("0x000000000000000000000000000000000000000000000000000000000000001600000000000"
                        + "00000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_IS_FROZEN));
        isFrozenPrecompile.when(() -> decodeIsFrozen(any(), any())).thenReturn(tokenFreezeUnFreezeWrapper);
        given(evmEncoder.encodeIsFrozen(true)).willReturn(successResult);
        given(evmEncoder.encodeIsFrozen(true)).willReturn(Bytes.fromHexString(output));
        given(frame.getValue()).willReturn(Wei.ZERO);

//        // when
//        subject.prepareFields(frame);
//        subject.prepareComputation(input, a -> a);
//        final var result = subject.computeInternal(frame);
//
//        // then
//        assertEquals(successOutput, result);
    }

    @Test
    void decodeTokenIsFrozenWithValidInput() {
        isFrozenPrecompile
                .when(() -> decodeIsFrozen(IS_FROZEN_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeIsFrozen(IS_FROZEN_INPUT, identity());

        assertEquals(TokenID.newBuilder().setTokenNum(1294).build(), decodedInput.token());
    }

    private void givenMinimalFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
    }
}
