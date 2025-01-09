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

package com.hedera.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BlockFileTest {

    @Test
    void addItem() {
        var blockItem = BlockItem.builder().build();
        var blockFile = BlockFile.builder().addItem(blockItem).build();
        assertThat(blockFile.getItems()).containsExactly(blockItem);
    }

    @Test
    void count() {
        var blockFile = BlockFile.builder().build();
        assertThat(blockFile.getCount()).isZero();

        blockFile = BlockFile.builder().count(10L).build();
        assertThat(blockFile.getCount()).isEqualTo(10L);

        var blockItem = BlockItem.builder().build();
        blockFile = BlockFile.builder().items(List.of(blockItem)).build();
        assertThat(blockFile.getCount()).isEqualTo(1L);

        blockFile = BlockFile.builder().addItem(blockItem).build();
        assertThat(blockFile.getCount()).isEqualTo(1L);

        blockFile = BlockFile.builder().addItem(blockItem).count(5L).build();
        assertThat(blockFile.getCount()).isEqualTo(5L);
    }

    @Test
    void onNewRound() {
        var blockFile = BlockFile.builder().onNewRound(1L).build();
        assertThat(blockFile).returns(1L, BlockFile::getRoundStart).returns(1L, BlockFile::getRoundEnd);

        blockFile = BlockFile.builder().onNewRound(1L).onNewRound(2L).build();
        assertThat(blockFile).returns(1L, BlockFile::getRoundStart).returns(2L, BlockFile::getRoundEnd);
    }

    @Test
    void onNewTransaction() {
        var blockFile = BlockFile.builder().onNewTransaction(1).build();
        assertThat(blockFile).returns(1L, BlockFile::getConsensusStart).returns(1L, BlockFile::getConsensusEnd);

        blockFile =
                BlockFile.builder().onNewTransaction(1L).onNewTransaction(2L).build();
        assertThat(blockFile).returns(1L, BlockFile::getConsensusStart).returns(2L, BlockFile::getConsensusEnd);
    }
}
