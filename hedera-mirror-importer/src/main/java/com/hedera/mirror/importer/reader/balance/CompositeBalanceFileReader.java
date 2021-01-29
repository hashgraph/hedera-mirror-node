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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Stream;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.input.BoundedInputStream;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

@Named
@RequiredArgsConstructor
public class CompositeBalanceFileReader implements BalanceFileReader {

    static final int BUFFER_SIZE = 16;
    private final BalanceFileReaderImplV1 version1Reader;
    private final BalanceFileReaderImplV2 version2Reader;

    @Override
    public Stream<AccountBalance> read(File file) {
        return getReader(file).read(file);
    }

    private BalanceFileReader getReader(File file) {
        if (file == null) {
            throw new InvalidDatasetException("Null file provided to balance file reader");
        }
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(new BoundedInputStream(new FileInputStream(file),
                             BUFFER_SIZE)), BUFFER_SIZE)) {
            String line = reader.readLine();
            if (line == null) {
                throw new InvalidDatasetException("Account balance file is empty");
            } else if (version2Reader.isFirstLineFromFileVersion(line)) {
                return version2Reader;
            } else {
                return version1Reader;
            }
        } catch (IOException ex) {
            throw new InvalidDatasetException("Error reading account balance file", ex);
        }
    }
}
