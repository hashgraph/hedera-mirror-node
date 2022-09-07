package com.hedera.mirror.web3.evm;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import javax.inject.Named;

@Named
public class PrecompilePricingUtils {

    //FUTURE WORK to be implemented
    public long getMinimumPriceInTinybars(GasCostType gasCostType, Timestamp timestamp) {
        return 100L;
    }

    public enum GasCostType {
        UNRECOGNIZED(HederaFunctionality.UNRECOGNIZED, SubType.UNRECOGNIZED),
        MINT_FUNGIBLE(TokenMint, TOKEN_FUNGIBLE_COMMON),
        MINT_NFT(TokenMint, TOKEN_NON_FUNGIBLE_UNIQUE);

        final HederaFunctionality functionality;
        final SubType subtype;

        GasCostType(HederaFunctionality functionality, SubType subtype) {
            this.functionality = functionality;
            this.subtype = subtype;
        }
    }
}
