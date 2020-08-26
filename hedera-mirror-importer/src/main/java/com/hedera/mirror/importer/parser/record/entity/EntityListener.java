package com.hedera.mirror.importer.parser.record.entity;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.exception.ImporterException;

/**
 * Handlers for items parsed during processing of record stream.
 */
public interface EntityListener {

    default boolean isEnabled() {
        return true;
    }

    default void onContractResult(ContractResult contractResult) throws ImporterException {
    }

    default void onCryptoTransfer(CryptoTransfer cryptoTransfer) throws ImporterException {
    }

    default void onEntityId(EntityId entityId) throws ImporterException {
    }

    default void onFileData(FileData fileData) throws ImporterException {
    }

    default void onLiveHash(LiveHash liveHash) throws ImporterException {
    }

    default void onNonFeeTransfer(NonFeeTransfer nonFeeTransfer) throws ImporterException {
    }

    default void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
    }

    default void onTransaction(Transaction transaction) throws ImporterException {
    }
}
