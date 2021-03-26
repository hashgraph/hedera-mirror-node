package com.hedera.mirror.importer.parser.record.entity;

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

import java.util.List;
import java.util.function.BiConsumer;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Primary;

import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.Schedule;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenTransfer;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionSignature;
import com.hedera.mirror.importer.exception.ImporterException;

@Log4j2
@Named
@Primary
@RequiredArgsConstructor
public class CompositeEntityListener implements EntityListener {

    private final List<EntityListener> entityListeners;

    private <T> void onEach(BiConsumer<EntityListener, T> consumer, T t) {
        entityListeners.stream()
                .filter(EntityListener::isEnabled)
                .peek(e -> log.trace("On: {}", t))
                .forEach(e -> consumer.accept(e, t));
    }

    @Override
    public void onContractResult(ContractResult contractResult) throws ImporterException {
        onEach(EntityListener::onContractResult, contractResult);
    }

    @Override
    public void onCryptoTransfer(CryptoTransfer cryptoTransfer) throws ImporterException {
        onEach(EntityListener::onCryptoTransfer, cryptoTransfer);
    }

    @Override
    public void onEntityId(EntityId entityId) throws ImporterException {
        onEach(EntityListener::onEntityId, entityId);
    }

    @Override
    public void onFileData(FileData fileData) throws ImporterException {
        onEach(EntityListener::onFileData, fileData);
    }

    @Override
    public void onLiveHash(LiveHash liveHash) throws ImporterException {
        onEach(EntityListener::onLiveHash, liveHash);
    }

    @Override
    public void onNonFeeTransfer(NonFeeTransfer nonFeeTransfer) throws ImporterException {
        onEach(EntityListener::onNonFeeTransfer, nonFeeTransfer);
    }

    @Override
    public void onSchedule(Schedule schedule) throws ImporterException {
        onEach(EntityListener::onSchedule, schedule);
    }

    @Override
    public void onToken(Token token) throws ImporterException {
        onEach(EntityListener::onToken, token);
    }

    @Override
    public void onTokenAccount(TokenAccount tokenAccount) throws ImporterException {
        onEach(EntityListener::onTokenAccount, tokenAccount);
    }

    @Override
    public void onTokenTransfer(TokenTransfer tokenTransfer) throws ImporterException {
        onEach(EntityListener::onTokenTransfer, tokenTransfer);
    }

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        onEach(EntityListener::onTopicMessage, topicMessage);
    }

    @Override
    public void onTransaction(Transaction transaction) throws ImporterException {
        onEach(EntityListener::onTransaction, transaction);
    }

    @Override
    public void onTransactionSignature(TransactionSignature transactionSignature) throws ImporterException {
        onEach(EntityListener::onTransactionSignature, transactionSignature);
    }
}
