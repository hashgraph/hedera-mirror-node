package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.TransactionBody;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class TransactionHandlerFactory {
    private final ConsensusCreateTopicTransactionHandler consensusCreateTopicTransactionHandler;
    private final ConsensusDeleteTopicTransactionHandler consensusDeleteTopicTransactionHandler;
    private final ConsensusSubmitMessageTransactionHandler consensusSubmitMessageTransactionHandler;
    private final ConsensusUpdateTopicTransactionHandler consensusUpdateTopicTransactionHandler;
    private final ContractCallTransactionHandler contractCallTransactionHandler;
    private final ContractCreateTransactionHandler contractCreateTransactionHandler;
    private final ContractDeleteTransactionHandler contractDeleteTransactionHandler;
    private final ContractUpdateTransactionHandler contractUpdateTransactionHandler;
    private final CryptoAddLiveHashTransactionHandler cryptoAddLiveHashTransactionHandler;
    private final CryptoCreateTransactionHandler cryptoCreateTransactionHandler;
    private final CryptoDeleteLiveHashTransactionHandler cryptoDeleteLiveHashTransactionHandler;
    private final CryptoDeleteTransactionHandler cryptoDeleteTransactionHandler;
    private final CryptoTransferTransactionHandler cryptoTransferTransactionHandler;
    private final CryptoUpdateTransactionHandler cryptoUpdateTransactionHandler;
    private final FileAppendTransactionHandler fileAppendTransactionHandler;
    private final FileCreateTransactionHandler fileCreateTransactionHandler;
    private final FileDeleteTransactionHandler fileDeleteTransactionHandler;
    private final FileUpdateTransactionHandler fileUpdateTransactionHandler;
    private final ScheduleCreateTransactionHandler scheduleCreateTransactionHandler;
    private final ScheduleSignTransactionHandler scheduleSignTransactionHandler;
    private final TokenAssociateTransactionsHandler tokenAssociateTransactionsHandler;
    private final TokenBurnTransactionsHandler tokenBurnTransactionsHandler;
    private final TokenCreateTransactionsHandler tokenCreateTransactionsHandler;
    private final TokenDeleteTransactionsHandler tokenDeleteTransactionsHandler;
    private final TokenDissociateTransactionsHandler tokenDissociateTransactionsHandler;
    private final TokenFreezeTransactionsHandler tokenFreezeTransactionsHandler;
    private final TokenGrantKycTransactionsHandler tokenGrantKycTransactionsHandler;
    private final TokenMintTransactionsHandler tokenMintTransactionsHandler;
    private final TokenRevokeKycTransactionsHandler tokenRevokeKycTransactionsHandler;
    private final TokenUnfreezeTransactionsHandler tokenUnfreezeTransactionsHandler;
    private final TokenUpdateTransactionsHandler tokenUpdateTransactionsHandler;
    private final TokenWipeTransactionsHandler tokenWipeTransactionsHandler;
    private final SystemDeleteTransactionHandler systemDeleteTransactionHandler;
    private final SystemUndeleteTransactionHandler systemUndeleteTransactionHandler;
    private final UnknownDataTransactionHandler unknownDataTransactionHandler;

    public TransactionHandler create(TransactionBody body) {
        if (body.hasConsensusCreateTopic()) {
            return consensusCreateTopicTransactionHandler;
        } else if (body.hasConsensusDeleteTopic()) {
            return consensusDeleteTopicTransactionHandler;
        } else if (body.hasConsensusSubmitMessage()) {
            return consensusSubmitMessageTransactionHandler;
        } else if (body.hasConsensusUpdateTopic()) {
            return consensusUpdateTopicTransactionHandler;
        } else if (body.hasContractCall()) {
            return contractCallTransactionHandler;
        } else if (body.hasContractCreateInstance()) {
            return contractCreateTransactionHandler;
        } else if (body.hasContractDeleteInstance()) {
            return contractDeleteTransactionHandler;
        } else if (body.hasContractUpdateInstance()) {
            return contractUpdateTransactionHandler;
        } else if (body.hasCryptoAddLiveHash()) {
            return cryptoAddLiveHashTransactionHandler;
        } else if (body.hasCryptoCreateAccount()) {
            return cryptoCreateTransactionHandler;
        } else if (body.hasCryptoDeleteLiveHash()) {
            return cryptoDeleteLiveHashTransactionHandler;
        } else if (body.hasCryptoDelete()) {
            return cryptoDeleteTransactionHandler;
        } else if (body.hasCryptoTransfer()) {
            return cryptoTransferTransactionHandler;
        } else if (body.hasCryptoUpdateAccount()) {
            return cryptoUpdateTransactionHandler;
        } else if (body.hasFileAppend()) {
            return fileAppendTransactionHandler;
        } else if (body.hasFileCreate()) {
            return fileCreateTransactionHandler;
        } else if (body.hasFileDelete()) {
            return fileDeleteTransactionHandler;
        } else if (body.hasFileUpdate()) {
            return fileUpdateTransactionHandler;
        } else if (body.hasScheduleCreate()) {
            return scheduleCreateTransactionHandler;
        } else if (body.hasScheduleSign()) {
            return scheduleSignTransactionHandler;
        } else if (body.hasTokenAssociate()) {
            return tokenAssociateTransactionsHandler;
        } else if (body.hasTokenBurn()) {
            return tokenBurnTransactionsHandler;
        } else if (body.hasTokenCreation()) {
            return tokenCreateTransactionsHandler;
        } else if (body.hasTokenDeletion()) {
            return tokenDeleteTransactionsHandler;
        } else if (body.hasTokenDissociate()) {
            return tokenDissociateTransactionsHandler;
        } else if (body.hasTokenFreeze()) {
            return tokenFreezeTransactionsHandler;
        } else if (body.hasTokenGrantKyc()) {
            return tokenGrantKycTransactionsHandler;
        } else if (body.hasTokenMint()) {
            return tokenMintTransactionsHandler;
        } else if (body.hasTokenRevokeKyc()) {
            return tokenRevokeKycTransactionsHandler;
        } else if (body.hasTokenUnfreeze()) {
            return tokenUnfreezeTransactionsHandler;
        } else if (body.hasTokenUpdate()) {
            return tokenUpdateTransactionsHandler;
        } else if (body.hasTokenWipe()) {
            return tokenWipeTransactionsHandler;
        } else if (body.hasSystemDelete()) {
            return systemDeleteTransactionHandler;
        } else if (body.hasSystemUndelete()) {
            return systemUndeleteTransactionHandler;
        } else {
            return unknownDataTransactionHandler;
        }
    }
}
