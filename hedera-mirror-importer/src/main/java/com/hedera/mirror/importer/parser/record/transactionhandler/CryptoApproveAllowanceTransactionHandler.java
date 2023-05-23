/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static com.hedera.mirror.importer.util.Utility.RECOVERABLE_ERROR;

import com.hedera.mirror.common.domain.entity.AbstractCryptoAllowance;
import com.hedera.mirror.common.domain.entity.AbstractNftAllowance;
import com.hedera.mirror.common.domain.entity.AbstractTokenAllowance;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.contractlog.ApproveAllowanceContractLog;
import com.hedera.mirror.importer.parser.contractlog.ApproveAllowanceIndexedContractLog;
import com.hedera.mirror.importer.parser.contractlog.ApproveForAllAllowanceContractLog;
import com.hedera.mirror.importer.parser.contractlog.SyntheticContractLogService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hederahashgraph.api.proto.java.AccountID;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

@CustomLog
@Named
@RequiredArgsConstructor
class CryptoApproveAllowanceTransactionHandler implements TransactionHandler {

    private final EntityIdService entityIdService;

    private final EntityListener entityListener;

    private final SyntheticContractLogService syntheticContractLogService;

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

        var transactionBody = recordItem.getTransactionBody().getCryptoApproveAllowance();
        parseCryptoAllowances(transactionBody.getCryptoAllowancesList(), recordItem);
        parseNftAllowances(transactionBody.getNftAllowancesList(), recordItem);
        parseTokenAllowances(transactionBody.getTokenAllowancesList(), recordItem);
    }

    private void parseCryptoAllowances(
            List<com.hederahashgraph.api.proto.java.CryptoAllowance> cryptoAllowances, RecordItem recordItem) {
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        var cryptoAllowanceState = new HashMap<AbstractCryptoAllowance.Id, CryptoAllowance>();
        var payerAccountId = recordItem.getPayerAccountId();

        // iterate the crypto allowance list in reverse order and honor the last allowance for the same owner and
        // spender
        var iterator = cryptoAllowances.listIterator(cryptoAllowances.size());
        while (iterator.hasPrevious()) {
            var cryptoApproval = iterator.previous();
            EntityId ownerAccountId = getOwnerAccountId(cryptoApproval.getOwner(), payerAccountId);
            if (EntityId.isEmpty(ownerAccountId)) {
                log.error(RECOVERABLE_ERROR + "Empty ownerAccountId at consensusTimestamp {}", consensusTimestamp);
                continue;
            }

            CryptoAllowance cryptoAllowance = new CryptoAllowance();
            cryptoAllowance.setAmount(cryptoApproval.getAmount());
            cryptoAllowance.setOwner(ownerAccountId.getId());
            cryptoAllowance.setPayerAccountId(payerAccountId);
            cryptoAllowance.setSpender(EntityId.of(cryptoApproval.getSpender()).getId());
            cryptoAllowance.setTimestampLower(consensusTimestamp);

            if (cryptoAllowanceState.putIfAbsent(cryptoAllowance.getId(), cryptoAllowance) == null) {
                entityListener.onCryptoAllowance(cryptoAllowance);
            }
        }
    }

    private void parseNftAllowances(
            List<com.hederahashgraph.api.proto.java.NftAllowance> nftAllowances, RecordItem recordItem) {
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        var payerAccountId = recordItem.getPayerAccountId();
        var nftAllowanceState = new HashMap<AbstractNftAllowance.Id, NftAllowance>();
        var nftSerialAllowanceState = new HashMap<NftId, Nft>();
        // iterate the nft allowance list in reverse order and honor the last allowance for either
        // the same owner, spender, and token for approved for all allowances, or the last serial allowance for
        // the same owner, spender, token, and serial
        var iterator = nftAllowances.listIterator(nftAllowances.size());
        while (iterator.hasPrevious()) {
            var nftApproval = iterator.previous();
            EntityId ownerAccountId = getOwnerAccountId(nftApproval.getOwner(), payerAccountId);
            if (EntityId.isEmpty(ownerAccountId)) {
                // ownerAccountId will be EMPTY only when getOwnerAccountId fails to resolve the owner in the alias form
                continue;
            }

            EntityId spender = EntityId.of(nftApproval.getSpender());
            EntityId tokenId = EntityId.of(nftApproval.getTokenId());
            boolean hasApprovedForAll = nftApproval.hasApprovedForAll();
            parseNftApproveForAll(
                    recordItem, nftAllowanceState, nftApproval, ownerAccountId, spender, tokenId, hasApprovedForAll);

            EntityId delegatingSpender = EntityId.of(nftApproval.getDelegatingSpender());
            for (var serialNumber : nftApproval.getSerialNumbersList()) {
                // services allows the same serial number of a nft token appears in multiple nft allowances to
                // different spenders. The last spender will be granted such allowance.
                Nft nft = new Nft(serialNumber, tokenId);
                nft.setAccountId(ownerAccountId);
                nft.setDelegatingSpender(delegatingSpender);
                nft.setModifiedTimestamp(consensusTimestamp);
                nft.setSpender(spender);

                if (nftSerialAllowanceState.putIfAbsent(nft.getId(), nft) == null) {
                    entityListener.onNft(nft);
                    if (!hasApprovedForAll) {
                        syntheticContractLogService.create(new ApproveAllowanceIndexedContractLog(
                                recordItem, tokenId, ownerAccountId, spender, serialNumber));
                    }
                }
            }
        }
    }

    private void parseNftApproveForAll(
            RecordItem recordItem,
            HashMap<AbstractNftAllowance.Id, NftAllowance> nftAllowanceState,
            com.hederahashgraph.api.proto.java.NftAllowance nftApproval,
            EntityId ownerAccountId,
            EntityId spender,
            EntityId tokenId,
            boolean hasApprovedForAll) {
        if (hasApprovedForAll) {
            var consensusTimestamp = recordItem.getConsensusTimestamp();
            var payerAccountId = recordItem.getPayerAccountId();

            var approvedForAll = nftApproval.getApprovedForAll().getValue();
            NftAllowance nftAllowance = new NftAllowance();
            nftAllowance.setApprovedForAll(approvedForAll);
            nftAllowance.setOwner(ownerAccountId.getId());
            nftAllowance.setPayerAccountId(payerAccountId);
            nftAllowance.setSpender(spender.getId());
            nftAllowance.setTokenId(tokenId.getId());
            nftAllowance.setTimestampLower(consensusTimestamp);

            if (nftAllowanceState.putIfAbsent(nftAllowance.getId(), nftAllowance) == null) {
                entityListener.onNftAllowance(nftAllowance);
                syntheticContractLogService.create(new ApproveForAllAllowanceContractLog(
                        recordItem, tokenId, ownerAccountId, spender, approvedForAll));
            }
        }
    }

    private void parseTokenAllowances(
            List<com.hederahashgraph.api.proto.java.TokenAllowance> tokenAllowances, RecordItem recordItem) {
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        var payerAccountId = recordItem.getPayerAccountId();
        var tokenAllowanceState = new HashMap<AbstractTokenAllowance.Id, TokenAllowance>();
        // iterate the token allowance list in reverse order and honor the last allowance for the same owner, spender,
        // and token
        var iterator = tokenAllowances.listIterator(tokenAllowances.size());
        while (iterator.hasPrevious()) {
            var tokenApproval = iterator.previous();
            EntityId ownerAccountId = getOwnerAccountId(tokenApproval.getOwner(), payerAccountId);
            if (EntityId.isEmpty(ownerAccountId)) {
                // ownerAccountId will be EMPTY only when getOwnerAccountId fails to resolve the owner in the alias form
                continue;
            }
            EntityId spenderId = EntityId.of(tokenApproval.getSpender());
            EntityId tokenId = EntityId.of(tokenApproval.getTokenId());

            TokenAllowance tokenAllowance = new TokenAllowance();
            tokenAllowance.setAmount(tokenApproval.getAmount());
            tokenAllowance.setOwner(ownerAccountId.getId());
            tokenAllowance.setPayerAccountId(payerAccountId);
            tokenAllowance.setSpender(spenderId.getId());
            tokenAllowance.setTokenId(tokenId.getId());
            tokenAllowance.setTimestampLower(consensusTimestamp);

            if (tokenAllowanceState.putIfAbsent(tokenAllowance.getId(), tokenAllowance) == null) {
                entityListener.onTokenAllowance(tokenAllowance);
                syntheticContractLogService.create(new ApproveAllowanceContractLog(
                        recordItem, tokenId, ownerAccountId, spenderId, tokenApproval.getAmount()));
            }
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
        var entityId = entityIdService.lookup(owner);
        if (entityId.isPresent()) {
            var ownerAccountId = entityId.get();
            return !EntityId.isEmpty(ownerAccountId) ? ownerAccountId : payerAccountId;
        }

        return EntityId.EMPTY;
    }
}
