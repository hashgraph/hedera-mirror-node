package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.parser.PartialDataAction.SKIP;
import static com.hedera.mirror.importer.util.Utility.RECOVERABLE_ERROR;

import com.hederahashgraph.api.proto.java.AccountID;
import java.util.HashMap;
import java.util.List;
import javax.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

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
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@CustomLog
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

        var transactionBody = recordItem.getTransactionBody().getCryptoApproveAllowance();
        parseCryptoAllowances(transactionBody.getCryptoAllowancesList(), recordItem);
        parseNftAllowances(transactionBody.getNftAllowancesList(), recordItem);
        parseTokenAllowances(transactionBody.getTokenAllowancesList(), recordItem);
    }

    @SuppressWarnings("java:S2259")
    private void parseCryptoAllowances(List<com.hederahashgraph.api.proto.java.CryptoAllowance> cryptoAllowances,
                                       RecordItem recordItem) {
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        var cryptoAllowanceState = new HashMap<AbstractCryptoAllowance.Id, CryptoAllowance>();
        var payerAccountId = recordItem.getPayerAccountId();

        // iterate the crypto allowance list in reverse order and honor the last allowance for the same owner and spender
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

    @SuppressWarnings("java:S2259")
    private void parseNftAllowances(List<com.hederahashgraph.api.proto.java.NftAllowance> nftAllowances,
                                    RecordItem recordItem) {
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
                // and the partialDataAction is SKIP
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

                if (nftAllowanceState.putIfAbsent(nftAllowance.getId(), nftAllowance) == null) {
                    entityListener.onNftAllowance(nftAllowance);
                }
            }

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
                }
            }
        }
    }

    @SuppressWarnings("java:S2259")
    private void parseTokenAllowances(List<com.hederahashgraph.api.proto.java.TokenAllowance> tokenAllowances,
                                      RecordItem recordItem) {
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
                // and the partialDataAction is SKIP
                continue;
            }

            TokenAllowance tokenAllowance = new TokenAllowance();
            tokenAllowance.setAmount(tokenApproval.getAmount());
            tokenAllowance.setOwner(ownerAccountId.getId());
            tokenAllowance.setPayerAccountId(payerAccountId);
            tokenAllowance.setSpender(EntityId.of(tokenApproval.getSpender()).getId());
            tokenAllowance.setTokenId(EntityId.of(tokenApproval.getTokenId()).getId());
            tokenAllowance.setTimestampLower(consensusTimestamp);

            if (tokenAllowanceState.putIfAbsent(tokenAllowance.getId(), tokenAllowance) == null) {
                entityListener.onTokenAllowance(tokenAllowance);
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
        if (entityId == null && recordParserProperties.getPartialDataAction() == SKIP) {
            return EntityId.EMPTY;
        }

        return !EntityId.isEmpty(entityId) || recordParserProperties.getPartialDataAction() != SKIP ?
                entityId : payerAccountId;
    }
}
