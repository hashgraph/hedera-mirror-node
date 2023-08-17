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

package com.hedera.mirror.importer.parser.batch;

import java.util.Collection;

/**
 * Performs bulk insertion of domain objects to the database. For some domain types it might be insert-only while others
 * may use upsert logic.
 */
public interface BatchPersister {

    String LATENCY_METRIC = "hedera.mirror.importer.batch.latency";

    void persist(Collection<? extends Object> items);
}
