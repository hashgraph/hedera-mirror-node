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

import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.services.utils.MiscUtils.asPrimitiveKeyUnchecked;
import static com.hedera.services.utils.accessors.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.jproto.JKey;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.*;
import java.util.Collections;
import org.apache.commons.lang3.tuple.Pair;
import org.hyperledger.besu.datatypes.Address;

/**
 * Copied Logic type from hedera-services. Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. Remove alias logic and pending creations, use {@link MirrorEvmContractAliases} instead
 * thus removing the List<BalanceChange> changes argument from create
 * 3. Remove SyntheticTxnFactory
 * 4. Remove UsageLimits and GlobalDynamicProperties
 * 5. trackAliases consumes 2 Addresses
 * 6. The class is stateless and the arguments are passed into the functions
 */
public abstract class AbstractAutoCreationLogic {
    private final FeeCalculator feeCalculator;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    protected AbstractAutoCreationLogic(FeeCalculator feeCalculator, MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.feeCalculator = feeCalculator;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    /**
     * Provisionally auto-creates an account in the given accounts ledger for the triggering balance change.
     *
     * <p>Returns the amount deducted from the balance change as an auto-creation charge; or a
     * failure code.
     *
     * <p><b>IMPORTANT:</b> If this change was to be part of a zero-sum balance change list, then
     * after those changes are applied atomically, the returned fee must be given to the funding account!
     *
     * @param change a triggering change with unique alias
     * @return the fee charged for the auto-creation if ok, a failure reason otherwise
     */
    public Pair<ResponseCodeEnum, Long> create(
            final BalanceChange change,
            final Timestamp timestamp,
            final Store store,
            final EntityIdSource ids,
            final MirrorEvmContractAliases mirrorEvmContractAliases) {
        if (change.isForToken() && !mirrorNodeEvmProperties.isLazyCreationEnabled()) {
            return Pair.of(NOT_SUPPORTED, 0L);
        }
        final var alias = change.getNonEmptyAliasIfPresent();
        if (alias == null) {
            throw new IllegalStateException("Cannot auto-create an account from unaliased change " + change);
        }

        final var key = asPrimitiveKeyUnchecked(alias);
        final JKey jKey = asFcKeyUnchecked(key);
        var fee = autoCreationFeeFor(jKey, store, timestamp);

        final var newId = ids.newAccountId();
        final var account = new Account(
                Id.fromGrpcAccount(newId),
                0L,
                0L,
                false,
                0L,
                0L,
                null,
                0,
                Collections.emptySortedMap(),
                Collections.emptySortedMap(),
                Collections.emptySortedSet(),
                0,
                0,
                0,
                0L,
                false);
        store.updateAccount(account);
        replaceAliasAndSetBalanceOnChange(change, newId);
        trackAlias(jKey, account.getAccountAddress(), mirrorEvmContractAliases);
        return Pair.of(OK, fee);
    }

    protected abstract void trackAlias(
            final JKey jKey, final Address alias, final MirrorEvmContractAliases mirrorEvmContractAliases);

    private void replaceAliasAndSetBalanceOnChange(final BalanceChange change, final AccountID newAccountId) {
        if (change.isForHbar()) {
            change.setNewBalance(change.getAggregatedUnits());
        }
        change.replaceNonEmptyAliasWith(fromAccountId(newAccountId));
    }

    private long autoCreationFeeFor(final JKey payerKey, final Store store, Timestamp timestamp) {
        final var updateTxnBody =
                CryptoUpdateTransactionBody.newBuilder().setKey(Key.newBuilder().setECDSASecp256K1(ByteString.EMPTY));
        final var cryptoCreateTxn = TransactionBody.newBuilder().setCryptoUpdateAccount(updateTxnBody);
        final var signedTxn = SignedTransaction.newBuilder()
                .setBodyBytes(cryptoCreateTxn.build().toByteString())
                .setSigMap(SignatureMap.getDefaultInstance())
                .build();
        final var txn = Transaction.newBuilder()
                .setSignedTransactionBytes(signedTxn.toByteString())
                .build();
        final var accessor = uncheckedFrom(txn);
        final var fees = feeCalculator.computeFee(accessor, payerKey, store, timestamp);
        return fees.getServiceFee() + fees.getNetworkFee() + fees.getNodeFee();
    }
}
