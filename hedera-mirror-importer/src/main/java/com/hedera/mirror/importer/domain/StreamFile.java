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

import lombok.NonNull;
import reactor.core.publisher.Flux;

import com.hedera.mirror.importer.parser.domain.StreamItem;

public interface StreamFile<T extends StreamItem> {

    byte[] getBytes();

    Long getConsensusStart();

    Long getConsensusEnd();

    Long getCount();

    String getFileHash();

    // Get the chained hash of the stream file
    default String getHash() {
        return null;
    }

    default Long getIndex() {
        return null;
    }

    Flux<T> getItems();

    Long getLoadEnd();

    Long getLoadStart();

    default String getMetadataHash() {
        return null;
    }

    String getName();

    EntityId getNodeAccountId();

    // Get the chained hash of the previous stream file
    default String getPreviousHash() {
        return null;
    }

    void setConsensusStart(Long timestamp);

    default void setHash(String hash) {
    }

    void setBytes(byte[] bytes);

    void setItems(Flux<T> items);

    void setName(String name);

    default void setIndex(Long index) {
    }

    void setNodeAccountId(@NonNull EntityId nodeAccountId);

    default void setPreviousHash(String previousHash) {
    }

    StreamType getType();
}
