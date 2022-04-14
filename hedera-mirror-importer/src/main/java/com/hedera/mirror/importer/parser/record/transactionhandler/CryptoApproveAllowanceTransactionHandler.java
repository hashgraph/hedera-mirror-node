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

import static com.hedera.mirror.importer.parser.PartialDataAction.DEFAULT;
import static com.hedera.mirror.importer.parser.PartialDataAction.ERROR;

import com.hederahashgraph.api.proto.java.AccountID;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@Named
@RequiredArgsConstructor
class CryptoApproveAllowanceTransactionHandler implements TransactionHandler {

    private final EntityIdService entityIdService;

    private final EntityListener entityListener;

    private final RecordParserProperties recordParserProperties;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return null;
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOAPPROVEALLOWANCE;
    }

    @Override
    public void updateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        long consensusTimestamp = transaction.getConsensusTimestamp();
        var payerAccountId = recordItem.getPayerAccountId();

        var transactionBody = recordItem.getTransactionBody().getCryptoApproveAllowance();

        for (var cryptoApproval : transactionBody.getCryptoAllowancesList()) {
            EntityId ownerAccountId = getOwnerAccountId(cryptoApproval.getOwner(), payerAccountId);
            if (ownerAccountId == EntityId.EMPTY) {
                continue;
            }

            CryptoAllowance cryptoAllowance = new CryptoAllowance();
            cryptoAllowance.setAmount(cryptoApproval.getAmount());
            cryptoAllowance.setOwner(ownerAccountId.getId());
            cryptoAllowance.setPayerAccountId(payerAccountId);
            cryptoAllowance.setSpender(EntityId.of(cryptoApproval.getSpender()).getId());
            cryptoAllowance.setTimestampLower(consensusTimestamp);
            entityListener.onCryptoAllowance(cryptoAllowance);
        }

        for (var nftApproval : transactionBody.getNftAllowancesList()) {
            EntityId ownerAccountId = getOwnerAccountId(nftApproval.getOwner(), payerAccountId);
            if (ownerAccountId == EntityId.EMPTY) {
                continue;
            }

            EntityId spender = EntityId.of(nftApproval.getSpender());
            EntityId tokenId = EntityId.of(nftApproval.getTokenId());

            if (nftApproval.hasApprovedForAll()) {
                var approvedForAll = nftApproval.getApprovedForAll().getValue();
                NftAllowance nftAllowance = new NftAllowance();
                nftAllowance.setApprovedForAll(approvedForAll);
                nftAllowance.setOwner(ownerAccountId.getId());
                nftAllowance.setPayerAccountId(payerAccountId);
                nftAllowance.setSpender(spender.getId());
                nftAllowance.setTokenId(tokenId.getId());
                nftAllowance.setTimestampLower(consensusTimestamp);
                entityListener.onNftAllowance(nftAllowance);
            }

            EntityId delegatingSpender = EntityId.of(nftApproval.getDelegatingSpender());
            for (var serialNumber : nftApproval.getSerialNumbersList()) {
                // nft instance allowance update doesn't set nft modifiedTimestamp
                // services allows the same serial number of a nft token appears in multiple nft allowances to
                // different spenders. The last spender will be granted such allowance.
                Nft nft = new Nft(serialNumber, tokenId);
                nft.setAccountId(ownerAccountId);
                nft.setDelegatingSpender(delegatingSpender);
                nft.setModifiedTimestamp(consensusTimestamp);
                nft.setSpender(spender);
                entityListener.onNft(nft);
            }
        }

        for (var tokenApproval : transactionBody.getTokenAllowancesList()) {
            EntityId ownerAccountId = getOwnerAccountId(tokenApproval.getOwner(), payerAccountId);
            if (ownerAccountId == EntityId.EMPTY) {
                continue;
            }

            TokenAllowance tokenAllowance = new TokenAllowance();
            tokenAllowance.setAmount(tokenApproval.getAmount());
            tokenAllowance.setOwner(ownerAccountId.getId());
            tokenAllowance.setPayerAccountId(payerAccountId);
            tokenAllowance.setSpender(EntityId.of(tokenApproval.getSpender()).getId());
            tokenAllowance.setTokenId(EntityId.of(tokenApproval.getTokenId()).getId());
            tokenAllowance.setTimestampLower(consensusTimestamp);
            entityListener.onTokenAllowance(tokenAllowance);
        }
    }

    /**
     * Gets the owner of the allowance. An empty owner in the *Allowance protobuf message implies the transaction payer
     * is the owner of the resource the spender is granted allowance of.
     *
     * @param owner          The owner in the *Allowance protobuf message
     * @param payerAccountId The transaction payer
     * @return The effective owner account id
     */
    private EntityId getOwnerAccountId(AccountID owner, EntityId payerAccountId) {
        if (owner == AccountID.getDefaultInstance()) {
            return payerAccountId;
        }

        var accountId = entityIdService.lookup(owner);
        if (accountId == EntityId.EMPTY) {
            // Owner has alias and entityIdService lookup failed
            var partialDataAction = recordParserProperties.getPartialDataAction();
            if (partialDataAction == DEFAULT || partialDataAction == ERROR) {
                // There is no appropriate default for allowance owner, so throw an exception
                throw new InvalidEntityException("Invalid owner for allowance");
            }
        }
        return accountId;
    }
}
