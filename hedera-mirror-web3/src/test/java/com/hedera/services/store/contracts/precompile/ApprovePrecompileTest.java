/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.store.contracts.precompile.impl.ApprovePrecompile.decodeTokenApprove;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hederahashgraph.api.proto.java.TokenID;
import java.math.BigInteger;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovePrecompileTest {
    private static final Bytes APPROVE_NFT_INPUT_ERC = Bytes.fromHexString(
            "0x095ea7b300000000000000000000000000000000000000000000000000000000000003ea0000000000000000000000000000000000000000000000000000000000000001");
    private static final Bytes APPROVE_NFT_INPUT_HAPI = Bytes.fromHexString(
            "0x7336aaf0000000000000000000000000000000000000000000000000000000000000123400000000000000000000000000000000000000000000000000000000000003ea0000000000000000000000000000000000000000000000000000000000000001");

    public static final Bytes APPROVE_TOKEN_INPUT_ERC = Bytes.fromHexString(
            "0x095ea7b300000000000000000000000000000000000000000000000000000000000003f0000000000000000000000000000000000000000000000000000000000000000a");
    public static final Bytes APPROVE_TOKEN_INPUT_HAPI = Bytes.fromHexString(
            "0xe1f21c67000000000000000000000000000000000000000000000000000000000000123400000000000000000000000000000000000000000000000000000000000003f0000000000000000000000000000000000000000000000000000000000000000a");
    private static final long ACCOUNT_NUM_SPENDER_NFT = 0x3ea;
    private static final long ACCOUNT_NUM_SPENDER = 0x3f0;
    private static final long TOKEN_NUM_HAPI_TOKEN = 0x1234;
    private static final TokenID TOKEN_ID =
            TokenID.newBuilder().setTokenNum(TOKEN_NUM_HAPI_TOKEN).build();

    @Test
    void decodeApproveForNFTERC() {
        final var decodedInput = decodeTokenApprove(APPROVE_NFT_INPUT_ERC, TOKEN_ID, false, identity());

        assertEquals(ACCOUNT_NUM_SPENDER_NFT, decodedInput.spender().getAccountNum());
        assertEquals(TOKEN_NUM_HAPI_TOKEN, decodedInput.tokenId().getTokenNum());
        assertEquals(BigInteger.ONE, decodedInput.serialNumber());
    }

    @Test
    void decodeApproveForTokenERC() {
        final var decodedInput = decodeTokenApprove(APPROVE_TOKEN_INPUT_ERC, TOKEN_ID, true, identity());

        assertTrue(decodedInput.spender().getAccountNum() > 0);
        assertEquals(BigInteger.TEN, decodedInput.amount());
    }

    @Test
    void decodeApproveForNFTHAPI() {
        UnaryOperator<byte[]> identity = identity();
        final var decodedInput =
                decodeTokenApprove(APPROVE_NFT_INPUT_HAPI, TokenID.getDefaultInstance(), false, identity);

        assertEquals(ACCOUNT_NUM_SPENDER_NFT, decodedInput.spender().getAccountNum());
        assertEquals(TOKEN_NUM_HAPI_TOKEN, decodedInput.tokenId().getTokenNum());
        assertEquals(BigInteger.ONE, decodedInput.serialNumber());
    }

    @Test
    void decodeApproveForTokenAHPI() {
        UnaryOperator<byte[]> identity = identity();

        final var decodedInput =
                decodeTokenApprove(APPROVE_TOKEN_INPUT_HAPI, TokenID.getDefaultInstance(), true, identity);

        assertEquals(ACCOUNT_NUM_SPENDER, decodedInput.spender().getAccountNum());
        assertEquals(TOKEN_NUM_HAPI_TOKEN, decodedInput.tokenId().getTokenNum());
        assertEquals(BigInteger.TEN, decodedInput.amount());
    }
}
