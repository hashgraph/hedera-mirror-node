package com.hedera.mirror.importer.reader.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.domain.StreamFileData;

class RecordFileReaderImplV5Test extends AbstractRecordFileReaderTest {

    @Override
    protected RecordFileReader getRecordFileReader() {
        return new RecordFileReaderImplV5();
    }

    @Override
    protected boolean filterFile(int version) {
        return version == 5;
    }

    @SneakyThrows
    @TestFactory
    Stream<DynamicTest> verifyRecordItemLinksInEthTransactionValidFile() {
        String template = "read file %s containing eth transactions";
        var resourceResolver = new PathMatchingResourcePatternResolver();

        return DynamicTest.stream(
                Arrays.stream(resourceResolver.getResources("classpath:data/recordstreams/eth-0.26.0/record0.0.3/*" +
                        ".rcd")),
                (recordFile) -> String.format(template, recordFile.getFilename()),
                (recordFile) -> {
                    // given
                    StreamFileData streamFileData = StreamFileData.from(recordFile.getFile());

                    // when
                    RecordFile actual = recordFileReader.read(streamFileData);

                    // then
                    RecordItem previousItem = null;
                    RecordItem lastParentItem = null;
                    for (var item : actual.getItems().collectList().block()) {
                        // assert previous link points to previous item
                        assertThat(item.getPrevious()).isEqualTo(previousItem);

                        // confirm if child that parent is populated
                        if (item.isChild()) {
                            assertThat(item.getParent()).isEqualTo(lastParentItem);
                        } else {
                            lastParentItem = item;
                        }

                        previousItem = item;
                    }
                });
    }
}
