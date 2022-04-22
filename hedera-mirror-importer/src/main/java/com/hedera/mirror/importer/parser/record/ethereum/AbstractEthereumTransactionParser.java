package com.hedera.mirror.importer.parser.record.ethereum;

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

import java.math.BigInteger;
import org.apache.commons.codec.binary.Hex;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;

import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

public abstract class AbstractEthereumTransactionParser implements EthereumTransactionParser {
    @Override
    public byte[] retrievePublicKey(EthereumTransaction ethereumTransaction) {
        return recoverPublicKeyECDSASignature(ethereumTransaction.getRecoveryId(), ethereumTransaction.getSignatureR(),
                ethereumTransaction.getSignatureS(), ethereumTransaction.getRLPEncodedMessage());
    }

    private byte[] recoverPublicKeyECDSASignature(int recId, byte[] r, byte[] s, byte[] message) {
        try {
            var publicKey = Sign.recoverFromSignature(
                    (byte) recId,
                    new ECDSASignature(
                            new BigInteger(1, r),
                            new BigInteger(1, s)),
                    Hash.sha3(message));

            // compress
            String publicKeyYPrefix = publicKey.testBit(0) ? "03" : "02";
            String publicKeyHex = publicKey.toString(16);
            String publicKeyX = publicKeyHex.substring(0, 64);
            var compressedKey = publicKeyYPrefix + publicKeyX;

            return Hex.decodeHex(compressedKey);
        } catch (Exception ex) {
            throw new InvalidDatasetException("Unable to extract publicKey");
        }
    }
}
