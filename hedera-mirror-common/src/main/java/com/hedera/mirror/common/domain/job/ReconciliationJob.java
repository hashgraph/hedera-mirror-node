/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.common.domain.job;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Entity
@NoArgsConstructor
public class ReconciliationJob {

    private long consensusTimestamp;

    private long count;

    private String error;

    private ReconciliationStatus status;

    private Instant timestampEnd;

    @Id
    private Instant timestampStart;

    public boolean hasErrors() {
        return status.ordinal() > ReconciliationStatus.SUCCESS.ordinal();
    }

    public void increment() {
        ++count;
    }
}
