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

package com.hedera.services.txns.crypto;

import static com.hedera.services.store.models.Id.fromGrpcToken;
import static com.hedera.services.utils.EntityIdUtils.asToken;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.mirror.web3.evm.store.accessor.AccountDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.ledger.BalanceChange;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutoCreationLogicTest {
    private final Timestamp at = Timestamp.newBuilder().setSeconds(1_234_567L).build();

    @Mock
    private EntityDatabaseAccessor entityDatabaseAccessor;

    private Store store;

    @Mock
    private EntityAddressSequencer ids;

    @Mock
    private MirrorEntityAccess mirrorEntityAccess;

    private MirrorEvmContractAliases aliasManager;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private EvmProperties evmProperties;

    private AutoCreationLogic subject;

    @BeforeEach
    void setUp() throws Exception {
        aliasManager = new MirrorEvmContractAliases(mirrorEntityAccess);
        List<DatabaseAccessor<Object, ?>> accessors =
                List.of(new AccountDatabaseAccessor(entityDatabaseAccessor, null, null, null, null, null));
        store = new StoreImpl(accessors);
        store.wrap();
        subject = new AutoCreationLogic(feeCalculator, evmProperties);

        final Key key = Key.parseFrom(ECDSA_PUBLIC_KEY);
        aPrimitiveKey = key;
        edKeyAlias = aPrimitiveKey.toByteString();
    }

    @Test
    void doesntAutoCreateWhenTokenTransferToAliasFeatureDisabled() {
        given(evmProperties.isLazyCreationEnabled()).willReturn(false);

        final var input = wellKnownTokenChange(edKeyAlias);

        final var result = subject.create(input, at, store, ids, aliasManager);
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
        final var result =
                assertThrows(IllegalStateException.class, () -> subject.create(input, at, store, ids, aliasManager));
        assertTrue(result.getMessage().contains("Cannot auto-create an account from unaliased change"));
    }

    @Test
    void createsAsExpected() {
        // given
        given(ids.getNewAccountId()).willReturn(created);
        given(feeCalculator.computeFee(any(), any(), eq(store), eq(at))).willReturn(fees);
        given(evmProperties.isLazyCreationEnabled()).willReturn(true);

        // when
        final var input1 = wellKnownTokenChange(edKeyAlias);
        final var result = subject.create(input1, at, store, ids, aliasManager);

        // then
        final var expected = Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b");
        assertEquals(16L, input1.getAggregatedUnits());
        assertTrue(aliasManager.isInUse(expected));
        assertNotNull(store.getAccount(expected, OnMissing.DONT_THROW));

        assertEquals(Pair.of(OK, totalFee), result);
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

    private static final long initialTransfer = 16L;
    private static Key aPrimitiveKey;

    private static ByteString edKeyAlias;

    private static final AccountID created = asAccount("0.0.1234");
    public static final AccountID payer = asAccount("0.0.12345");
    private static final FeeObject fees = new FeeObject(1L, 2L, 3L);
    private static final long totalFee = 6L;

    public static final TokenID token = asToken("0.0.23456");
    private static final byte[] ECDSA_PUBLIC_KEY =
            Hex.decode("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
}
