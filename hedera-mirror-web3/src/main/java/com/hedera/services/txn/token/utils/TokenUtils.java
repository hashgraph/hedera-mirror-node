package com.hedera.services.txn.token.utils;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class TokenUtils {

    public static boolean hasAssociation(final TokenRelationshipKey tokenRelationshipKey, final Store store) {
        return store.getTokenRelationship(tokenRelationshipKey, OnMissing.DONT_THROW)
                .getAccount()
                .getId()
                .num()
                > 0;
    }


}
