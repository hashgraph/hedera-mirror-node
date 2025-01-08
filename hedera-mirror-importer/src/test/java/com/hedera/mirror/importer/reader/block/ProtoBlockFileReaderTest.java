/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.reader.block;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.domain.StreamFileData;
import java.io.File;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.shaded.org.apache.commons.io.comparator.NameFileComparator;

class ProtoBlockFileReaderTest {

    private ProtoBlockFileReader reader;
    private List<File> blockStreamFiles;

    @BeforeEach
    @SneakyThrows
    void setup() {
        reader = new ProtoBlockFileReader();
        blockStreamFiles =
                FileUtils.listFiles(new ClassPathResource("data/blockstreams").getFile(), null, false).stream()
                        .sorted(NameFileComparator.NAME_COMPARATOR)
                        .toList();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void read(int index) {
        var streamFileData = StreamFileData.from(blockStreamFiles.get(index));
        var blockFile = reader.read(streamFileData);
        assertThat(blockFile).isNotNull();
    }
}
