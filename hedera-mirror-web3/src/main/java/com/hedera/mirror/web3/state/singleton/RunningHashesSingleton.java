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

package com.hedera.mirror.web3.state.singleton;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.RUNNING_HASHES_STATE_KEY;

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;

@Named
public class RunningHashesSingleton implements SingletonState<RunningHashes> {

    @Override
    public String getKey() {
        return RUNNING_HASHES_STATE_KEY;
    }

    @Override
    public RunningHashes get() {
        var recordFile = ContractCallContext.get().getRecordFile();
        return RunningHashes.newBuilder()
                .runningHash(Bytes.EMPTY)
                .nMinus1RunningHash(Bytes.EMPTY)
                .nMinus2RunningHash(Bytes.EMPTY)
                .nMinus3RunningHash(Bytes.fromHex(recordFile.getHash())) // Used by prevrandao
                .build();
    }
}
