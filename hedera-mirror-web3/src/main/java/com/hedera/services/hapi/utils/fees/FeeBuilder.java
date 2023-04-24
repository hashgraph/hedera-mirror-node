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
package com.hedera.services.hapi.utils.fees;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.Key;
import java.math.BigInteger;
import java.util.List;

/**
 * Temporary extracted class from services.
 *
 * <p>This is the base class for building Fee Matrices and calculating the Total as well as specific
 * component Fee for a given Transaction or Query. It includes common methods which is used to calculate Fee for Crypto,
 * File and Smart Contracts Transactions and Query
 */
public class FeeBuilder {

    public static final long MAX_ENTITY_LIFETIME = 100L * 365L * 24L * 60L * 60L;

    public static final int LONG_SIZE = 8;
    public static final int FEE_MATRICES_CONST = 1;
    public static final int INT_SIZE = 4;
    public static final int BOOL_SIZE = 4;
    public static final long SOLIDITY_ADDRESS = 20;
    public static final int KEY_SIZE = 32;
    public static final int TX_HASH_SIZE = 48;
    public static final long RECEIPT_STORAGE_TIME_SEC = 180;
    public static final int THRESHOLD_STORAGE_TIME_SEC = 90000;
    public static final int FEE_DIVISOR_FACTOR = 1000;
    public static final int HRS_DIVISOR = 3600;
    public static final int BASIC_ENTITY_ID_SIZE = (3 * LONG_SIZE);
    public static final long BASIC_RICH_INSTANT_SIZE = (1L * LONG_SIZE) + INT_SIZE;
    public static final int BASIC_ACCOUNT_AMT_SIZE = BASIC_ENTITY_ID_SIZE + LONG_SIZE;
    public static final int BASIC_TX_ID_SIZE = BASIC_ENTITY_ID_SIZE + LONG_SIZE;
    public static final int EXCHANGE_RATE_SIZE = 2 * INT_SIZE + LONG_SIZE;
    public static final int CRYPTO_ALLOWANCE_SIZE = BASIC_ENTITY_ID_SIZE + INT_SIZE + LONG_SIZE; // owner, spender ,
    // amount
    public static final int TOKEN_ALLOWANCE_SIZE = BASIC_ENTITY_ID_SIZE + 2 * INT_SIZE + LONG_SIZE; // owner, tokenNum,
    // spender num, amount
    public static final int NFT_ALLOWANCE_SIZE = BASIC_ENTITY_ID_SIZE + 2 * INT_SIZE + BOOL_SIZE; // owner, tokenNum,
    // spender num, approvedForAll

    public static final int NFT_DELETE_ALLOWANCE_SIZE = 2 * BASIC_ENTITY_ID_SIZE; // owner, tokenID

    /**
     * Fields included: status, exchangeRate.
     */
    public static final int BASIC_RECEIPT_SIZE = INT_SIZE + 2 * EXCHANGE_RATE_SIZE;
    /**
     * Fields included: transactionID, nodeAccountID, transactionFee, transactionValidDuration, generateRecord
     */
    public static final int BASIC_TX_BODY_SIZE =
            BASIC_ENTITY_ID_SIZE + BASIC_TX_ID_SIZE + LONG_SIZE + (LONG_SIZE) + BOOL_SIZE;

    public static final int STATE_PROOF_SIZE = 2000;
    public static final int BASE_FILEINFO_SIZE = BASIC_ENTITY_ID_SIZE + LONG_SIZE + (LONG_SIZE) + BOOL_SIZE;
    public static final int BASIC_ACCOUNT_SIZE = 8 * LONG_SIZE + BOOL_SIZE;
    /**
     * Fields included: nodeTransactionPrecheckCode, responseType, cost
     */
    public static final long BASIC_QUERY_RES_HEADER = 2L * INT_SIZE + LONG_SIZE;

    public static final long BASIC_QUERY_HEADER = 212L;
    public static final int BASIC_CONTRACT_CREATE_SIZE = BASIC_ENTITY_ID_SIZE + 6 * LONG_SIZE;
    public static final long BASIC_CONTRACT_INFO_SIZE = 2L * BASIC_ENTITY_ID_SIZE + SOLIDITY_ADDRESS + BASIC_TX_ID_SIZE;
    /**
     * Fields included in size: receipt (basic size), transactionHash, consensusTimestamp, transactionID
     * transactionFee.
     */
    public static final int BASIC_TX_RECORD_SIZE =
            BASIC_RECEIPT_SIZE + TX_HASH_SIZE + LONG_SIZE + BASIC_TX_ID_SIZE + LONG_SIZE;

    private FeeBuilder() {
    }

    /**
     * Convert tinyCents to tinybars
     *
     * @param exchangeRate exchange rate
     * @param tinyCentsFee tiny cents fee
     * @return tinyHbars
     */
    public static long getTinybarsFromTinyCents(final ExchangeRate exchangeRate, final long tinyCentsFee) {
        return getAFromB(tinyCentsFee, exchangeRate.getHbarEquiv(), exchangeRate.getCentEquiv());
    }

    /**
     * This method returns the Key size in bytes
     *
     * @param key key
     * @return int representing account key storage size
     */
    public static int getAccountKeyStorageSize(final Key key) {

        if (key == null) {
            return 0;
        }
        if (key == Key.getDefaultInstance()) {
            return 0;
        }

        int[] countKeyMetatData = {0, 0};
        countKeyMetatData = calculateKeysMetadata(key, countKeyMetatData);

        return countKeyMetatData[0] * KEY_SIZE + countKeyMetatData[1] * INT_SIZE;
    }

    /**
     * This method calculates number of keys
     *
     * @param key key
     * @param count count array
     * @return int array containing key metadata
     */
    public static int[] calculateKeysMetadata(final Key key, int[] count) {
        if (key.hasKeyList()) {
            final List<Key> keyList = key.getKeyList().getKeysList();
            for (final Key value : keyList) {
                count = calculateKeysMetadata(value, count);
            }
        } else if (key.hasThresholdKey()) {
            final List<Key> keyList = key.getThresholdKey().getKeys().getKeysList();
            count[1]++;
            for (final Key value : keyList) {
                count = calculateKeysMetadata(value, count);
            }
        } else {
            count[0]++;
        }
        return count;
    }

    private static long getAFromB(final long bAmount, final int aEquiv, final int bEquiv) {
        final var aMultiplier = BigInteger.valueOf(aEquiv);
        final var bDivisor = BigInteger.valueOf(bEquiv);
        return BigInteger.valueOf(bAmount)
                .multiply(aMultiplier)
                .divide(bDivisor)
                .longValueExact();
    }
}
