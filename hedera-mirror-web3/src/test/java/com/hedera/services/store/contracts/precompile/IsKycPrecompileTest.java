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

import com.esaulpaugh.headlong.util.Integers;

import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;

import com.hedera.services.store.contracts.precompile.impl.IsKycPrecompile;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.IOException;
import java.util.Optional;

import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_IS_KYC;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.grantRevokeKycWrapper;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.impl.IsKycPrecompile.decodeIsKyc;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class IsKycPrecompileTest {

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame frame;


    @Mock
    private EncodingFacade encoder;

    @Mock
    private EvmEncodingFacade evmEncoder;

    @Mock
    private TransactionBody.Builder mockSynthBodyBuilder;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    public static final Bytes IS_KYC = Bytes.fromHexString(
            "0xf2c31ff400000000000000000000000000000000000000000000000000000000000004b200000000000000000000000000000000000000000000000000000000000004b0");
    private MockedStatic<IsKycPrecompile> isKycPrecompile;

    @BeforeEach
    void setUp() throws IOException {
        isKycPrecompile = Mockito.mockStatic(IsKycPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        isKycPrecompile.close();
    }

    @Test
    void IsKyc() {
        // given
        final var output = "0x000000000000000000000000000000000000000000000000000000000000"
                + "00160000000000000000000000000000000000000000000000000000000000000001";
        final var successOutput =
                Bytes.fromHexString("0x000000000000000000000000000000000000000000000000000000000000001600000000000"
                        + "00000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_IS_KYC));
        isKycPrecompile.when(() -> decodeIsKyc(any(), any())).thenReturn(grantRevokeKycWrapper);
        given(evmEncoder.encodeIsKyc(true)).willReturn(successResult);
        given(evmEncoder.encodeIsKyc(true)).willReturn(Bytes.fromHexString(output));
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
    void decodeIsKycInput() {
        isKycPrecompile.when(() -> decodeIsKyc(IS_KYC, identity())).thenCallRealMethod();
        final var decodedInput = decodeIsKyc(IS_KYC, identity());

        assertTrue(decodedInput.token().getTokenNum() > 0);
        assertTrue(decodedInput.account().getAccountNum() > 0);
    }

    private void givenMinimalFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
    }
}
