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

import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.defaultKycStatusWrapper;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.impl.GetTokenDefaultKycStatus.decodeTokenDefaultKycStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
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
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.GetTokenDefaultKycStatus;

@ExtendWith(MockitoExtension.class)
class GetTokenDefaultKycStatusTest {

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame frame;

    @Mock
    private EncodingFacade encoder;

    @Mock
    private EvmEncodingFacade evmEncoder;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    public static final Bytes GET_TOKEN_DEFAULT_KYC_STATUS_INPUT =
            Bytes.fromHexString("0x335e04c10000000000000000000000000000000000000000000000000000000000000404");

    //TODO waiting for Tanyu's implementation for EvmHTSPrecompileContract
    //private HTSPrecompiledContract subject;
    private MockedStatic<GetTokenDefaultKycStatus> getTokenDefaultKycStatus;

    @BeforeEach
    void setUp() throws IOException {
//        subject = new HTSPrecompiledContract(
//                gasCalculator,
//                encoder,
//                evmEncoder,
//                (now, minimumFeeInTinybars) -> 0,
//                evmHTSPrecompiledContract);
        getTokenDefaultKycStatus = Mockito.mockStatic(GetTokenDefaultKycStatus.class);
    }

    @AfterEach
    void closeMocks() {
        getTokenDefaultKycStatus.close();
    }

    @Test
    void getTokenDefaultKycStatus() {
        final var output = "0x000000000000000000000000000000000000000000000000000000000000"
                + "00160000000000000000000000000000000000000000000000000000000000000001";

        final var successOutput =
                Bytes.fromHexString("0x000000000000000000000000000000000000000000000000000000000000001600000000000"
                        + "00000000000000000000000000000000000000000000000000001");

        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS));

        //getTokenDefaultKycStatus.when(() -> decodeTokenDefaultKycStatus(any())).thenReturn(defaultKycStatusWrapper);
        //given(evmEncoder.encodeGetTokenDefaultKycStatus(true)).willReturn(successResult);
        //given(evmEncoder.encodeGetTokenDefaultKycStatus(true)).willReturn(Bytes.fromHexString(output));
        //given(frame.getValue()).willReturn(Wei.ZERO);

        //subject.prepareComputation(pretendArguments, a -> a);
        //final var result = subject.computeInternal(frame);

        //assertEquals(successOutput, result);
    }
}
