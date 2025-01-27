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

package com.hedera.mirror.web3.evm.contracts.operations;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.config.ModularizedOperation;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import jakarta.inject.Named;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.BlockHashOperation;

/**
 * Custom version of the Besu's BlockHashOperation class. The difference is that in the mirror node we have the block
 * hash values of all the blocks so the restriction for the latest 256 blocks is removed. The latest block value can be
 * returned as well.
 */
@Named
class MirrorBlockHashOperation extends BlockHashOperation implements ModularizedOperation {

    private final RecordFileRepository recordFileRepository;

    /**
     * Instantiates a new Block hash operation.
     *
     * @param gasCalculator the gas calculator
     */
    MirrorBlockHashOperation(GasCalculator gasCalculator, RecordFileRepository recordFileRepository) {
        super(gasCalculator);
        this.recordFileRepository = recordFileRepository;
    }

    @Override
    public OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
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
        } else if (currentBlockNumber == soughtBlock) {
            final var latestBlock = ContractCallContext.get().getRecordFile();
            final var blockHash = getBlockHash(latestBlock);
            frame.pushStackItem(blockHash);
        } else {
            final Hash blockHash = getBlockHash(soughtBlock);
            frame.pushStackItem(blockHash);
        }

        return successResponse;
    }

    private Hash getBlockHash(long blockNumber) {
        final var recordFile = recordFileRepository.findByIndex(blockNumber);
        return recordFile.map(this::getBlockHash).orElse(Hash.ZERO);
    }

    private Hash getBlockHash(RecordFile recordFile) {
        return Hash.fromHexString(StringUtils.substring(recordFile.getHash(), 0, 64));
    }
}
