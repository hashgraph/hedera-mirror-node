/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.evm.account.AccountAccessorImpl.EVM_ADDRESS_SIZE;
import static com.hedera.services.fees.calculation.utils.AccessorBasedUsages.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.store.contracts.precompile.SyntheticTxnFactory.AUTO_MEMO;
import static com.hedera.services.store.contracts.precompile.codec.Association.multiAssociation;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.EMPTY_KEY;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.MintWrapper;
import com.hedera.services.store.contracts.precompile.codec.WipeWrapper;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenID;
import java.security.InvalidKeyException;
import java.util.List;
import org.apache.commons.codec.DecoderException;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
;

@ExtendWith(MockitoExtension.class)
class SyntheticTxnFactoryTest {
    private SyntheticTxnFactory subject = new SyntheticTxnFactory();

    @Test
    void createsExpectedCryptoCreateWithEDKeyAlias() {
        final var balance = 10L;
        final var bytes = new byte[33];
        bytes[0] = 0x02;
        final Key key =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(bytes)).build();
        final var alias = key.toByteString();
        final var result = subject.createAccount(alias, key, balance, 0);
        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(AUTO_MEMO, txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(0L, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
        assertEquals(
                key.toByteString(), txnBody.getCryptoCreateAccount().getKey().toByteString());
        assertEquals(alias, txnBody.getCryptoCreateAccount().getAlias());
    }

    @Test
    void createsExpectedCryptoCreateWithECKeyAlias() throws InvalidKeyException, DecoderException {
        final var balance = 10L;
        final var bytes = new byte[33];
        bytes[0] = 0x02;
        final Key key =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(bytes)).build();
        final var alias = key.toByteString();
        final var evmAddress = ByteString.copyFrom(
                EthSigsUtils.recoverAddressFromPubKey(JKey.mapKey(key).getECDSASecp256k1Key()));
        final var result = subject.createAccount(alias, key, balance, 0);
        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(AUTO_MEMO, txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(0L, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
        assertEquals(
                key.toByteString(), txnBody.getCryptoCreateAccount().getKey().toByteString());
        assertEquals(alias, txnBody.getCryptoCreateAccount().getAlias());
    }

    @Test
    void createsExpectedHollowAccountCreate() {
        final var balance = 10L;
        final var evmAddressAlias = ByteString.copyFrom(Hex.decode("a94f5374fce5edbc8e2a8697c15331677e6ebf0b"));
        final var result = subject.createHollowAccount(evmAddressAlias, balance);
        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(asKeyUnchecked(EMPTY_KEY), txnBody.getCryptoCreateAccount().getKey());
        assertEquals(evmAddressAlias, txnBody.getCryptoCreateAccount().getAlias());
        assertEquals(
                EVM_ADDRESS_SIZE, txnBody.getCryptoCreateAccount().getAlias().size());
        assertEquals("lazy-created account", txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(0L, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
    }

    @Test
    void fungibleTokenChangeAddsAutoAssociations() {
        final var balance = 10L;
        final var bytes = new byte[33];
        bytes[0] = 0x02;
        final Key key =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(bytes)).build();
        final var alias = key.toByteString();
        final var result = subject.createAccount(alias, key, balance, 1);
        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(AUTO_MEMO, txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(1, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
        assertEquals(
                key.toByteString(), txnBody.getCryptoCreateAccount().getKey().toByteString());
        assertEquals(alias, txnBody.getCryptoCreateAccount().getAlias());
    }

    @Test
    void createsExpectedAssociations() {
        final var tokens = List.of(fungible, nonFungible);
        final var associations = multiAssociation(a, tokens);

        final var result = subject.createAssociate(associations);
        final var txnBody = result.build();

        assertEquals(a, txnBody.getTokenAssociate().getAccount());
        assertEquals(tokens, txnBody.getTokenAssociate().getTokensList());
    }

    @Test
    void createsExpectedDissociations() {
        final var tokens = List.of(fungible, nonFungible);
        final var associations = Dissociation.multiDissociation(a, tokens);

        final var result = subject.createDissociate(associations);
        final var txnBody = result.build();

        assertEquals(a, txnBody.getTokenDissociate().getAccount());
        assertEquals(tokens, txnBody.getTokenDissociate().getTokensList());
    }

    @Test
    void createsExpectedNftMint() {
        final var nftMints = MintWrapper.forNonFungible(nonFungible, newMetadata);

        final var result = subject.createMint(nftMints);
        final var txnBody = result.build();

        assertEquals(nonFungible, txnBody.getTokenMint().getToken());
        assertEquals(newMetadata, txnBody.getTokenMint().getMetadataList());
    }

    @Test
    void createsExpectedNftBurn() {
        final var nftBurns = BurnWrapper.forNonFungible(nonFungible, targetSerialNos);

        final var result = subject.createBurn(nftBurns);
        final var txnBody = result.build();

        assertEquals(nonFungible, txnBody.getTokenBurn().getToken());
        assertEquals(targetSerialNos, txnBody.getTokenBurn().getSerialNumbersList());
    }

    @Test
    void createsExpectedFungibleMint() {
        final var amount = 1234L;
        final var funMints = MintWrapper.forFungible(fungible, amount);

        final var result = subject.createMint(funMints);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenMint().getToken());
        assertEquals(amount, txnBody.getTokenMint().getAmount());
    }

    @Test
    void createsExpectedFungibleBurn() {
        final var amount = 1234L;
        final var funBurns = BurnWrapper.forFungible(fungible, amount);

        final var result = subject.createBurn(funBurns);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenBurn().getToken());
        assertEquals(amount, txnBody.getTokenBurn().getAmount());
    }

    @Test
    void createsExpectedGrantKycCall() {
        final var grantWrapper = new GrantRevokeKycWrapper<>(fungible, a);
        final var result = subject.createGrantKyc(grantWrapper);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenGrantKyc().getToken());
        assertEquals(a, txnBody.getTokenGrantKyc().getAccount());
    }

    @Test
    void createsExpectedFungibleWipe() {
        final var amount = 1234L;
        final var fungibleWipe = WipeWrapper.forFungible(fungible, a, amount);

        final var result = subject.createWipe(fungibleWipe);
        final var txnBody = result.build();

        assertEquals(fungible, txnBody.getTokenWipe().getToken());
        assertEquals(amount, txnBody.getTokenWipe().getAmount());
        assertEquals(a, txnBody.getTokenWipe().getAccount());
    }

    @Test
    void createsExpectedNftWipe() {
        final var nftWipe = WipeWrapper.forNonFungible(nonFungible, a, targetSerialNos);

        final var result = subject.createWipe(nftWipe);
        final var txnBody = result.build();

        assertEquals(nonFungible, txnBody.getTokenWipe().getToken());
        assertEquals(a, txnBody.getTokenWipe().getAccount());
        assertEquals(targetSerialNos, txnBody.getTokenWipe().getSerialNumbersList());
    }

    private static final long serialNo = 100;
    private static final long secondAmount = 200;
    private static final long newExpiry = 1_234_567L;
    private final EntityNum contractNum = EntityNum.fromLong(666);
    private final EntityNum accountNum = EntityNum.fromLong(1234);
    private static final AccountID a = IdUtils.asAccount("0.0.2");
    private static final AccountID b = IdUtils.asAccount("0.0.3");
    private static final AccountID c = IdUtils.asAccount("0.0.4");
    private static final TokenID fungible = IdUtils.asToken("0.0.555");
    private static final TokenID nonFungible = IdUtils.asToken("0.0.666");
    private static final List<Long> targetSerialNos = List.of(1L, 2L, 3L);
    private static final List<ByteString> newMetadata =
            List.of(ByteString.copyFromUtf8("AAA"), ByteString.copyFromUtf8("BBB"), ByteString.copyFromUtf8("CCC"));
    private static final long valueInTinyBars = 123;
}
