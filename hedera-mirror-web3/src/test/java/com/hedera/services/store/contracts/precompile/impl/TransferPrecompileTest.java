/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.wrapUnsafely;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.addNftExchanges;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.addSignedAdjustments;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeCryptoTransfer;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeCryptoTransferV2;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeHbarTransfers;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeTokenTransfer;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeTransferNFT;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeTransferNFTs;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeTransferToken;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeTransferTokens;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

import com.hedera.services.store.contracts.precompile.FungibleTokenTransfer;
import com.hedera.services.store.contracts.precompile.HbarTransfer;
import com.hedera.services.store.contracts.precompile.NftExchange;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.List;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferPrecompileTest {

    private static final Bytes POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT = Bytes.fromHexString(
            "0x189a554c00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004a4000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004a1000000000000000000000000000000000000000000000000000000000000002b000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004a100000000000000000000000000000000000000000000000000000000000004a10000000000000000000000000000000000000000000000000000000000000048");

    private static final Bytes NEGATIVE_FUNGIBLE_AMOUNT_CRYPTO_TRANSFER_INPUT = Bytes.fromHexString(
            "0x189a554c00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004c0000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004bdffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffce0000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes TRANSFER_TOKEN_INPUT = Bytes.fromHexString(
            "0xeca3691700000000000000000000000000000000000000000000000000000000000004380000000000000000000000000000000000000000000000000000000000000435000000000000000000000000000000000000000000000000000000000000043a0000000000000000000000000000000000000000000000000000000000000014");

    private static final Bytes NEGATIVE_AMOUNT_TRANSFER_TOKEN_INPUT = Bytes.fromHexString(
            "0xeca3691700000000000000000000000000000000000000000000000000000000000004380000000000000000000000000000000000000000000000000000000000000435000000000000000000000000000000000000000000000000000000000000043afffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff000");

    private static final Bytes POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT = Bytes.fromHexString(
            "0x82bba4930000000000000000000000000000000000000000000000000000000000000444000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000044100000000000000000000000000000000000000000010000000000000000004410000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000014");

    private static final Bytes POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT = Bytes.fromHexString(
            "0x82bba49300000000000000000000000000000000000000000000000000000000000004d8000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000004d500000000000000000000000000000000000000000000000000000000000004d500000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000014ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffec");

    private static final Bytes TRANSFER_NFT_INPUT = Bytes.fromHexString(
            "0x5cfc901100000000000000000000000000000000000000000000000000000000000004680000000000000000000000000000000000000000000000000000000000000465000000000000000000000000000000000000000000000000000000000000046a0000000000000000000000000000000000000000000000000000000000000065");

    private static final Bytes TRANSFER_NFTS_INPUT = Bytes.fromHexString(
            "0x2c4ba191000000000000000000000000000000000000000000000000000000000000047a000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000001400000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000047700000000000000000000000000000000000000000000000000000000000004770000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000047c000000000000000000000000000000000000000000000010000000000000047c0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000ea");

    private static final Bytes CRYPTO_TRANSFER_HBAR_ONLY_INPUT = Bytes.fromHexString(
            "0x0e71804f00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000140000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff600000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes CRYPTO_TRANSFER_FUNGIBLE_INPUT = Bytes.fromHexString(
            "0x0e71804f00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000014000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000005fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff600000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes CRYPTO_TRANSFER_NFT_INPUT = Bytes.fromHexString(
            "0x0e71804f000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000800000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000700000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000090000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000b00000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes CRYPTO_TRANSFER_HBAR_FUNGIBLE_INPUT = Bytes.fromHexString(
            "0x0e71804f00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000140000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff600000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000014000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000005fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff600000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes CRYPTO_TRANSFER_HBAR_NFT_INPUT = Bytes.fromHexString(
            "0x0e71804f00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000140000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff6000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000700000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000090000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000b00000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000000");

    private MockedStatic<TransferPrecompile> transferPrecompile;

    @BeforeEach
    void setup() {
        transferPrecompile = Mockito.mockStatic(TransferPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        transferPrecompile.close();
    }

    @Test
    void decodeCryptoTransferPositiveFungibleAmountAndNftTransfer() {
        transferPrecompile
                .when(() -> decodeCryptoTransfer(
                        POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT, identity()))
                .thenCallRealMethod();
        transferPrecompile.when(() -> decodeTokenTransfer(any(), any(), any())).thenCallRealMethod();
        transferPrecompile.when(() -> decodeHbarTransfers(any(), any(), any())).thenCallRealMethod();
        final var decodedInput =
                decodeCryptoTransfer(POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT, identity());
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nftExchanges = decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        assertNotNull(fungibleTransfers);
        assertNotNull(nftExchanges);
        assertEquals(1, fungibleTransfers.size());
        assertEquals(1, nftExchanges.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertEquals(43, fungibleTransfers.get(0).receiverAdjustment().getAmount());
        assertTrue(nftExchanges.get(0).getTokenType().getTokenNum() > 0);
        assertTrue(nftExchanges.get(0).asGrpc().getReceiverAccountID().getAccountNum() > 0);
        assertTrue(nftExchanges.get(0).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertEquals(72, nftExchanges.get(0).asGrpc().getSerialNumber());
    }

    @Test
    void decodeCryptoTransferNegativeFungibleAmount() {
        transferPrecompile
                .when(() -> decodeCryptoTransfer(NEGATIVE_FUNGIBLE_AMOUNT_CRYPTO_TRANSFER_INPUT, identity()))
                .thenCallRealMethod();
        transferPrecompile.when(() -> decodeTokenTransfer(any(), any(), any())).thenCallRealMethod();
        transferPrecompile.when(() -> decodeHbarTransfers(any(), any(), any())).thenCallRealMethod();
        final var decodedInput = decodeCryptoTransfer(NEGATIVE_FUNGIBLE_AMOUNT_CRYPTO_TRANSFER_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertNotNull(fungibleTransfers);
        assertEquals(1, fungibleTransfers.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(0).sender().getAccountNum() > 0);
        assertEquals(50, fungibleTransfers.get(0).amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferTokenInput() {
        transferPrecompile
                .when(() -> decodeTransferToken(TRANSFER_TOKEN_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferToken(TRANSFER_TOKEN_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfer =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers().get(0);

        assertTrue(fungibleTransfer.sender().getAccountNum() > 0);
        assertTrue(fungibleTransfer.receiver().getAccountNum() > 0);
        assertTrue(fungibleTransfer.getDenomination().getTokenNum() > 0);
        assertEquals(20, fungibleTransfer.amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferTokenWithNegativeInput() {
        transferPrecompile
                .when(() -> decodeTransferToken(NEGATIVE_AMOUNT_TRANSFER_TOKEN_INPUT, identity()))
                .thenCallRealMethod();
        UnaryOperator<byte[]> identity = identity();
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> decodeTransferToken(NEGATIVE_AMOUNT_TRANSFER_TOKEN_INPUT, identity));

        assertEquals("Amount must be non-negative", exception.getMessage());
    }

    @Test
    void decodeTransferTokensPositiveAmounts() {
        transferPrecompile
                .when(() -> decodeTransferTokens(POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT, identity()))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> addSignedAdjustments(any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferTokens(POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        final var nonLongZeroAlias =
                wrapUnsafely(java.util.HexFormat.of().parseHex("0000000000000000001000000000000000000441"));

        assertEquals(2, fungibleTransfers.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(1).getDenomination().getTokenNum() > 0);
        assertNull(fungibleTransfers.get(0).sender());
        assertNull(fungibleTransfers.get(1).sender());
        assertEquals(
                AccountID.newBuilder().setAlias(nonLongZeroAlias).build(),
                fungibleTransfers.get(1).receiver());
        assertEquals(10, fungibleTransfers.get(0).amount());
        assertEquals(20, fungibleTransfers.get(1).amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferTokensPositiveAmountsWithAliases() {
        transferPrecompile
                .when(() -> decodeTransferTokens(POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT, identity()))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> addSignedAdjustments(any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferTokens(POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        final var longZeroAlias = wrapUnsafely(EntityIdUtils.asTypedEvmAddress(
                        AccountID.newBuilder().setAccountNum(1089).build())
                .toArrayUnsafe());

        final var nonLongZeroAlias =
                wrapUnsafely(java.util.HexFormat.of().parseHex("0000000000000000001000000000000000000441"));

        assertEquals(2, fungibleTransfers.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(1).getDenomination().getTokenNum() > 0);
        assertNull(fungibleTransfers.get(0).sender());
        assertNull(fungibleTransfers.get(1).sender());
        assertEquals(
                AccountID.newBuilder().setAlias(longZeroAlias).build(),
                fungibleTransfers.get(0).receiver());
        assertEquals(
                AccountID.newBuilder().setAlias(nonLongZeroAlias).build(),
                fungibleTransfers.get(1).receiver());
        assertEquals(10, fungibleTransfers.get(0).amount());
        assertEquals(20, fungibleTransfers.get(1).amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferTokensPositiveNegativeAmount() {
        transferPrecompile
                .when(() -> decodeTransferTokens(POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT, identity()))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> addSignedAdjustments(any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferTokens(POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertEquals(2, fungibleTransfers.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(1).getDenomination().getTokenNum() > 0);
        assertNull(fungibleTransfers.get(0).sender());
        assertNull(fungibleTransfers.get(1).receiver());
        assertTrue(fungibleTransfers.get(1).sender().getAccountNum() > 0);
        assertEquals(20, fungibleTransfers.get(0).amount());
        assertEquals(20, fungibleTransfers.get(1).amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferNFTInput() {
        transferPrecompile
                .when(() -> decodeTransferNFT(TRANSFER_NFT_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferNFT(TRANSFER_NFT_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var nonFungibleTransfer =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges().get(0);

        assertTrue(nonFungibleTransfer.asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfer.asGrpc().getReceiverAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfer.getTokenType().getTokenNum() > 0);
        assertEquals(101, nonFungibleTransfer.asGrpc().getSerialNumber());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferNFTsInput() {
        transferPrecompile
                .when(() -> decodeTransferNFTs(TRANSFER_NFTS_INPUT, identity()))
                .thenCallRealMethod();
        transferPrecompile.when(() -> decodeTokenTransfer(any(), any(), any())).thenCallRealMethod();
        transferPrecompile.when(() -> decodeHbarTransfers(any(), any(), any())).thenCallRealMethod();
        transferPrecompile
                .when(() -> addNftExchanges(any(), any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferNFTs(TRANSFER_NFTS_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var nonFungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        final var nonLongZeroAlias =
                wrapUnsafely(java.util.HexFormat.of().parseHex("000000000000000000000010000000000000047c"));
        final var expectedReceiver =
                AccountID.newBuilder().setAlias(nonLongZeroAlias).build();

        assertEquals(2, nonFungibleTransfers.size());
        assertTrue(nonFungibleTransfers.get(0).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertEquals(expectedReceiver, nonFungibleTransfers.get(1).asGrpc().getReceiverAccountID());
        assertTrue(nonFungibleTransfers.get(0).getTokenType().getTokenNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).getTokenType().getTokenNum() > 0);
        assertEquals(123, nonFungibleTransfers.get(0).asGrpc().getSerialNumber());
        assertEquals(234, nonFungibleTransfers.get(1).asGrpc().getSerialNumber());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferNFTsInputWithAlias() {
        transferPrecompile
                .when(() -> decodeTransferNFTs(TRANSFER_NFTS_INPUT, identity()))
                .thenCallRealMethod();
        transferPrecompile
                .when(() -> addNftExchanges(any(), any(), any(), any(), any()))
                .thenCallRealMethod();
        transferPrecompile.when(() -> decodeTokenTransfer(any(), any(), any())).thenCallRealMethod();
        transferPrecompile.when(() -> decodeHbarTransfers(any(), any(), any())).thenCallRealMethod();
        final var decodedInput = decodeTransferNFTs(TRANSFER_NFTS_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var nonFungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        final var alias = wrapUnsafely(EntityIdUtils.asTypedEvmAddress(
                        AccountID.newBuilder().setAccountNum(1148).build())
                .toArrayUnsafe());
        final var nonLongZeroAlias =
                wrapUnsafely(java.util.HexFormat.of().parseHex("000000000000000000000010000000000000047c"));

        assertEquals(2, nonFungibleTransfers.size());
        assertTrue(nonFungibleTransfers.get(0).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).asGrpc().getSenderAccountID().getAccountNum() > 0);
        final var expectedReceiver = AccountID.newBuilder().setAlias(alias).build();
        final var secondExpectedReceiver =
                AccountID.newBuilder().setAlias(nonLongZeroAlias).build();
        assertEquals(expectedReceiver, nonFungibleTransfers.get(0).asGrpc().getReceiverAccountID());
        assertEquals(
                secondExpectedReceiver, nonFungibleTransfers.get(1).asGrpc().getReceiverAccountID());
        assertTrue(nonFungibleTransfers.get(0).getTokenType().getTokenNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).getTokenType().getTokenNum() > 0);
        assertEquals(123, nonFungibleTransfers.get(0).asGrpc().getSerialNumber());
        assertEquals(234, nonFungibleTransfers.get(1).asGrpc().getSerialNumber());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeCryptoTransferHBarOnlyTransfer() {
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(CRYPTO_TRANSFER_HBAR_ONLY_INPUT, identity()))
                .thenCallRealMethod();
        transferPrecompile.when(() -> decodeHbarTransfers(any(), any(), any())).thenCallRealMethod();
        final var decodedInput = decodeCryptoTransferV2(CRYPTO_TRANSFER_HBAR_ONLY_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var tokenTransferWrappers = decodedInput.tokenTransferWrappers();

        assertNotNull(hbarTransfers);
        assertNotNull(tokenTransferWrappers);
        assertEquals(2, hbarTransfers.size());
        assertEquals(0, tokenTransferWrappers.size());

        assertHbarTransfers(hbarTransfers);
    }

    @Test
    void decodeCryptoTransferFungibleTransfer() {
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(CRYPTO_TRANSFER_FUNGIBLE_INPUT, identity()))
                .thenCallRealMethod();
        transferPrecompile.when(() -> decodeTokenTransfer(any(), any(), any())).thenCallRealMethod();
        transferPrecompile.when(() -> decodeHbarTransfers(any(), any(), any())).thenCallRealMethod();
        final var decodedInput = decodeCryptoTransferV2(CRYPTO_TRANSFER_FUNGIBLE_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nonFungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        assertNotNull(hbarTransfers);
        assertNotNull(fungibleTransfers);
        assertNotNull(nonFungibleTransfers);
        assertEquals(0, hbarTransfers.size());
        assertEquals(2, fungibleTransfers.size());
        assertEquals(0, nonFungibleTransfers.size());

        assertFungibleTransfers(fungibleTransfers);
    }

    @Test
    void decodeCryptoTransferNftTransfer() {
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(CRYPTO_TRANSFER_NFT_INPUT, identity()))
                .thenCallRealMethod();
        transferPrecompile.when(() -> decodeTokenTransfer(any(), any(), any())).thenCallRealMethod();
        transferPrecompile.when(() -> decodeHbarTransfers(any(), any(), any())).thenCallRealMethod();
        final var decodedInput = decodeCryptoTransferV2(CRYPTO_TRANSFER_NFT_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nonFungibleTransfers1 =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges();
        final var nonFungibleTransfers2 =
                decodedInput.tokenTransferWrappers().get(1).nftExchanges();

        assertNotNull(hbarTransfers);
        assertNotNull(fungibleTransfers);
        assertNotNull(nonFungibleTransfers1);
        assertNotNull(nonFungibleTransfers2);
        assertEquals(0, hbarTransfers.size());
        assertEquals(0, fungibleTransfers.size());
        assertEquals(1, nonFungibleTransfers1.size());
        assertEquals(1, nonFungibleTransfers2.size());

        assertNftTransfers(nonFungibleTransfers1, nonFungibleTransfers2);
    }

    @Test
    void decodeCryptoTransferHbarFungibleTransfer() {
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(CRYPTO_TRANSFER_HBAR_FUNGIBLE_INPUT, identity()))
                .thenCallRealMethod();
        transferPrecompile.when(() -> decodeTokenTransfer(any(), any(), any())).thenCallRealMethod();
        transferPrecompile.when(() -> decodeHbarTransfers(any(), any(), any())).thenCallRealMethod();
        final var decodedInput = decodeCryptoTransferV2(CRYPTO_TRANSFER_HBAR_FUNGIBLE_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nonFungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        assertNotNull(hbarTransfers);
        assertNotNull(fungibleTransfers);
        assertNotNull(nonFungibleTransfers);
        assertEquals(2, hbarTransfers.size());
        assertEquals(2, fungibleTransfers.size());
        assertEquals(0, nonFungibleTransfers.size());

        assertHbarTransfers(hbarTransfers);
        assertFungibleTransfers(fungibleTransfers);
    }

    @Test
    void decodeCryptoTransferHbarNFTTransfer() {
        transferPrecompile
                .when(() -> decodeCryptoTransferV2(CRYPTO_TRANSFER_HBAR_NFT_INPUT, identity()))
                .thenCallRealMethod();
        transferPrecompile.when(() -> decodeTokenTransfer(any(), any(), any())).thenCallRealMethod();
        transferPrecompile.when(() -> decodeHbarTransfers(any(), any(), any())).thenCallRealMethod();
        final var decodedInput = decodeCryptoTransferV2(CRYPTO_TRANSFER_HBAR_NFT_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nonFungibleTransfers1 =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges();
        final var nonFungibleTransfers2 =
                decodedInput.tokenTransferWrappers().get(1).nftExchanges();

        assertNotNull(hbarTransfers);
        assertNotNull(fungibleTransfers);
        assertNotNull(nonFungibleTransfers1);
        assertNotNull(nonFungibleTransfers2);
        assertEquals(2, hbarTransfers.size());
        assertEquals(0, fungibleTransfers.size());
        assertEquals(1, nonFungibleTransfers1.size());
        assertEquals(1, nonFungibleTransfers2.size());

        assertHbarTransfers(hbarTransfers);
        assertNftTransfers(nonFungibleTransfers1, nonFungibleTransfers2);
    }

    private void assertHbarTransfers(final List<HbarTransfer> hbarTransfers) {
        assertEquals(10, hbarTransfers.get(0).amount());
        assertFalse(hbarTransfers.get(0).isApproval());
        assertNull(hbarTransfers.get(0).sender());
        assertEquals(1, hbarTransfers.get(0).receiver().getAccountNum());

        assertEquals(10, hbarTransfers.get(1).amount());
        assertFalse(hbarTransfers.get(1).isApproval());
        assertNull(hbarTransfers.get(1).receiver());
        assertEquals(2, hbarTransfers.get(1).sender().getAccountNum());
    }

    private void assertFungibleTransfers(final List<FungibleTokenTransfer> fungibleTokenTransfers) {
        assertEquals(10, fungibleTokenTransfers.get(0).amount());
        assertTrue(fungibleTokenTransfers.get(0).isApproval());
        assertNull(fungibleTokenTransfers.get(0).sender());
        assertEquals(3, fungibleTokenTransfers.get(0).getDenomination().getTokenNum());

        assertEquals(10, fungibleTokenTransfers.get(1).amount());
        assertFalse(fungibleTokenTransfers.get(1).isApproval());
        assertNull(fungibleTokenTransfers.get(1).receiver());
        assertEquals(5, fungibleTokenTransfers.get(1).sender().getAccountNum());
        assertEquals(3, fungibleTokenTransfers.get(1).getDenomination().getTokenNum());
    }

    private void assertNftTransfers(final List<NftExchange> nftExchanges1, final List<NftExchange> nftExchanges2) {
        assertEquals(6, nftExchanges1.get(0).getTokenType().getTokenNum());
        assertEquals(2, nftExchanges1.get(0).getSerialNo());
        assertEquals(7, nftExchanges1.get(0).asGrpc().getSenderAccountID().getAccountNum());
        assertEquals(8, nftExchanges1.get(0).asGrpc().getReceiverAccountID().getAccountNum());
        assertTrue(nftExchanges1.get(0).isApproval());

        assertEquals(9, nftExchanges2.get(0).getTokenType().getTokenNum());
        assertEquals(3, nftExchanges2.get(0).getSerialNo());
        assertEquals(10, nftExchanges2.get(0).asGrpc().getSenderAccountID().getAccountNum());
        assertEquals(11, nftExchanges2.get(0).asGrpc().getReceiverAccountID().getAccountNum());
        assertFalse(nftExchanges2.get(0).isApproval());
    }
}
