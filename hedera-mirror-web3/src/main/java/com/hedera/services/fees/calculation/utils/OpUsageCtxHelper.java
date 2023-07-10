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

package com.hedera.services.fees.calculation.utils;

import static com.hedera.services.fees.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.getCryptoAllowancesList;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.getFungibleTokenAllowancesList;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.getNftApprovedForAll;
import static com.hedera.services.utils.EntityIdUtils.asEvmAddress;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.services.fees.usage.token.meta.TokenMintMeta;
import com.hedera.services.hapi.fees.usage.crypto.ExtantCryptoContext;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Use abstraction for the state by introducing {@link Store} interface
 *  2. Remove FeeSchedule, UtilPrng, File logic
 *  3. Use HederaEvmContractAliases
 */
public class OpUsageCtxHelper {

    public ExtantCryptoContext ctxForCryptoUpdate(
            TransactionBody txn, final Store store, final HederaEvmContractAliases hederaEvmContractAliases) {
        final var op = txn.getCryptoUpdateAccount();
        final var id = op.getAccountIDToUpdate();
        final var accountOrAlias = id.getAlias().isEmpty()
                ? Address.wrap(Bytes.wrap(asEvmAddress(id)))
                : hederaEvmContractAliases.resolveForEvm(
                        Address.wrap(Bytes.wrap(id.getAlias().toByteArray())));
        final var account = store.getAccount(accountOrAlias, OnMissing.DONT_THROW);
        ExtantCryptoContext cryptoContext;
        if (!account.isEmptyAccount()) {
            cryptoContext = ExtantCryptoContext.newBuilder()
                    .setCurrentKey(asKeyUnchecked(account.getKey()))
                    .setCurrentMemo("")
                    .setCurrentExpiry(account.getExpiry())
                    .setCurrentlyHasProxy(account.getProxy() != null)
                    .setCurrentNumTokenRels(account.getNumAssociations())
                    .setCurrentMaxAutomaticAssociations(account.getMaxAutomaticAssociations())
                    .setCurrentCryptoAllowances(getCryptoAllowancesList(account))
                    .setCurrentTokenAllowances(getFungibleTokenAllowancesList(account))
                    .setCurrentApproveForAllNftAllowances(getNftApprovedForAll(account))
                    .build();
        } else {
            cryptoContext = ExtantCryptoContext.newBuilder()
                    .setCurrentExpiry(
                            txn.getTransactionID().getTransactionValidStart().getSeconds())
                    .setCurrentMemo("")
                    .setCurrentKey(Key.getDefaultInstance())
                    .setCurrentlyHasProxy(false)
                    .setCurrentNumTokenRels(0)
                    .setCurrentMaxAutomaticAssociations(0)
                    .setCurrentCryptoAllowances(Collections.emptyMap())
                    .setCurrentTokenAllowances(Collections.emptyMap())
                    .setCurrentApproveForAllNftAllowances(Collections.emptySet())
                    .build();
        }
        return cryptoContext;
    }

    public ExtantCryptoContext ctxForCryptoAllowance(
            TxnAccessor accessor, final Store store, final HederaEvmContractAliases hederaEvmContractAliases) {
        final var id = accessor.getPayer();
        final var accountOrAlias = id.getAlias().isEmpty()
                ? Address.wrap(Bytes.wrap(asEvmAddress(id)))
                : hederaEvmContractAliases.resolveForEvm(
                        Address.wrap(Bytes.wrap(id.getAlias().toByteArray())));
        final var account = store.getAccount(accountOrAlias, OnMissing.DONT_THROW);
        ExtantCryptoContext cryptoContext;
        if (!account.isEmptyAccount()) {
            cryptoContext = ExtantCryptoContext.newBuilder()
                    .setCurrentKey(asKeyUnchecked(account.getKey()))
                    .setCurrentMemo("")
                    .setCurrentExpiry(account.getExpiry())
                    .setCurrentlyHasProxy(account.getProxy() != null)
                    .setCurrentNumTokenRels(account.getNumAssociations())
                    .setCurrentMaxAutomaticAssociations(account.getMaxAutomaticAssociations())
                    .setCurrentCryptoAllowances(getCryptoAllowancesList(account))
                    .setCurrentTokenAllowances(getFungibleTokenAllowancesList(account))
                    .setCurrentApproveForAllNftAllowances(getNftApprovedForAll(account))
                    .build();
        } else {
            cryptoContext = ExtantCryptoContext.newBuilder()
                    .setCurrentExpiry(accessor.getTxn()
                            .getTransactionID()
                            .getTransactionValidStart()
                            .getSeconds())
                    .setCurrentMemo("")
                    .setCurrentKey(Key.getDefaultInstance())
                    .setCurrentlyHasProxy(false)
                    .setCurrentNumTokenRels(0)
                    .setCurrentMaxAutomaticAssociations(0)
                    .setCurrentCryptoAllowances(Collections.emptyMap())
                    .setCurrentTokenAllowances(Collections.emptyMap())
                    .setCurrentApproveForAllNftAllowances(Collections.emptySet())
                    .build();
        }
        return cryptoContext;
    }

    public TokenMintMeta metaForTokenMint(TxnAccessor accessor, final Store store) {
        final var subType = accessor.getSubType();

        long lifeTime = 0L;
        if (subType == TOKEN_NON_FUNGIBLE_UNIQUE) {
            final var token = accessor.getTxn().getTokenMint().getToken();
            final var now = accessor.getTxnId().getTransactionValidStart().getSeconds();
            final var tokenModel = store.getToken(Address.wrap(Bytes.wrap(asEvmAddress(token))), OnMissing.THROW);
            lifeTime = tokenModel.isEmptyToken() ? 0 : Math.max(0L, tokenModel.getExpiry() - now);
        }
        return TOKEN_OPS_USAGE_UTILS.tokenMintUsageFrom(accessor.getTxn(), subType, lifeTime);
    }
}
