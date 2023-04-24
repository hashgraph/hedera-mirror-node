/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.utils.ethereum;

import com.google.common.base.MoreObjects;
import java.util.Arrays;
import org.apache.commons.codec.binary.Hex;

public record EthTxSigs(byte[] publicKey, byte[] address) {

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final EthTxSigs ethTxSigs = (EthTxSigs) o;

        if (!Arrays.equals(publicKey, ethTxSigs.publicKey)) {
            return false;
        }
        return Arrays.equals(address, ethTxSigs.address);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(publicKey);
        result = 31 * result + Arrays.hashCode(address);
        return result;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("publicKey", Hex.encodeHexString(publicKey))
                .add("address", Hex.encodeHexString(address))
                .toString();
    }
}
