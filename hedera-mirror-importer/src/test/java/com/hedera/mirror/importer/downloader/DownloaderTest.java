/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.downloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.ConsensusNodeStub;
import com.hedera.mirror.importer.domain.StreamFileSignature;
import com.hedera.mirror.importer.domain.StreamFilename;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DownloaderTest {

    @Mock
    private Downloader<RecordFile, ?> downloader;

    @Test
    void streamFileSignatureMultiMapExpectNoDuplicate() {
        // given
        when(downloader.getStreamFileSignatureMultiMap()).thenCallRealMethod();
        var signatureFilename = StreamFilename.of("2022-01-01T00_00_00Z.rcd_sig");
        var multimap = downloader.getStreamFileSignatureMultiMap();
        var streamFileSignature = streamFileSignature(1L, signatureFilename);
        multimap.put(signatureFilename, streamFileSignature);
        // Same object
        multimap.put(signatureFilename, streamFileSignature);
        // Different object with same value
        multimap.put(signatureFilename, streamFileSignature(1L, signatureFilename));
        // Different value
        multimap.put(signatureFilename, streamFileSignature(2L, signatureFilename));

        // when
        var actual = multimap.get(signatureFilename);

        // then
        assertThat(actual)
                .map(StreamFileSignature::getNode)
                .map(ConsensusNode::getNodeId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    private StreamFileSignature streamFileSignature(long nodeId, StreamFilename streamFilename) {
        return StreamFileSignature.builder()
                .filename(streamFilename)
                .node(ConsensusNodeStub.builder().nodeId(nodeId).build())
                .build();
    }
}
