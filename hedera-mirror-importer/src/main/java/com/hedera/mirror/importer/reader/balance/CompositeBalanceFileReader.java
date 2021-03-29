package com.hedera.mirror.importer.reader.balance;

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

import java.util.function.Consumer;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.StreamFileData;

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
    public AccountBalanceFile read(StreamFileData streamFileData, Consumer<AccountBalance> itemConsumer) {
        BalanceFileReader balanceFileReader = getReader(streamFileData);
        return balanceFileReader.read(streamFileData, itemConsumer);
    }

    private BalanceFileReader getReader(StreamFileData streamFileData) {
        if (protoBalanceFileReader.supports(streamFileData)) {
            return protoBalanceFileReader;
        }

        return balanceFileReaderImplV2.supports(streamFileData) ? balanceFileReaderImplV2 : balanceFileReaderImplV1;
    }
}
