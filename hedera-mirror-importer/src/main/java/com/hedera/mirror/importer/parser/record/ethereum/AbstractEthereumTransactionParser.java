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

import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.CONTEXT;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.secp256k1_ecdsa_recover;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.secp256k1_ecdsa_recoverable_signature_parse_compact;

import com.sun.jna.ptr.LongByReference;
import java.nio.ByteBuffer;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;

import com.hedera.mirror.common.domain.transaction.EthereumTransaction;

public abstract class AbstractEthereumTransactionParser implements EthereumTransactionParser {

    static final int SECP256K1_FLAGS_TYPE_COMPRESSION = 1 << 1;
    static final int SECP256K1_FLAGS_BIT_COMPRESSION = 1 << 8;
    static final int SECP256K1_EC_COMPRESSED = (SECP256K1_FLAGS_TYPE_COMPRESSION | SECP256K1_FLAGS_BIT_COMPRESSION);

    @Override
    public byte[] retrievePublicKey(EthereumTransaction ethereumTransaction) {
        return extractSig(ethereumTransaction.getRecoveryId(), ethereumTransaction.getSignatureR(),
                ethereumTransaction.getSignatureS(), ethereumTransaction.getSignableMessage());
    }

    private byte[] extractSig(int recId, byte[] r, byte[] s, byte[] message) {
        byte[] dataHash = new Keccak.Digest256().digest(message);

        byte[] signature = new byte[64];
        System.arraycopy(r, 0, signature, 0, r.length);
        System.arraycopy(s, 0, signature, 32, s.length);

        LibSecp256k1.secp256k1_ecdsa_recoverable_signature parsedSignature =
                new LibSecp256k1.secp256k1_ecdsa_recoverable_signature();

        if (secp256k1_ecdsa_recoverable_signature_parse_compact(CONTEXT, parsedSignature, signature, recId) == 0) {
            throw new IllegalArgumentException("Could not parse signature");
        }
        LibSecp256k1.secp256k1_pubkey newPubKey = new LibSecp256k1.secp256k1_pubkey();
        if (secp256k1_ecdsa_recover(CONTEXT, newPubKey, parsedSignature, dataHash) == 0) {
            throw new IllegalArgumentException("Could not recover signature");
        }

        // compress key
        ByteBuffer recoveredFullKey = ByteBuffer.allocate(33);
        LongByReference fullKeySize = new LongByReference(recoveredFullKey.limit());
        LibSecp256k1.secp256k1_ec_pubkey_serialize(
                CONTEXT, recoveredFullKey, fullKeySize, newPubKey, SECP256K1_EC_COMPRESSED);
        return recoveredFullKey.array();
    }
}
