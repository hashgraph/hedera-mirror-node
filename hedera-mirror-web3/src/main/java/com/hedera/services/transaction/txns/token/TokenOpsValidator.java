package com.hedera.services.transaction.txns.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

public final class TokenOpsValidator {
    /**
     * Validate the token operations mint/wipe/burn given the attributes of the transaction.
     *
     * @param nftCount The number of unique nfts to mint/wipe/burn.
     * @param fungibleCount The number of fungible common token to mint/wipe/burn.
     * @param areNftEnabled A boolean that specifies if NFTs are enabled in the network.
     * @param invalidTokenAmountResponse Respective response code for invalid token amount in
     *     mint/wipe/burn operations.
     * @param metaDataList either metadata of the nfts being minted or serialNumber list of the
     *     burn/wipe operations.
     * @param batchSizeCheck validation method to check if the batch size of requested nfts is
     *     valid.
     * @param nftMetaDataCheck validation method to check if the metadata of minted nft is valid.
     * @return The validity of the token operation.
     */
    public static ResponseCodeEnum validateTokenOpsWith(
            final int nftCount,
            final long fungibleCount,
            final boolean areNftEnabled,
            final ResponseCodeEnum invalidTokenAmountResponse,
            final List<ByteString> metaDataList,
            final IntFunction<ResponseCodeEnum> batchSizeCheck,
            Function<byte[], ResponseCodeEnum> nftMetaDataCheck) {
        var validity =
                validateCounts(
                        nftCount,
                        fungibleCount,
                        areNftEnabled,
                        invalidTokenAmountResponse,
                        batchSizeCheck);

        if (validity != OK) {
            return validity;
        }

        if (fungibleCount <= 0 && nftCount > 0) {
            return validateMetaData(metaDataList, nftMetaDataCheck);
        }
        return OK;
    }

    private static ResponseCodeEnum validateCounts(
            final int nftCount,
            final long fungibleCount,
            final boolean areNftEnabled,
            final ResponseCodeEnum invalidTokenAmount,
            final IntFunction<ResponseCodeEnum> batchSizeCheck) {
        if (nftCount > 0 && !areNftEnabled) {
            return NOT_SUPPORTED;
        }

        boolean bothPresent = (fungibleCount > 0 && nftCount > 0);
        boolean nonePresent = (fungibleCount <= 0 && nftCount == 0);
        if (nonePresent) {
            return invalidTokenAmount;
        }
        if (bothPresent) {
            return INVALID_TRANSACTION_BODY;
        }

        if (fungibleCount <= 0 && nftCount > 0) {
            return batchSizeCheck.apply(nftCount);
        }
        return OK;
    }

    private static ResponseCodeEnum validateMetaData(
            final List<ByteString> metaDataList,
            Function<byte[], ResponseCodeEnum> nftMetaDataCheck) {
        for (var bytes : metaDataList) {
            var validity = nftMetaDataCheck.apply(bytes.toByteArray());
            if (validity != OK) {
                return validity;
            }
        }
        return OK;
    }
}
