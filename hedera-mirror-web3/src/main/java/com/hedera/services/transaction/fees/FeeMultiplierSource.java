package com.hedera.services.transaction.fees;

import com.hedera.services.transaction.utils.accessors.TxnAccessor;

public interface FeeMultiplierSource {

    long currentMultiplier(final TxnAccessor accessor);
}
