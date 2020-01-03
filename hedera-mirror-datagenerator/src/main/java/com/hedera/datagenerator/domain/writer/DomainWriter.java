package com.hedera.datagenerator.domain.writer;
/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import java.io.Closeable;

import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;

public interface DomainWriter extends Closeable {
    void addTransaction(Transaction transaction);

    void addEntity(Entities entity);

    void addCryptoTransfer(CryptoTransfer cryptoTransfer);

    void addFileData(FileData fileData);

    void addTopicMessage(TopicMessage topicMessage);

    void addAccountBalances(long consensusNs, long balance, long accountNum);
}
