/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.BlockHashOperation;
import org.hyperledger.besu.evm.operation.Operation;

/**
 * Custom version of the Besu's BlockHashOperation class. The difference is
 * that in the mirror node we have the block hash values of all the blocks
 * so the restriction for the latest 256 blocks is removed. The latest
 * block value can be returned as well.
 */
public class HederaBlockHashOperation extends BlockHashOperation {
    /**
     * Instantiates a new Block hash operation.
     *
     * @param gasCalculator the gas calculator
     */
    public HederaBlockHashOperation(GasCalculator gasCalculator) {
        super(gasCalculator);
    }

    @Override
    public Operation.OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
        final Bytes blockArg = frame.popStackItem().trimLeadingZeros();

        // Short-circuit if value is unreasonably large
        if (blockArg.size() > 8) {
            frame.pushStackItem(UInt256.ZERO);
            return successResponse;
        }

        final long soughtBlock = blockArg.toLong();
        final BlockValues blockValues = frame.getBlockValues();
        final long currentBlockNumber = blockValues.getNumber();

        if (currentBlockNumber <= 0 || soughtBlock > currentBlockNumber) {
            frame.pushStackItem(Bytes32.ZERO);
        } else {
            final Function<Long, Hash> blockHashLookup = frame.getBlockHashLookup();
            final Hash blockHash = blockHashLookup.apply(soughtBlock);
            frame.pushStackItem(blockHash);
        }

        return successResponse;
    }
}
