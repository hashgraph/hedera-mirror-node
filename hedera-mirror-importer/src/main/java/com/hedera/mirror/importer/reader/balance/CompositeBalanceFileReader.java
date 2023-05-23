/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.reader.balance;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Primary;

@Log4j2
@Named
@Primary
@RequiredArgsConstructor
public class CompositeBalanceFileReader implements BalanceFileReader {

    private final BalanceFileReaderImplV1 balanceFileReaderImplV1;
    private final BalanceFileReaderImplV2 balanceFileReaderImplV2;
    private final ProtoBalanceFileReader protoBalanceFileReader;

    @Override
    public boolean supports(StreamFileData streamFileData) {
        return true;
    }

    @Override
    public AccountBalanceFile read(StreamFileData streamFileData) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        boolean success = false;

        try {
            BalanceFileReader balanceFileReader = getReader(streamFileData);
            AccountBalanceFile accountBalanceFile = balanceFileReader.read(streamFileData);
            success = true;
            return accountBalanceFile;
        } finally {
            log.info(
                    "Read account balance file {} {}successfully in {}",
                    streamFileData.getFilename(),
                    success ? "" : "un",
                    stopwatch);
        }
    }

    private BalanceFileReader getReader(StreamFileData streamFileData) {
        if (protoBalanceFileReader.supports(streamFileData)) {
            return protoBalanceFileReader;
        }

        return balanceFileReaderImplV2.supports(streamFileData) ? balanceFileReaderImplV2 : balanceFileReaderImplV1;
    }
}
