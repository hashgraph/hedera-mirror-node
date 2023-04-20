/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.jproto;

/**
 * Maps to proto Key.
 */
public abstract class JKey {

    private static final byte[] MISSING_RSA_3072_KEY = new byte[0];
    private static final byte[] MISSING_ED25519_KEY = new byte[0];
    private static final byte[] MISSING_ECDSA_384_KEY = new byte[0];
    private static final byte[] MISSING_ECDSA_SECP256K1_KEY = new byte[0];

    private boolean forScheduledTxn = false;

    public abstract boolean isEmpty();

    /**
     * Expected to return {@code false} if the key is empty
     *
     * @return whether the key is valid
     */
    public abstract boolean isValid();

    public void setForScheduledTxn(boolean flag) {
        forScheduledTxn = flag;
    }

    public boolean isForScheduledTxn() {
        return forScheduledTxn;
    }

    public boolean hasEd25519Key() {
        return false;
    }

    public boolean hasECDSA384Key() {
        return false;
    }

    public boolean hasECDSAsecp256k1Key() {
        return false;
    }

    public boolean hasRSA3072Key() {
        return false;
    }

    public boolean hasKeyList() {
        return false;
    }

    public boolean hasThresholdKey() {
        return false;
    }

    public boolean hasContractID() {
        return false;
    }

    public boolean hasContractAlias() {
        return false;
    }

    public boolean hasDelegatableContractAlias() {
        return false;
    }

    public boolean hasDelegatableContractId() {
        return false;
    }

    public JContractIDKey getContractIDKey() {
        return null;
    }

    public JContractAliasKey getContractAliasKey() {
        return null;
    }

    public JDelegatableContractAliasKey getDelegatableContractAliasKey() {
        return null;
    }

    public JDelegatableContractIDKey getDelegatableContractIdKey() {
        return null;
    }

    public JThresholdKey getThresholdKey() {
        return null;
    }

    public JKeyList getKeyList() {
        return null;
    }

    public byte[] getEd25519() {
        return MISSING_ED25519_KEY;
    }

    public byte[] getECDSA384() {
        return MISSING_ECDSA_384_KEY;
    }

    public byte[] getECDSASecp256k1Key() {
        return MISSING_ECDSA_SECP256K1_KEY;
    }

    public byte[] getRSA3072() {
        return MISSING_RSA_3072_KEY;
    }
}
