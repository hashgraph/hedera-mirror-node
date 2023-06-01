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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.jproto.JKey;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public abstract class AbstractAutoCreationLogic {
    private final StackedStateFrames<Object> stackedStateFrames;
    protected final EntityIdSource ids;
    protected final Map<ByteString, Set<Id>> tokenAliasMap = new HashMap<>();
    protected FeeCalculator feeCalculator;

    protected AbstractAutoCreationLogic(final EntityIdSource ids, final StackedStateFrames<Object> stackedStateFrames) {
        this.ids = ids;
        this.stackedStateFrames = stackedStateFrames;
    }

    public void setFeeCalculator(final FeeCalculator feeCalculator) {
        this.feeCalculator = feeCalculator;
    }

    /**
     * Clears any state related to provisionally created accounts and their pending child records.
     */
    public void reset() {
        tokenAliasMap.clear();
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
     * @param change  a triggering change with unique alias
     * @param changes list of all changes need to construct tokenAliasMap
     * @return the fee charged for the auto-creation if ok, a failure reason otherwise
     */
    public Pair<ResponseCodeEnum, Long> create(
            final BalanceChange change, final List<BalanceChange> changes, final Timestamp timestamp) {
        final var alias = change.getNonEmptyAliasIfPresent();
        if (alias == null) {
            throw new IllegalStateException("Cannot auto-create an account from unaliased change " + change);
        }
        analyzeTokenTransferCreations(changes);
        final var key = asPrimitiveKeyUnchecked(alias);
        final JKey jKey = asFcKeyUnchecked(key);
        var fee = autoCreationFeeFor(jKey, stackedStateFrames, timestamp);

        final var newId = ids.newAccountId();
        final var topFrame = stackedStateFrames.top();
        final var accountAccessor = topFrame.getAccessor(Account.class);
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
                0);
        accountAccessor.set(Id.fromGrpcAccount(newId).asEvmAddress(), account);
        replaceAliasAndSetBalanceOnChange(change, newId);
        trackAlias(alias, newId);
        return Pair.of(OK, fee);
    }

    protected abstract void trackAlias(final ByteString alias, final AccountID newId);

    private void replaceAliasAndSetBalanceOnChange(final BalanceChange change, final AccountID newAccountId) {
        if (change.isForHbar()) {
            change.setNewBalance(change.getAggregatedUnits());
        }
        change.replaceNonEmptyAliasWith(fromAccountId(newAccountId));
    }

    private long autoCreationFeeFor(
            final JKey payerKey, final StackedStateFrames<Object> stackedStateFrames, Timestamp timestamp) {
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
        final var fees = feeCalculator.computeFee(accessor, payerKey, stackedStateFrames, timestamp);
        return fees.getServiceFee() + fees.getNetworkFee() + fees.getNodeFee();
    }

    private void analyzeTokenTransferCreations(final List<BalanceChange> changes) {
        for (final var change : changes) {
            if (change.isForHbar()) {
                continue;
            }
            var alias = change.getNonEmptyAliasIfPresent();

            if (alias != null) {
                if (tokenAliasMap.containsKey(alias)) {
                    final var oldSet = tokenAliasMap.get(alias);
                    oldSet.add(change.getToken());
                    tokenAliasMap.put(alias, oldSet);
                } else {
                    tokenAliasMap.put(alias, new HashSet<>(Arrays.asList(change.getToken())));
                }
            }
        }
    }
}
