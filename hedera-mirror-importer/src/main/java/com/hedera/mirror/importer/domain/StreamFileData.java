package com.hedera.mirror.importer.domain;

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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.NonNull;
import lombok.Value;
import org.apache.commons.io.FileUtils;

import com.hedera.mirror.importer.exception.FileOperationException;

@Value
public class StreamFileData {

    private final String filename;
    private final InputStream inputStream;

    public static StreamFileData from(@NonNull File file) {
        try {
            byte[] bytes = FileUtils.readFileToByteArray(file);
            return new StreamFileData(file.getAbsolutePath(), new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new FileOperationException("Unable to read file to byte array", e);
        }
    }

    // Used for testing String based files like CSVs
    public static StreamFileData from(@NonNull String filename, @NonNull String contents) {
        return new StreamFileData(filename, new BufferedInputStream(new ByteArrayInputStream(contents
                .getBytes(StandardCharsets.UTF_8))));
    }
}
