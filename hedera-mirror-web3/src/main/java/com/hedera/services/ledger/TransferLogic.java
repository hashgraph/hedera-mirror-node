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

package com.hedera.services.ledger;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.hyperledger.besu.datatypes.Address;

public class TransferLogic {
    private final HederaTokenStore hederaTokenStore;
    private final AutoCreationLogic autoCreationLogic;
    private final MirrorEvmContractAliases mirrorEvmContractAliases;

    public TransferLogic(
            final HederaTokenStore hederaTokenStore,
            final @Nullable AutoCreationLogic autoCreationLogic,
            final MirrorEvmContractAliases mirrorEvmContractAliases) {
        this.hederaTokenStore = hederaTokenStore;
        this.autoCreationLogic = autoCreationLogic;
        this.mirrorEvmContractAliases = mirrorEvmContractAliases;
    }

    public void doZeroSum(final List<BalanceChange> changes, Store store, EntityIdSource ids, Address topLevelPayer) {
        var validity = OK;
        var autoCreationFee = 0L;
        var updatedPayerBalance = Long.MIN_VALUE;
        for (final var change : changes) {
            // If the change consists of any repeated aliases, replace the alias with the account
            // number
            replaceAliasWithIdIfExisting(change);

            // create a new account for alias when the no account is already created using the alias
            if (change.hasAlias()) {
                if (autoCreationLogic == null) {
                    throw new IllegalStateException(
                            "Cannot auto-create account from " + change + " with null autoCreationLogic");
                }
                final var result = autoCreationLogic.create(
                        change,
                        Timestamp.newBuilder()
                                .setSeconds(Instant.now().getEpochSecond())
                                .build(),
                        store,
                        ids,
                        mirrorEvmContractAliases);
                validity = result.getKey();
                autoCreationFee += result.getValue();
                if (validity == OK && (change.isForToken())) {
                    validity = hederaTokenStore.tryTokenChange(change);
                }
            } else if (change.isForHbar()) {
                if (change.affectsAccount(topLevelPayer)) {
                    updatedPayerBalance = change.getNewBalance();
                }
            } else {
                if (validity == OK) {
                    validity = hederaTokenStore.tryTokenChange(change);
                }
            }
            if (validity != OK) {
                break;
            }
        }

        if (validity == OK && autoCreationFee > 0) {
            updatedPayerBalance = (updatedPayerBalance == Long.MIN_VALUE)
                    ? (long) store.getAccount(topLevelPayer, OnMissing.THROW).getBalance()
                    : updatedPayerBalance;
            if (autoCreationFee > updatedPayerBalance) {
                validity = INSUFFICIENT_PAYER_BALANCE;
            }
        }

        if (validity == OK) {
            adjustBalancesAndAllowances(changes, store);
        } else {
            throw new InvalidTransactionException(validity);
        }
    }

    private void adjustBalancesAndAllowances(final List<BalanceChange> changes, Store store) {
        for (final var change : changes) {
            final var accountId = change.accountId();
            if (change.isForHbar()) {
                final var newBalance = change.getNewBalance();
                Address accountAddress = asTypedEvmAddress(accountId);
                Account account = store.getAccount(accountAddress, OnMissing.THROW);
                account = account.setBalance(newBalance);
                store.updateAccount(account);
                if (change.isApprovedAllowance()) {
                    adjustCryptoAllowance(change, accountId, store);
                }
            } else if (change.isApprovedAllowance() && change.isForFungibleToken()) {
                adjustFungibleTokenAllowance(change, accountId, store);
            } else if (change.isForNft()) {
                // wipe the allowance on this uniqueToken
                UniqueToken uniqueToken = store.getUniqueToken(change.nftId(), OnMissing.THROW);
                uniqueToken = uniqueToken.setSpender(Id.DEFAULT);
                store.updateUniqueToken(uniqueToken);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void adjustCryptoAllowance(final BalanceChange change, final AccountID ownerID, final Store store) {
        final var payerNum = EntityNum.fromAccountId(change.getPayerID());
        Account account = store.getAccount(asTypedEvmAddress(ownerID), OnMissing.THROW);
        final var hbarAllowances = new TreeMap<>((Map<EntityNum, Long>) account.getCryptoAllowances());
        final var currentAllowance = hbarAllowances.get(payerNum);
        final var newAllowance = currentAllowance + change.getAllowanceUnits();
        if (newAllowance != 0) {
            hbarAllowances.put(payerNum, newAllowance);
        } else {
            hbarAllowances.remove(payerNum);
        }
        account = account.setCryptoAllowance(hbarAllowances);
        store.updateAccount(account);
    }

    @SuppressWarnings("unchecked")
    private void adjustFungibleTokenAllowance(final BalanceChange change, final AccountID ownerID, final Store store) {
        final var allowanceId = FcTokenAllowanceId.from(
                EntityNum.fromLong(change.getToken().num()), EntityNum.fromAccountId(change.getPayerID()));
        Account account = store.getAccount(asTypedEvmAddress(ownerID), OnMissing.THROW);
        final var fungibleAllowances =
                new TreeMap<>((Map<FcTokenAllowanceId, Long>) account.getFungibleTokenAllowances());
        final var currentAllowance = fungibleAllowances.get(allowanceId);
        final var newAllowance = currentAllowance + change.getAllowanceUnits();
        if (newAllowance == 0) {
            fungibleAllowances.remove(allowanceId);
        } else {
            fungibleAllowances.put(allowanceId, newAllowance);
        }
        account = account.setFungibleTokenAllowances(fungibleAllowances);
        store.updateAccount(account);
    }

    /**
     * Checks if the alias is a known alias i.e, if the alias is already used in any cryptoTransfer
     * transaction that has led to account creation
     *
     * @param change change that contains alias
     */
    private void replaceAliasWithIdIfExisting(final BalanceChange change) {
        final var alias = change.getNonEmptyAliasIfPresent();

        if (alias != null) {
            final var aliasNum = mirrorEvmContractAliases.lookupIdBy(alias);
            if (aliasNum != EntityNum.MISSING_NUM) {
                change.replaceNonEmptyAliasWith(aliasNum);
            }
        }
    }
}
