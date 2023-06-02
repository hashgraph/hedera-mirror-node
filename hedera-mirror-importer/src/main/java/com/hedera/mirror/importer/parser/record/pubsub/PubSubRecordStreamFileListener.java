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

package com.hedera.mirror.importer.parser.record.pubsub;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.SidecarFileRepository;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
@ConditionalOnPubSubRecordParser
public class PubSubRecordStreamFileListener implements RecordStreamFileListener {

    private final RecordFileRepository recordFileRepository;
    private final SidecarFileRepository sidecarFileRepository;

    @Override
    public void onStart() throws ImporterException {
        // Do nothing
    }

    @Override
    public void onEnd(RecordFile recordFile) throws ImporterException {
        if (recordFile != null) {
            recordFileRepository.save(recordFile);
            sidecarFileRepository.saveAll(recordFile.getSidecars());
        }
    }

    @Override
    public void onError() {
        // Do nothing
    }
}
