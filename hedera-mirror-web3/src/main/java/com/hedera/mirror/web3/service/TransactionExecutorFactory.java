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

package com.hedera.mirror.web3.service;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.mirror.web3.evm.config.ModularizedOperation;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.node.app.workflows.standalone.TransactionExecutor;
import com.hedera.node.app.workflows.standalone.TransactionExecutors;
import com.hedera.node.app.workflows.standalone.TransactionExecutors.Properties;
import com.swirlds.state.State;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class TransactionExecutorFactory {

    private final Set<ModularizedOperation> customOperations;
    private final State mirrorNodeState;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final Map<SemanticVersion, TransactionExecutor> transactionExecutors = new ConcurrentHashMap<>();

    // Reuse TransactionExecutor across requests for the same EVM version
    public TransactionExecutor get() {
        var version = mirrorNodeEvmProperties.getSemanticEvmVersion();
        return transactionExecutors.computeIfAbsent(version, this::create);
    }

    private TransactionExecutor create(SemanticVersion evmVersion) {
        var appProperties = new HashMap<>(mirrorNodeEvmProperties.getTransactionProperties());
        appProperties.put("contracts.evm.version", "v" + evmVersion.major() + "." + evmVersion.minor());

        var executorConfig = Properties.newBuilder()
                .appProperties(appProperties)
                .customOps(customOperations)
                .state(mirrorNodeState)
                .build();

        return TransactionExecutors.TRANSACTION_EXECUTORS.newExecutor(executorConfig);
    }
}
