package com.hedera.mirror.common.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.entity.EntityId;

public interface StreamFile<T extends StreamItem> {

    byte[] getBytes();

    void setBytes(byte[] bytes);

    Long getConsensusStart();

    void setConsensusStart(Long timestamp);

    Long getConsensusEnd();

    Long getCount();

    String getFileHash();

    // Get the chained hash of the stream file
    default String getHash() {
        return null;
    }

    default void setHash(String hash) {
    }

    default Long getIndex() {
        return null;
    }

    default void setIndex(Long index) {
    }

    Flux<T> getItems();

    void setItems(Flux<T> items);

    Long getLoadEnd();

    Long getLoadStart();

    default String getMetadataHash() {
        return null;
    }

    String getName();

    void setName(String name);

    EntityId getNodeAccountId();

    void setNodeAccountId(@NonNull EntityId nodeAccountId);

    // Get the chained hash of the previous stream file
    default String getPreviousHash() {
        return null;
    }

    default void setPreviousHash(String previousHash) {
    }

    StreamType getType();
}
