/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.records.schemas;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.Schema;
import edu.umd.cs.findbugs.annotations.NonNull;

public class V0540BlockRecordSchema extends Schema {
    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(54).patch(0).build();

    private static final String SHARED_BLOCK_RECORD_INFO = "SHARED_BLOCK_RECORD_INFO";
    private static final String SHARED_RUNNING_HASHES = "SHARED_RUNNING_HASHES";

    public V0540BlockRecordSchema() {
        super(VERSION);
    }

    /**
     * {@inheritDoc}
     * */
    @Override
    public void migrate(@NonNull final MigrationContext ctx) {}
}
