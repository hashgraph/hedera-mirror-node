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

package com.hedera.mirror.importer.parser.record.entity;

import com.hedera.mirror.common.domain.addressbook.NetworkStake;
import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityTransaction;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.transaction.AssessedCustomFee;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.LiveHash;
import com.hedera.mirror.common.domain.transaction.NetworkFreeze;
import com.hedera.mirror.common.domain.transaction.Prng;
import com.hedera.mirror.common.domain.transaction.StakingRewardTransfer;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionSignature;
import com.hedera.mirror.importer.exception.ImporterException;
import java.util.Collection;

/**
 * Handlers for items parsed during processing of record stream.
 */
public interface EntityListener {

    default boolean isEnabled() {
        return true;
    }

    default void onAssessedCustomFee(AssessedCustomFee assessedCustomFee) throws ImporterException {}

    default void onContract(Contract contract) {}

    default void onContractAction(ContractAction contractAction) {}

    default void onContractLog(ContractLog contractLog) {}

    default void onContractStateChange(ContractStateChange contractStateChange) {}

    default void onContractResult(ContractResult contractResult) throws ImporterException {}

    default void onCryptoAllowance(CryptoAllowance cryptoAllowance) {}

    default void onCustomFee(CustomFee customFee) throws ImporterException {}

    default void onCryptoTransfer(CryptoTransfer cryptoTransfer) throws ImporterException {}

    default void onEntity(Entity entity) throws ImporterException {}

    default void onEntityTransactions(Collection<EntityTransaction> entityTransactions) throws ImporterException {}

    default void onEthereumTransaction(EthereumTransaction ethereumTransaction) {}

    default void onFileData(FileData fileData) throws ImporterException {}

    default void onLiveHash(LiveHash liveHash) throws ImporterException {}

    default void onNetworkFreeze(NetworkFreeze networkFreeze) {}

    default void onNetworkStake(NetworkStake networkStake) throws ImporterException {}

    default void onNft(Nft nft) throws ImporterException {}

    default void onNftAllowance(NftAllowance nftAllowance) {}

    default void onNodeStake(NodeStake nodeStake) throws ImporterException {}

    default void onPrng(Prng prng) {}

    default void onSchedule(Schedule schedule) throws ImporterException {}

    default void onStakingRewardTransfer(StakingRewardTransfer stakingRewardTransfer) {}

    default void onToken(Token token) throws ImporterException {}

    default void onTokenAccount(TokenAccount tokenAccount) throws ImporterException {}

    default void onTokenAllowance(TokenAllowance tokenAllowance) {}

    default void onTokenTransfer(TokenTransfer tokenTransfer) throws ImporterException {}

    default void onTopicMessage(TopicMessage topicMessage) throws ImporterException {}

    default void onTransaction(Transaction transaction) throws ImporterException {}

    default void onTransactionSignature(TransactionSignature transactionSignature) throws ImporterException {}
}
