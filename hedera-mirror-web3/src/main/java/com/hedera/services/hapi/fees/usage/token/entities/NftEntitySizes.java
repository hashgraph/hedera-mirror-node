package com.hedera.services.hapi.fees.usage.token.entities;

import static com.hedera.services.hapi.utils.fees.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.BASIC_RICH_INSTANT_SIZE;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.LONG_SIZE;

public enum NftEntitySizes {
    NFT_ENTITY_SIZES;

    public long fixedBytesInNftRepr() {
        /* { creation time, tokenId, accountId, serialNum } */
        return BASIC_RICH_INSTANT_SIZE + 2 * BASIC_ENTITY_ID_SIZE + LONG_SIZE;
    }
}
