package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import java.util.List;
import lombok.RequiredArgsConstructor;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@RequiredArgsConstructor
abstract class AbstractAllowanceTransactionHandler implements TransactionHandler {

    private final EntityListener entityListener;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return null;
    }

    @Override
    public void updateTransaction(Transaction transaction, RecordItem recordItem) {
        long consensusTimestamp = transaction.getConsensusTimestamp();
        var payerAccountId = recordItem.getPayerAccountId();

        for (var cryptoApproval : getCryptoAllowances(recordItem)) {
            CryptoAllowance cryptoAllowance = new CryptoAllowance();
            EntityId ownerAccountId = getOwnerAccountId(cryptoApproval.getOwner(), payerAccountId);
            cryptoAllowance.setAmount(cryptoApproval.getAmount());
            cryptoAllowance.setOwner(ownerAccountId.getId());
            cryptoAllowance.setPayerAccountId(payerAccountId);
            cryptoAllowance.setSpender(EntityId.of(cryptoApproval.getSpender()).getId());
            cryptoAllowance.setTimestampLower(consensusTimestamp);
            entityListener.onCryptoAllowance(cryptoAllowance);
        }

        for (var nftApproval : getNftAllowances(recordItem)) {
            EntityId ownerAccountId = getOwnerAccountId(nftApproval.getOwner(), payerAccountId);
            EntityId spender = EntityId.of(nftApproval.getSpender());
            EntityId tokenId = EntityId.of(nftApproval.getTokenId());

            // duplicate
            if (nftApproval.hasApprovedForAll()) {
                NftAllowance nftAllowance = new NftAllowance();
                nftAllowance.setApprovedForAll(nftApproval.getApprovedForAll().getValue());
                nftAllowance.setOwner(ownerAccountId.getId());
                nftAllowance.setPayerAccountId(payerAccountId);
                nftAllowance.setSpender(spender.getId());
                nftAllowance.setTokenId(tokenId.getId());
                nftAllowance.setTimestampLower(consensusTimestamp);
                entityListener.onNftAllowance(nftAllowance);
            } else {
                EntityId delegatingSpender = EntityId.of(nftApproval.getDelegatingSpender());
                for (var serialNumber : nftApproval.getSerialNumbersList()) {
                    // nft instance allowance update doesn't set nft modifiedTimestamp
                    // services allows the same serial number of a nft token appears in multiple nft allowances to
                    // different accounts. The last spender will be granted such allowance.
                    Nft nft = new Nft(serialNumber, tokenId);
                    nft.setAccountId(ownerAccountId);
                    nft.setSpender(spender);
                    nft.setAllowanceGrantedTimestamp(consensusTimestamp);
                    nft.setDelegatingSpender(delegatingSpender);
                    entityListener.onNftInstanceAllowance(nft);
                }
            }
        }

        for (var tokenApproval : getTokenAllowances(recordItem)) {
            TokenAllowance tokenAllowance = new TokenAllowance();
            EntityId ownerAccountId = getOwnerAccountId(tokenApproval.getOwner(), payerAccountId);
            tokenAllowance.setAmount(tokenApproval.getAmount());
            tokenAllowance.setOwner(ownerAccountId.getId());
            tokenAllowance.setPayerAccountId(payerAccountId);
            tokenAllowance.setSpender(EntityId.of(tokenApproval.getSpender()).getId());
            tokenAllowance.setTokenId(EntityId.of(tokenApproval.getTokenId()).getId());
            tokenAllowance.setTimestampLower(consensusTimestamp);
            entityListener.onTokenAllowance(tokenAllowance);
        }
    }

    protected abstract List<com.hederahashgraph.api.proto.java.CryptoAllowance> getCryptoAllowances(RecordItem recordItem);

    protected abstract List<com.hederahashgraph.api.proto.java.NftAllowance> getNftAllowances(RecordItem recordItem);

    protected abstract List<com.hederahashgraph.api.proto.java.TokenAllowance> getTokenAllowances(RecordItem recordItem);

    /**
     * Gets the owner of the allowance. An empty owner in the *Allowance protobuf message implies the transaction payer
     * is the owner of the resource the spender is granted allowance of.
     *
     * @param owner The owner in the *Allowance protobuf message
     * @param payerAccountId The transaction payer
     * @return The effective owner account id
     */
    private EntityId getOwnerAccountId(AccountID owner, EntityId payerAccountId) {
        return owner == AccountID.getDefaultInstance() ? payerAccountId : EntityId.of(owner);
    }
}
