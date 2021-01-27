package com.hedera.mirror.importer.parser.record.entity.repository;

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

import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.annotation.Order;

import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.Schedule;
import com.hedera.mirror.importer.domain.ScheduleSignature;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenTransfer;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.repository.LiveHashRepository;
import com.hedera.mirror.importer.repository.NonFeeTransferRepository;
import com.hedera.mirror.importer.repository.ScheduleRepository;
import com.hedera.mirror.importer.repository.ScheduleSignatureRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import com.hedera.mirror.importer.repository.TopicMessageRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

@ConditionOnEntityRecordParser
@Log4j2
@Named
@Order(1)
@RequiredArgsConstructor
public class RepositoryEntityListener implements EntityListener {

    private final RepositoryProperties repositoryProperties;
    private final ContractResultRepository contractResultRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final EntityRepository entityRepository;
    private final FileDataRepository fileDataRepository;
    private final LiveHashRepository liveHashRepository;
    private final NonFeeTransferRepository nonFeeTransferRepository;
    private final TokenRepository tokenRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenTransferRepository tokenTransferRepository;
    private final TopicMessageRepository topicMessageRepository;
    private final TransactionRepository transactionRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleSignatureRepository scheduleSignatureRepository;

    @Override
    public boolean isEnabled() {
        return repositoryProperties.isEnabled();
    }

    @Override
    public void onContractResult(ContractResult contractResult) throws ImporterException {
        contractResultRepository.save(contractResult);
    }

    @Override
    public void onCryptoTransfer(CryptoTransfer cryptoTransfer) throws ImporterException {
        cryptoTransferRepository.save(cryptoTransfer);
    }

    @Override
    public void onEntityId(EntityId entityId) throws ImporterException {
        entityRepository.insertEntityId(entityId);
    }

    @Override
    public void onFileData(FileData fileData) throws ImporterException {
        fileDataRepository.save(fileData);
    }

    @Override
    public void onLiveHash(LiveHash liveHash) throws ImporterException {
        liveHashRepository.save(liveHash);
    }

    @Override
    public void onNonFeeTransfer(NonFeeTransfer nonFeeTransfer) throws ImporterException {
        nonFeeTransferRepository.save(nonFeeTransfer);
    }

    @Override
    public void onSchedule(Schedule schedule) throws ImporterException {
        scheduleRepository.save(schedule);
    }

    @Override
    public void onScheduleSignature(ScheduleSignature scheduleSignature) throws ImporterException {
        scheduleSignatureRepository.save(scheduleSignature);
    }

    @Override
    public void onToken(Token token) throws ImporterException {
        tokenRepository.save(token);
    }

    @Override
    public void onTokenAccount(TokenAccount tokenAccount) throws ImporterException {
        tokenAccountRepository.save(tokenAccount);
    }

    @Override
    public void onTokenTransfer(TokenTransfer tokenTransfer) throws ImporterException {
        tokenTransferRepository.save(tokenTransfer);
    }

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        topicMessageRepository.save(topicMessage);
    }

    @Override
    public void onTransaction(Transaction transaction) throws ImporterException {
        transactionRepository.save(transaction);
    }
}
