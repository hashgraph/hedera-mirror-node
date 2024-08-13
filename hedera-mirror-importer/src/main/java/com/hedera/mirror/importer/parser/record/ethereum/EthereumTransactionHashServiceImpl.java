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

package com.hedera.mirror.importer.parser.record.ethereum;

import static com.hedera.mirror.importer.parser.record.ethereum.Eip1559EthereumTransactionParser.EIP1559_TYPE_BYTE;
import static com.hedera.mirror.importer.parser.record.ethereum.Eip2930EthereumTransactionParser.EIP2930_TYPE_BYTE;
import static com.hedera.mirror.importer.parser.record.ethereum.LegacyEthereumTransactionParser.LEGACY_TYPE_BYTE;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.importer.repository.FileDataRepository;
import jakarta.inject.Named;
import jakarta.validation.constraints.NotNull;
import java.math.BigInteger;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.util.encoders.Hex;

@CustomLog
@Named
@RequiredArgsConstructor
class EthereumTransactionHashServiceImpl implements EthereumTransactionHashService {

    private static final byte[] EIP1559_TYPE_BYTES = Integers.toBytes(EIP1559_TYPE_BYTE);
    private static final byte[] EIP2930_TYPE_BYTES = Integers.toBytes(EIP2930_TYPE_BYTE);

    private final EthereumTransactionParser ethereumTransactionParser;
    private final FileDataRepository fileDataRepository;

    @Override
    public byte[] getHash(EntityId callDataId, long consensusTimestamp, byte @NotNull [] data) {
        if (EntityId.isEmpty(callDataId)) {
            return getHash(data);
        }

        try {
            var ethereumTransaction = ethereumTransactionParser.decode(data);
            ethereumTransaction.setCallDataId(callDataId);
            ethereumTransaction.setConsensusTimestamp(consensusTimestamp);
            ethereumTransaction.setData(data);
            return getHash(ethereumTransaction);
        } catch (Exception e) {
            log.warn("Failed to decode / encode ethereum transaction at {}", consensusTimestamp, e);
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }
    }

    @Override
    @SuppressWarnings("java:S6880")
    public byte[] getHash(@NotNull EthereumTransaction ethereumTransaction) {
        var callDataId = ethereumTransaction.getCallDataId();
        // Note if callData is not empty, callDataId should be ignored, and directly calculate the hash over the saved
        // original raw bytes
        if (ArrayUtils.isNotEmpty(ethereumTransaction.getCallData()) || EntityId.isEmpty(callDataId)) {
            return getHash(ethereumTransaction.getData());
        }

        long consensusTimestamp = ethereumTransaction.getConsensusTimestamp();
        if (ArrayUtils.isNotEmpty(ethereumTransaction.getAccessList())) {
            log.warn("Re-encoding ethereum transaction at {} with access list is unsupported", consensusTimestamp);
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        var callDataOptional = fileDataRepository.getFileAtTimestamp(callDataId.getId(), consensusTimestamp);
        if (callDataOptional.isEmpty()) {
            log.warn("Call data not found from {} for ethereum transaction at {}", callDataId, consensusTimestamp);
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        try {
            byte[] rawBytes;
            byte[] callData = Hex.decode(stripHexPrefix(callDataOptional.get().getFileData()));
            var type = ethereumTransaction.getType();
            // Mirrornode decodes BigInteger 0 (encoded as an empty byte array in RLP) to a 1-byte array [0],
            // re-encoding it in the RLP way to get the correct raw bytes for hashing
            byte[] value = Integers.toBytesUnsigned(new BigInteger(ethereumTransaction.getValue()));
            if (type == EIP1559_TYPE_BYTE) {
                rawBytes = RLPEncoder.sequence(
                        EIP1559_TYPE_BYTES,
                        List.of(
                                ethereumTransaction.getChainId(),
                                Integers.toBytes(ethereumTransaction.getNonce()),
                                ethereumTransaction.getMaxPriorityFeePerGas(),
                                ethereumTransaction.getMaxFeePerGas(),
                                Integers.toBytes(ethereumTransaction.getGasLimit()),
                                ethereumTransaction.getToAddress(),
                                value,
                                callData,
                                List.of(/*accessList*/ ),
                                Integers.toBytes(ethereumTransaction.getRecoveryId()),
                                ethereumTransaction.getSignatureR(),
                                ethereumTransaction.getSignatureS()));
            } else if (type == EIP2930_TYPE_BYTE) {
                rawBytes = RLPEncoder.sequence(
                        EIP2930_TYPE_BYTES,
                        List.of(
                                ethereumTransaction.getChainId(),
                                Integers.toBytes(ethereumTransaction.getNonce()),
                                ethereumTransaction.getGasPrice(),
                                Integers.toBytes(ethereumTransaction.getGasLimit()),
                                ethereumTransaction.getToAddress(),
                                value,
                                callData,
                                List.of(/*accessList*/ ),
                                Integers.toBytes(ethereumTransaction.getRecoveryId()),
                                ethereumTransaction.getSignatureR(),
                                ethereumTransaction.getSignatureS()));
            } else if (type == LEGACY_TYPE_BYTE) {
                rawBytes = RLPEncoder.list(
                        Integers.toBytes(ethereumTransaction.getNonce()),
                        ethereumTransaction.getGasPrice(),
                        Integers.toBytes(ethereumTransaction.getGasLimit()),
                        ethereumTransaction.getToAddress(),
                        value,
                        callData,
                        ethereumTransaction.getSignatureV(),
                        ethereumTransaction.getSignatureR(),
                        ethereumTransaction.getSignatureS());
            } else {
                log.error("Unsupported transaction type {} of ethereum transaction at {}", type, consensusTimestamp);
                return ArrayUtils.EMPTY_BYTE_ARRAY;
            }

            return getHash(rawBytes);
        } catch (Exception e) {
            log.warn("Failed to decode / encode ethereum transaction at {}", consensusTimestamp, e);
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }
    }

    private static byte[] getHash(byte[] rawBytes) {
        return new Keccak.Digest256().digest(rawBytes);
    }

    private static byte[] stripHexPrefix(byte[] data) {
        // If the first two bytes are hex prefix '0x', strip them
        if (data.length >= 2 && data[0] == (byte) 0x30 && data[1] == (byte) 0x78) {
            return ArrayUtils.subarray(data, 2, data.length);
        }

        return data;
    }
}
