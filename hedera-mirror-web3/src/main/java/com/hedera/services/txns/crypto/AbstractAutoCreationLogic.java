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

import static com.hedera.node.app.service.evm.store.models.HederaEvmAccount.EVM_ADDRESS_SIZE;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.EMPTY_KEY;
import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hedera.services.utils.MiscUtils.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.*;

import java.util.*;

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
 * 7. Use {@link EntityAddressSequencer} in place of EntityIdSource
 */
public abstract class AbstractAutoCreationLogic {

    private final FeeCalculator feeCalculator;
    private final EvmProperties evmProperties;
    private final SyntheticTxnFactory syntheticTxnFactory;
    protected final Map<ByteString, Set<Id>> tokenAliasMap = new HashMap<>();

    protected AbstractAutoCreationLogic(
            final FeeCalculator feeCalculator,
            final EvmProperties evmProperties,
            final SyntheticTxnFactory syntheticTxnFactory) {
        this.feeCalculator = feeCalculator;
        this.evmProperties = evmProperties;
        this.syntheticTxnFactory = syntheticTxnFactory;
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
            final BalanceChange change,
            final Timestamp timestamp,
            final Store store,
            final EntityAddressSequencer ids,
            final List<BalanceChange> changes) {
        if (change.isForToken() && !evmProperties.isLazyCreationEnabled()) {
            return Pair.of(NOT_SUPPORTED, 0L);
        }
        final var alias = change.getNonEmptyAliasIfPresent();
        if (alias == null) {
            throw new IllegalStateException("Cannot auto-create an account from unaliased change " + change);
        }

        TransactionBody.Builder syntheticCreation;
        // checks tokenAliasMap if the change consists an alias that is already used in previous
        // iteration of the token transfer list. This map is used to count number of
        // maxAutoAssociations needed on auto created account
        analyzeTokenTransferCreations(changes);
        final var maxAutoAssociations = tokenAliasMap.getOrDefault(alias, Collections.emptySet()).size();
        final var isAliasEVMAddress = alias.size() == EVM_ADDRESS_SIZE;
        if (isAliasEVMAddress) {
            syntheticCreation = syntheticTxnFactory.createHollowAccount(alias, 0L, maxAutoAssociations);
        } else {
            final var key = asPrimitiveKeyUnchecked(alias);
            syntheticCreation = syntheticTxnFactory.createAccount(alias, key, 0L, maxAutoAssociations);
        }

        var fee = autoCreationFeeFor(syntheticCreation, timestamp);
        if (isAliasEVMAddress) {
            fee += getLazyCreationFinalizationFee(timestamp);
        }

        final var newId = ids.getNewAccountId();
        final var account = new Account(
                alias,
                0L,
                Id.fromGrpcAccount(newId),
                0L,
                () -> 0L,
                false,
                () -> 0L,
                0L,
                null,
                0,
                Collections::emptySortedMap,
                Collections::emptySortedMap,
                Collections::emptySortedSet,
                () -> 0,
                () -> 0,
                0,
                0L,
                false,
                null,
                0L);
        store.updateAccount(account);

        replaceAliasAndSetBalanceOnChange(change, newId);
        trackAlias(alias, account.getAccountAddress());
        return Pair.of(OK, fee);
    }

    protected abstract void trackAlias(final ByteString alias, final Address address);

    private void replaceAliasAndSetBalanceOnChange(final BalanceChange change, final AccountID newAccountId) {
        if (change.isForHbar()) {
            change.setNewBalance(change.getAggregatedUnits());
        }
        change.replaceNonEmptyAliasWith(fromAccountId(newAccountId));
    }

    private long getLazyCreationFinalizationFee(Timestamp timestamp) {
        // an AccountID is already accounted for in the
        // fee estimator, so we just need to pass a stub ECDSA key
        // in the synthetic crypto update body
        final var updateTxnBody =
                CryptoUpdateTransactionBody.newBuilder().setKey(Key.newBuilder().setECDSASecp256K1(ByteString.EMPTY));
        return autoCreationFeeFor(TransactionBody.newBuilder().setCryptoUpdateAccount(updateTxnBody), timestamp);
    }

    private long autoCreationFeeFor(final TransactionBody.Builder cryptoCreateTxn, Timestamp timestamp) {
        final var accessor = synthAccessorFor(cryptoCreateTxn);
        final var fees = feeCalculator.computeFee(accessor, EMPTY_KEY, timestamp);
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

    /**
     * Clears any state related to provisionally created accounts.
     */
    public void reset() {
        tokenAliasMap.clear();
    }
}
