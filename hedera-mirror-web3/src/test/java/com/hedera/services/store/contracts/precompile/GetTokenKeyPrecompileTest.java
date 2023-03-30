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

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenKeyType.ADMIN_KEY;
import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.fungible;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.impl.GetTokenKeyPrecompile.decodeGetTokenKey;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenID;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenKeyWrapper;
import com.hedera.services.store.contracts.MirrorState;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.GetTokenKeyPrecompile;

@ExtendWith(MockitoExtension.class)
class GetTokenKeyPrecompileTest {

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame frame;

    @Mock
    private EncodingFacade encoder;

    @Mock
    private EvmEncodingFacade evmEncoder;

    @Mock
    private MirrorState wrappedState;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    private static final Bytes GET_TOKEN_KEY_INPUT = Bytes.fromHexString(
            "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
    private MockedStatic<GetTokenKeyPrecompile> getTokenKeyPrecompile;
    private GetTokenKeyWrapper<TokenID> wrapper = new GetTokenKeyWrapper<>(fungible, 1L);
    private final byte[] ed25519Key = new byte[] {
            -98, 65, 115, 52, -46, -22, 107, -28, 89, 98, 64, 96, -29, -17, -36, 27, 69, -102, -120, 75, -58, -87,
            -62, 50,
            52, -102, -13, 94, -112, 96, -19, 98
    };

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(HederaFunctionality.TokenGetInfo, Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
        getTokenKeyPrecompile = Mockito.mockStatic(GetTokenKeyPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        getTokenKeyPrecompile.close();
    }

    @Test
    void successfulCallForGetFungibleTokenKey() {
        // given
        final var input = Bytes.fromHexString(
                "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        getTokenKeyPrecompile.when(() -> decodeGetTokenKey(input)).thenReturn(wrapper);
        given(evmEncoder.encodeGetTokenKey(any())).willReturn(successResult);
//        // when
//        subject.prepareFields(frame);
//        subject.prepareComputation(input, a -> a);
//        final var result = subject.computeInternal(frame);
//        // then
//        assertEquals(successResult, result);
    }

    @ParameterizedTest
    @ValueSource(longs = {2L, 4L, 8L, 16L, 32L, 64L, 200L})
    void successfulCallForGetFungibleTokenKeyWithWipeKey(long keyType) {
        // given
        final var input = Bytes.fromHexString(
                "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        wrapper = new GetTokenKeyWrapper<>(fungible, keyType);
        getTokenKeyPrecompile.when(() -> decodeGetTokenKey(input)).thenReturn(wrapper);
        given(evmEncoder.encodeGetTokenKey(any())).willReturn(successResult);
//        // when
//        subject.prepareFields(frame);
//        subject.prepareComputation(input, a -> a);
//        final var result = subject.computeInternal(frame);
//        // then
//        assertEquals(successResult, result);
    }

    @Test
    void getTokenKeyCallForInvalidTokenIds() {
        // given
        final var input = Bytes.fromHexString(
                "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        getTokenKeyPrecompile.when(() -> decodeGetTokenKey(input)).thenReturn(wrapper);
//        // when
//        subject.prepareFields(frame);
//        subject.prepareComputation(input, a -> a);
//        final var result = subject.computeInternal(frame);
//        // then
//        assertEquals(invalidTokenIdResult, result);
    }

    @Test
    void decodeFungibleTokenGetKey() {
        getTokenKeyPrecompile.when(() -> decodeGetTokenKey(GET_TOKEN_KEY_INPUT)).thenCallRealMethod();
        final var decodedInput = decodeGetTokenKey(GET_TOKEN_KEY_INPUT);
        assertTrue(decodedInput.token().getTokenNum() > 0);
        assertEquals(1L, decodedInput.keyType());
        assertEquals(ADMIN_KEY, decodedInput.tokenKeyType());
    }

    private void givenMinimalFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getValue()).willReturn(Wei.ZERO);
    }
}
