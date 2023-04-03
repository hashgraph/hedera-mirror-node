package com.hedera.services.store.contracts.precompile.utils;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;

public enum GasCostType {
    APPROVE(CryptoApproveAllowance, DEFAULT),
    ASSOCIATE(TokenAssociateToAccount, DEFAULT),
    BURN_FUNGIBLE(TokenBurn, TOKEN_FUNGIBLE_COMMON),
    BURN_NFT(TokenBurn, TOKEN_NON_FUNGIBLE_UNIQUE),
    CRYPTO_CREATE(CryptoCreate, DEFAULT),
    CRYPTO_UPDATE(CryptoUpdate, DEFAULT),
    DELETE(TokenDelete, DEFAULT),
    DELETE_NFT_APPROVE(CryptoDeleteAllowance, DEFAULT),
    DISSOCIATE(TokenDissociateFromAccount, DEFAULT),
    FREEZE(TokenFreezeAccount, DEFAULT),
    GRANT_KYC(TokenGrantKycToAccount, DEFAULT),
    MINT_FUNGIBLE(TokenMint, TOKEN_FUNGIBLE_COMMON),
    MINT_NFT(TokenMint, TOKEN_NON_FUNGIBLE_UNIQUE),
    PAUSE(TokenPause, DEFAULT),
    PRNG(HederaFunctionality.UtilPrng, DEFAULT),
    REVOKE_KYC(TokenRevokeKycFromAccount, DEFAULT),
    TRANSFER_FUNGIBLE(CryptoTransfer, TOKEN_FUNGIBLE_COMMON),
    TRANSFER_FUNGIBLE_CUSTOM_FEES(CryptoTransfer, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES),
    TRANSFER_HBAR(CryptoTransfer, DEFAULT),
    TRANSFER_NFT(CryptoTransfer, TOKEN_NON_FUNGIBLE_UNIQUE),
    TRANSFER_NFT_CUSTOM_FEES(CryptoTransfer, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES),
    UNFREEZE(TokenUnfreezeAccount, DEFAULT),
    UNPAUSE(TokenUnpause, DEFAULT),
    UNRECOGNIZED(HederaFunctionality.UNRECOGNIZED, SubType.UNRECOGNIZED),
    UPDATE(TokenUpdate, DEFAULT),
    WIPE_FUNGIBLE(TokenAccountWipe, TOKEN_FUNGIBLE_COMMON),
    WIPE_NFT(TokenAccountWipe, TOKEN_NON_FUNGIBLE_UNIQUE);

    final HederaFunctionality functionality;
    final SubType subtype;

    GasCostType(final HederaFunctionality functionality, final SubType subtype) {
        this.functionality = functionality;
        this.subtype = subtype;
    }
}
