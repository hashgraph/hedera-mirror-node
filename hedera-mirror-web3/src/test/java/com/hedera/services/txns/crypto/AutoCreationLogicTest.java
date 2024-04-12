/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.txns.crypto;

import static com.hedera.services.jproto.JKey.mapKey;
import static com.hedera.services.store.models.Id.fromGrpcToken;
import static com.hedera.services.utils.EntityIdUtils.asEvmAddress;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.web3.ContextExtension;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.mirror.web3.evm.store.accessor.AccountDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.jproto.JKey;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.security.InvalidKeyException;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class AutoCreationLogicTest {

    public static final AccountID payer = asAccount("0.0.12345");
    public static final TokenID token = asToken("0.0.23456");
    private static final long initialTransfer = 16L;
    private static final AccountID created = asAccount("0.0.1234");
    private static final FeeObject fees = new FeeObject(1L, 2L, 3L);
    private static final long totalFee = 6L;
    private static final byte[] ECDSA_PUBLIC_KEY =
            Hex.decode("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
    private final Timestamp at = Timestamp.newBuilder().setSeconds(1_234_567L).build();

    @Mock
    private EntityDatabaseAccessor entityDatabaseAccessor;

    private Store store;

    @Mock
    private EntityAddressSequencer ids;

    @Mock
    private MirrorEvmContractAliases aliasManager;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private EvmProperties evmProperties;

    private AutoCreationLogic subject;

    @BeforeEach
    void setUp() {
        final List<DatabaseAccessor<Object, ?>> accessors =
                List.of(new AccountDatabaseAccessor(entityDatabaseAccessor, null, null, null, null, null, null));
        final var stackedStateFrames = new StackedStateFrames(accessors);
        store = new StoreImpl(stackedStateFrames);
        subject = new AutoCreationLogic(feeCalculator, evmProperties, syntheticTxnFactory, aliasManager);
    }

    @Test
    void doesntAutoCreateWhenTokenTransferToAliasFeatureDisabled() {
        Key aPrimitiveKey = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                .build();
        ByteString edKeyAlias = aPrimitiveKey.toByteString();

        given(evmProperties.isLazyCreationEnabled()).willReturn(false);

        final var input = wellKnownTokenChange(edKeyAlias);
        final var changes = List.of(input);

        final var result = subject.create(input, at, store, ids, changes);
        assertEquals(NOT_SUPPORTED, result.getLeft());
    }

    @Test
    void cannotCreateAccountFromUnaliasedChange() {
        final var input = BalanceChange.changingHbar(
                AccountAmount.newBuilder()
                        .setAmount(initialTransfer)
                        .setAccountID(payer)
                        .build(),
                payer);
        final var changes = List.of(input);

        final var result = assertThrows(IllegalStateException.class, () -> subject.create(input, at, store, ids, changes));
        assertTrue(result.getMessage().contains("Cannot auto-create an account from unaliased change"));
    }

    @Test
    void hollowAccountWithHbarChangeWorks() throws InvalidProtocolBufferException, InvalidKeyException {
        final var jKey = mapKey(Key.parseFrom(ECDSA_PUBLIC_KEY));
        final var evmAddressAlias =
                ByteString.copyFrom(EthSigsUtils.recoverAddressFromPubKey(jKey.getECDSASecp256k1Key()));
        TransactionBody.Builder syntheticHollowCreation =
                TransactionBody.newBuilder().setCryptoCreateAccount(CryptoCreateTransactionBody.newBuilder());

        given(syntheticTxnFactory.createHollowAccount(evmAddressAlias, 0L, 0)).willReturn(syntheticHollowCreation);
        given(ids.getNewAccountId()).willReturn(created);
        given(feeCalculator.computeFee(any(), any(), eq(at))).willReturn(fees);

        final var input = wellKnownChange(evmAddressAlias);
        final var changes = List.of(input);

        store.wrap();
        final var result = subject.create(input, at, store, ids, changes);

        assertEquals(initialTransfer, input.getAggregatedUnits());
        assertEquals(initialTransfer, input.getNewBalance());
        assertEquals(Pair.of(OK, totalFee * 2), result);
        verify(aliasManager)
                .link(
                        Address.wrap(Bytes.wrap(evmAddressAlias.toByteArray())),
                        Address.wrap(Bytes.wrap(asEvmAddress(created))));
    }

    @Test
    void happyPathWithFungibleTokenChangeWorks() throws InvalidKeyException {
        Key aPrimitiveKey = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                .build();
        ByteString edKeyAlias = aPrimitiveKey.toByteString();
        TransactionBody.Builder syntheticEDAliasCreation = TransactionBody.newBuilder()
                .setCryptoCreateAccount(CryptoCreateTransactionBody.newBuilder().setAlias(edKeyAlias));

        given(ids.getNewAccountId()).willReturn(created);
        given(feeCalculator.computeFee(any(), any(), eq(at))).willReturn(fees);
        given(evmProperties.isLazyCreationEnabled()).willReturn(true);
        given(syntheticTxnFactory.createAccount(edKeyAlias, aPrimitiveKey, 0L, 1))
                .willReturn(syntheticEDAliasCreation);

        final var input = wellKnownTokenChange(edKeyAlias);
        final var changes = List.of(input);

        store.wrap();
        final var result = subject.create(input, at, store, ids, changes);

        assertEquals(initialTransfer, input.getAggregatedUnits());
        verify(aliasManager)
                .maybeLinkEvmAddress(JKey.mapKey(aPrimitiveKey), Address.wrap(Bytes.wrap(asEvmAddress(created))));
        assertEquals(Pair.of(OK, totalFee), result);
    }

    @Test
    void analyzesTokenTransfersInChangesForAutoCreation() {
        final Key aPrimitiveKey = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                .build();
        final ByteString edKeyAlias = aPrimitiveKey.toByteString();
        final TransactionBody.Builder syntheticEDAliasCreation = TransactionBody.newBuilder()
                .setCryptoCreateAccount(CryptoCreateTransactionBody.newBuilder().setAlias(edKeyAlias));
        given(ids.getNewAccountId()).willReturn(created);
        given(feeCalculator.computeFee(any(), any(), eq(at))).willReturn(fees);
        given(evmProperties.isLazyCreationEnabled()).willReturn(true);
        given(syntheticTxnFactory.createAccount(edKeyAlias, aPrimitiveKey, 0L, 2))
                .willReturn(syntheticEDAliasCreation);

        final var input1 = wellKnownTokenChange(edKeyAlias);
        final var input2 = anotherTokenChange();
        final var changes = List.of(input1, input2);

        store.wrap();
        final var result = subject.create(input1, at, store, ids, changes);
        assertEquals(Pair.of(OK, totalFee), result);

        assertEquals(16L, input1.getAggregatedUnits());
        assertEquals(1, subject.getTokenAliasMap().size());
        assertEquals(2, subject.getTokenAliasMap().get(edKeyAlias).size());

        /* ---- clear tokenAliasMap */
        subject.reset();
        assertEquals(0, subject.getTokenAliasMap().size());
    }

    private BalanceChange wellKnownTokenChange(final ByteString alias) {
        return BalanceChange.changingFtUnits(
                fromGrpcToken(token),
                token,
                AccountAmount.newBuilder()
                        .setAmount(initialTransfer)
                        .setAccountID(AccountID.newBuilder().setAlias(alias).build())
                        .build(),
                payer);
    }

    private BalanceChange wellKnownChange(final ByteString alias) {
        return BalanceChange.changingHbar(
                AccountAmount.newBuilder()
                        .setAmount(initialTransfer)
                        .setAccountID(AccountID.newBuilder().setAlias(alias).build())
                        .build(),
                payer);
    }

    private BalanceChange anotherTokenChange() {
        Key primitiveKey = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                .build();
        final TokenID token1 = IdUtils.asToken("0.0.123456");
        return BalanceChange.changingFtUnits(
                fromGrpcToken(token1),
                token1,
                AccountAmount.newBuilder()
                        .setAmount(initialTransfer)
                        .setAccountID(
                                AccountID.newBuilder().setAlias(primitiveKey.toByteString()).build())
                        .build(),
                payer);
    }
}
