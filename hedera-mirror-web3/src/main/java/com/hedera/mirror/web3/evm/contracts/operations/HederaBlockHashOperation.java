/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.contracts.operations;

import jakarta.inject.Named;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.BlockHashOperation;

/**
 * Custom version of the Besu's BlockHashOperation class. The difference is
 * that in the mirror node we have the block hash values of all the blocks
 * so the restriction for the latest 256 blocks is removed. The latest
 * block value can be returned as well.
 */
@Named
public class HederaBlockHashOperation extends BlockHashOperation {
    /**
     * Instantiates a new Block hash operation.
     *
     * @param gasCalculator the gas calculator
     */
    public HederaBlockHashOperation(GasCalculator gasCalculator) {
        super(gasCalculator);
    }
}
