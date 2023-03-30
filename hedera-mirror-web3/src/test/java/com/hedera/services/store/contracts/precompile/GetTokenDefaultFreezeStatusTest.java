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

import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS;
import static com.hedera.services.store.contracts.precompile.impl.GetTokenDefaultFreezeStatus.decodeTokenDefaultFreezeStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.sun.xml.bind.AccessorFactory;
import java.io.IOException;
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
import com.hedera.services.store.contracts.precompile.impl.GetTokenDefaultFreezeStatus;

@ExtendWith(MockitoExtension.class)
class GetTokenDefaultFreezeStatusTest {

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame frame;

    @Mock
    private EncodingFacade encoder;

    @Mock
    private EvmEncodingFacade evmEncoder;

    @Mock
    private MirrorState wrapperState;

    @Mock
    private TransactionBody.Builder mockSynthBodyBuilder;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    public static final Bytes GET_TOKEN_DEFAULT_FREEZE_STATUS_INPUT =
            Bytes.fromHexString("0xa7daa18d00000000000000000000000000000000000000000000000000000000000003ff");

    private MockedStatic<GetTokenDefaultFreezeStatus> getTokenDefaultFreezeStatus;

    @BeforeEach
    void setUp() throws IOException {
        getTokenDefaultFreezeStatus = Mockito.mockStatic(GetTokenDefaultFreezeStatus.class);
    }

    @AfterEach
    void closeMocks() {
        getTokenDefaultFreezeStatus.close();
    }

    @Test
    void getTokenDefaultFreezeStatus() {
        final var output = "0x000000000000000000000000000000000000000000000000000000000000"
                + "00160000000000000000000000000000000000000000000000000000000000000001";

        final var successOutput =
                Bytes.fromHexString("0x000000000000000000000000000000000000000000000000000000000000001600000000000"
                        + "00000000000000000000000000000000000000000000000000001");

        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS));

        getTokenDefaultFreezeStatus
                .when(() -> decodeTokenDefaultFreezeStatus(any()))
                .thenReturn(HTSTestsUtil.defaultFreezeStatusWrapper);
        given(evmEncoder.encodeGetTokenDefaultFreezeStatus(true)).willReturn(HTSTestsUtil.successResult);
        given(evmEncoder.encodeGetTokenDefaultFreezeStatus(true)).willReturn(Bytes.fromHexString(output));
        given(frame.getValue()).willReturn(Wei.ZERO);

//        // when
//        subject.prepareFields(frame);
//        subject.prepareComputation(pretendArguments, a -> a);
//        final var result = subject.computeInternal(frame);
//
//        // then
//        assertEquals(successOutput, result);
    }

    @Test
    void decodeGetTokenDefaultFreezeStatusInput() {
        getTokenDefaultFreezeStatus
                .when(() -> decodeTokenDefaultFreezeStatus(GET_TOKEN_DEFAULT_FREEZE_STATUS_INPUT))
                .thenCallRealMethod();
        final var decodedInput = decodeTokenDefaultFreezeStatus(GET_TOKEN_DEFAULT_FREEZE_STATUS_INPUT);

        assertTrue(decodedInput.token().getTokenNum() > 0);
    }
}
