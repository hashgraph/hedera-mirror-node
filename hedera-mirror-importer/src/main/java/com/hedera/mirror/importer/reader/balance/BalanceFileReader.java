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

import java.io.File;
import java.util.stream.Stream;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

public interface BalanceFileReader {
    /**
     * Reads an account balance file, parses the header to get the consensus timestamp, and returns a stream of
     * <code>AccountBalance</code> objects, one such object per valid account balance line.
     *
     * @param file The account balances file object
     * @return A stream of <code>AccountBalance</code> objects
     * @throws InvalidDatasetException if a fatal parsing error occurs
     */
    Stream<AccountBalance> read(File file);
}
