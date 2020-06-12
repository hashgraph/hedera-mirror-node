/*
 * (c) 2016-2018 Swirlds, Inc.
 *
 * This software is the confidential and proprietary information of
 * Swirlds, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Swirlds.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */
package com.hedera.mirror.importer.fileencoding.event;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

/**
 * The type of cryptographic algorithm used to create a signature.
 *
 * @see Signature
 */
public enum SignatureType {
    /**
     * An Ed25519 signature which uses a SHA-512 hash and a 32 byte public key
     */
    ED25519,
    /**
     * An RSA signature as specified by the FIPS 186-4 standards
     */
    RSA,
    /**
     * An Elliptical Curve based signature as specified by the FIPS 186-4 standards
     */
    ECDSA;

    private static final SignatureType[] ORDINAL_LOOKUP = values();

    /**
     * Translates an ordinal position into an enumeration value.
     *
     * @param ordinal      the ordinal value to be translated
     * @param defaultValue the default enumeration value to return if the {@code ordinal} cannot be found
     * @return the enumeration value related to the given ordinal or the default value if the ordinal is not found
     */
    public static SignatureType from(int ordinal, SignatureType defaultValue) {
        if (ordinal < 0 || ordinal >= ORDINAL_LOOKUP.length) {
            return defaultValue;
        }
        return ORDINAL_LOOKUP[ordinal];
    }
}
