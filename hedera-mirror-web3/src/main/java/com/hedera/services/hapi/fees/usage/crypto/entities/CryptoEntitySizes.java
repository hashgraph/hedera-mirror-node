package com.hedera.services.hapi.fees.usage.crypto.entities;

import static com.hedera.services.hapi.utils.fees.FeeBuilder.BOOL_SIZE;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.LONG_SIZE;

public enum CryptoEntitySizes {
    CRYPTO_ENTITY_SIZES;

    /* { deleted, smartContract, receiverSigRequired } */
    private static final int NUM_FLAGS_IN_BASE_ACCOUNT_REPRESENTATION = 3;
    /* { expiry, hbarBalance, autoRenewSecs } + (LEGACY) { sendThreshold, receiveThreshold } */
    private static final int NUM_LONG_FIELDS_IN_BASE_ACCOUNT_REPRESENTATION = 5;
    /* { maxAutomaticAssociations }*/
    private static final int NUM_INT_FIELDS_IN_BASE_ACCOUNT_REPRESENTATION = 1;

    public long bytesInTokenAssocRepr() {
        return LONG_SIZE + 3L * BOOL_SIZE;
    }

    public int fixedBytesInAccountRepr() {
        return NUM_FLAGS_IN_BASE_ACCOUNT_REPRESENTATION * BOOL_SIZE
                + NUM_LONG_FIELDS_IN_BASE_ACCOUNT_REPRESENTATION * LONG_SIZE;
    }
}
