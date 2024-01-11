/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.services.store.contracts.precompile.impl.ERCTransferPrecompile.decodeERCTransfer;
import static com.hedera.services.store.contracts.precompile.impl.ERCTransferPrecompile.decodeERCTransferFrom;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.contracts.precompile.HTSTestsUtil;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ERCTransferPrecompileTest {
    private static final Bytes TRANSFER_INPUT = Bytes.fromHexString(
            "0xa9059cbb00000000000000000000000000000000000000000000000000000000000005a50000000000000000000000000000000000000000000000000000000000000002");
    private static final Bytes TRANSFER_LONG_OVERFLOWN = Bytes.fromHexString(
            "0xa9059cbb00000000000000000000000000000000000000000000000000000000000003ea0000000000000000000000000000000000000000000000010000000000000002");
    private static final Bytes TRANSFER_FROM_FUNGIBLE_INPUT = Bytes.fromHexString(
            "0x23b872dd00000000000000000000000000000000000000000000000000000000000005aa00000000000000000000000000000000000000000000000000000000000005ab0000000000000000000000000000000000000000000000000000000000000005");
    private static final Bytes TRANSFER_FROM_NON_FUNGIBLE_INPUT = Bytes.fromHexString(
            "0x23b872dd00000000000000000000000000000000000000000000000000000000000003e900000000000000000000000000000000000000000000000000000000000003ea0000000000000000000000000000000000000000000000000000000000000001");
    private static final Bytes TRANSFER_FROM_LONG_OVERFLOWN = Bytes.fromHexString(
            "0x23b872dd00000000000000000000000000000000000000000000000000000000000003ef00000000000000000000000000000000000000000000000000000000000003f00000000000000000000000000000000000000000000000010000000000000002");
    private static final Bytes HAPI_TRANSFER_FROM_FUNGIBLE_INPUT = Bytes.fromHexString(
            "0x15dacbea000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000005aa00000000000000000000000000000000000000000000000000000000000005ab0000000000000000000000000000000000000000000000000000000000000005");
    private static final Bytes HAPI_TRANSFER_FROM_NFT_INPUT = Bytes.fromHexString(
            "0x9b23d3d9000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000005aa00000000000000000000000000000000000000000000000000000000000005ab0000000000000000000000000000000000000000000000000000000000000005");
    private static final long TOKEN_NUM_HAPI_TOKEN = 0x1234;
    private static final TokenID TOKEN_ID =
            TokenID.newBuilder().setTokenNum(TOKEN_NUM_HAPI_TOKEN).build();

    private final Address senderAddress = HTSTestsUtil.senderAddress;

    @Mock
    private Store store;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private TokenID tokenID;

    @Test
    void decodeTransferInput() {
        final var decodedInput = decodeERCTransfer(TRANSFER_INPUT, tokenID, senderAddress, identity());
        final var fungibleTransfer =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers().get(0);

        assertTrue(fungibleTransfer.receiver().getAccountNum() > 0);
        assertEquals(2, fungibleTransfer.amount());
    }

    @Test
    void decodeTransferShouldThrowOnAmountOverflown() {
        final UnaryOperator<byte[]> identity = identity();
        assertThrows(
                ArithmeticException.class,
                () -> decodeERCTransfer(TRANSFER_LONG_OVERFLOWN, tokenID, senderAddress, identity));
    }

    @Test
    void decodeTransferFromFungibleInputUsingApprovalIfNotOwner() {
        final var notOwner = IdUtils.asAccount("0.0.1002");
        given(tokenAccessor.typeOf(any())).willReturn(TokenType.FUNGIBLE_COMMON);
        final var decodedInput =
                decodeERCTransferFrom(TRANSFER_FROM_FUNGIBLE_INPUT, tokenID, tokenAccessor, notOwner, identity());
        final var fungibleTransfer = decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertTrue(fungibleTransfer.get(0).receiver().getAccountNum() > 0);
        assertTrue(fungibleTransfer.get(1).sender().getAccountNum() > 0);
        assertTrue(fungibleTransfer.get(1).isApproval());
        assertEquals(5, fungibleTransfer.get(0).amount());
    }

    @Test
    void decodeHapiTransferFromFungibleInputUsingApprovalIfNotOwner() {
        final var notOwner = IdUtils.asAccount("0.0.1002");
        given(tokenAccessor.typeOf(any())).willReturn(TokenType.FUNGIBLE_COMMON);
        final var decodedInput =
                decodeERCTransferFrom(HAPI_TRANSFER_FROM_FUNGIBLE_INPUT, null, tokenAccessor, notOwner, identity());
        final var fungibleTransfer = decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        assertEquals(HTSTestsUtil.token, fungibleTransfer.get(0).getDenomination());
        assertEquals(fungibleTransfer.get(1).sender(), IdUtils.asAccount("0.0.1450"));
        assertTrue(fungibleTransfer.get(1).isApproval());
        assertEquals(fungibleTransfer.get(0).receiver(), IdUtils.asAccount("0.0.1451"));
        assertEquals(5, fungibleTransfer.get(0).amount());
    }

    @Test
    void decodeTransferFromFungibleInputStillUsesApprovalIfFromIsOperator() {
        final var owner = IdUtils.asAccount("0.0.1450");
        given(tokenAccessor.typeOf(any())).willReturn(TokenType.FUNGIBLE_COMMON);
        final var decodedInput =
                decodeERCTransferFrom(TRANSFER_FROM_FUNGIBLE_INPUT, tokenID, tokenAccessor, owner, identity());
        final var fungibleTransfer = decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertTrue(fungibleTransfer.get(0).receiver().getAccountNum() > 0);
        assertTrue(fungibleTransfer.get(1).sender().getAccountNum() > 0);
        assertTrue(fungibleTransfer.get(1).isApproval());
        assertEquals(5, fungibleTransfer.get(0).amount());
    }

    @Test
    void decodeHapiTransferFromFungibleInputStillUsesApprovalIfFromIsOperator() {
        final var owner = IdUtils.asAccount("0.0.1450");
        given(tokenAccessor.typeOf(any())).willReturn(TokenType.FUNGIBLE_COMMON);
        final var decodedInput =
                decodeERCTransferFrom(HAPI_TRANSFER_FROM_FUNGIBLE_INPUT, null, tokenAccessor, owner, identity());
        final var fungibleTransfer = decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertEquals(IdUtils.asToken("0.0.1"), fungibleTransfer.get(0).getDenomination());
        assertEquals(fungibleTransfer.get(1).sender(), IdUtils.asAccount("0.0.1450"));
        assertTrue(fungibleTransfer.get(1).isApproval());
        assertEquals(fungibleTransfer.get(0).receiver(), IdUtils.asAccount("0.0.1451"));
        assertEquals(5, fungibleTransfer.get(0).amount());
    }

    @Test
    void decodeTransferFromNonFungibleInputUsingApprovalIfNotOwner() {
        final var notOwner = IdUtils.asAccount("0.0.1002");
        given(tokenAccessor.typeOf(any())).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        final var decodedInput =
                decodeERCTransferFrom(TRANSFER_FROM_NON_FUNGIBLE_INPUT, tokenID, tokenAccessor, notOwner, identity());
        final var nftTransfer = decodedInput
                .tokenTransferWrappers()
                .get(0)
                .nftExchanges()
                .get(0)
                .asGrpc();

        assertTrue(nftTransfer.getSenderAccountID().getAccountNum() > 0);
        assertTrue(nftTransfer.getReceiverAccountID().getAccountNum() > 0);
        assertEquals(1, nftTransfer.getSerialNumber());
        assertTrue(nftTransfer.getIsApproval());
    }

    @Test
    void decodeHapiTransferFromNFTInputUsingApprovalIfNotOwner() {
        final var notOwner = IdUtils.asAccount("0.0.1002");
        given(tokenAccessor.typeOf(any())).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        final var decodedInput =
                decodeERCTransferFrom(HAPI_TRANSFER_FROM_NFT_INPUT, null, tokenAccessor, notOwner, identity());
        final var nftTransfer =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges().get(0);

        assertEquals(IdUtils.asToken("0.0.1"), nftTransfer.getTokenType());
        final var nftTransferAsGrpc = nftTransfer.asGrpc();
        assertEquals(nftTransferAsGrpc.getSenderAccountID(), IdUtils.asAccount("0.0.1450"));
        assertTrue(nftTransferAsGrpc.getIsApproval());
        assertEquals(nftTransferAsGrpc.getReceiverAccountID(), IdUtils.asAccount("0.0.1451"));
        assertEquals(5, nftTransferAsGrpc.getSerialNumber());
    }

    @Test
    void decodeTransferFromNonFungibleInputIfOwner() {
        final var owner = IdUtils.asAccount("0.0.1001");
        given(tokenAccessor.typeOf(any())).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        final var decodedInput =
                decodeERCTransferFrom(TRANSFER_FROM_NON_FUNGIBLE_INPUT, tokenID, tokenAccessor, owner, identity());
        final var nftTransfer = decodedInput
                .tokenTransferWrappers()
                .get(0)
                .nftExchanges()
                .get(0)
                .asGrpc();

        assertTrue(nftTransfer.getSenderAccountID().getAccountNum() > 0);
        assertTrue(nftTransfer.getReceiverAccountID().getAccountNum() > 0);
        assertEquals(1, nftTransfer.getSerialNumber());
        assertFalse(nftTransfer.getIsApproval());
    }

    @Test
    void decodeHapiTransferFromNFTIInputIfOwner() {
        final var owner = IdUtils.asAccount("0.0.1450");

        final var decodedInput =
                decodeERCTransferFrom(HAPI_TRANSFER_FROM_NFT_INPUT, null, tokenAccessor, owner, identity());
        final var nftTransfer =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges().get(0);

        assertEquals(IdUtils.asToken("0.0.1"), nftTransfer.getTokenType());
        final var nftTransferAsGrpc = nftTransfer.asGrpc();
        assertEquals(nftTransferAsGrpc.getSenderAccountID(), IdUtils.asAccount("0.0.1450"));
        assertFalse(nftTransferAsGrpc.getIsApproval());
        assertEquals(nftTransferAsGrpc.getReceiverAccountID(), IdUtils.asAccount("0.0.1451"));
        assertEquals(5, nftTransferAsGrpc.getSerialNumber());
    }

    @Test
    void decodeTransferFromShouldThrowOnAmountOverflown() {
        final var owner = IdUtils.asAccount("0.0.1450");
        final UnaryOperator<byte[]> identity = identity();
        assertThrows(
                ArithmeticException.class,
                () -> decodeERCTransferFrom(TRANSFER_FROM_LONG_OVERFLOWN, tokenID, tokenAccessor, owner, identity));
    }
}
