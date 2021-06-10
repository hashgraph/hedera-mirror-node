package com.hedera.mirror.importer.parser;

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

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.util.Utility;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractStreamFileParser<T extends StreamFile> implements StreamFileParser<T> {

    protected final ParserProperties parserProperties;

    public void parse(T streamFile) {
        if (parserProperties.isEnabled()) {
            parseStreamFile(streamFile);
        }

        postParse(streamFile);
    }

    protected abstract void parseStreamFile(T streamFile);

    private void postParse(T streamFile) {
        byte[] bytes = streamFile.getBytes();
        if (parserProperties.isEnabled() && parserProperties.isKeepFiles() && bytes != null) {
            Utility.archiveFile(streamFile.getName(), bytes, parserProperties.getParsedPath());
        }

        streamFile.setBytes(null);
        streamFile.setItems(null);
    }
}
