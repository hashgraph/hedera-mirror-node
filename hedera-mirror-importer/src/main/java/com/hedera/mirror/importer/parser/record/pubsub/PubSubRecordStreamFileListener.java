package com.hedera.mirror.importer.parser.record.pubsub;

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

import com.hedera.mirror.importer.domain.ApplicationStatusCode;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;

@Named
@RequiredArgsConstructor
@ConditionalOnPubSubRecordParser
public class PubSubRecordStreamFileListener implements RecordStreamFileListener {

    private final RecordFileRepository recordFileRepository;
    private final ApplicationStatusRepository applicationStatusRepository;

    @Override
    public void onStart() throws ImporterException {
    }

    @Override
    public void onEnd(RecordFile recordFile) throws ImporterException {
        recordFileRepository.save(recordFile);
        applicationStatusRepository.updateStatusValue(
                ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH, recordFile.getHash());
    }

    @Override
    public void onError() {
    }
}
