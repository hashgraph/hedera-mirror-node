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

package com.hedera.mirror.web3.evm.store.contract.precompile;

import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.Precompile;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Named
public class PrecompileMapper {

    public static final String UNSUPPORTED_ERROR = "Precompile not supported for non-static frames";
    private final Map<Integer, Precompile> abiConstantToPrecompile = new HashMap<>();

    private final Set<Integer> precompileSelectors = Set.of(
            AbiConstants.ABI_ID_CRYPTO_TRANSFER,
            AbiConstants.ABI_ID_CRYPTO_TRANSFER_V2,
            AbiConstants.ABI_ID_TRANSFER_TOKENS,
            AbiConstants.ABI_ID_TRANSFER_TOKEN,
            AbiConstants.ABI_ID_TRANSFER_NFTS,
            AbiConstants.ABI_ID_TRANSFER_NFT,
            AbiConstants.ABI_ID_MINT_TOKEN,
            AbiConstants.ABI_ID_MINT_TOKEN_V2,
            AbiConstants.ABI_ID_BURN_TOKEN,
            AbiConstants.ABI_ID_BURN_TOKEN_V2,
            AbiConstants.ABI_ID_DELETE_TOKEN,
            AbiConstants.ABI_ID_ASSOCIATE_TOKENS,
            AbiConstants.ABI_ID_ASSOCIATE_TOKEN,
            AbiConstants.ABI_ID_DISSOCIATE_TOKENS,
            AbiConstants.ABI_ID_DISSOCIATE_TOKEN,
            AbiConstants.ABI_ID_PAUSE_TOKEN,
            AbiConstants.ABI_ID_UNPAUSE_TOKEN,
            AbiConstants.ABI_ID_ALLOWANCE,
            AbiConstants.ABI_ID_APPROVE,
            AbiConstants.ABI_ID_APPROVE_NFT,
            AbiConstants.ABI_ID_SET_APPROVAL_FOR_ALL,
            AbiConstants.ABI_ID_GET_APPROVED,
            AbiConstants.ABI_ID_IS_APPROVED_FOR_ALL,
            AbiConstants.ABI_ID_TRANSFER_FROM,
            AbiConstants.ABI_ID_TRANSFER_FROM_NFT,
            AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN,
            AbiConstants.ABI_ID_ERC_NAME,
            AbiConstants.ABI_ID_ERC_SYMBOL,
            AbiConstants.ABI_ID_ERC_DECIMALS,
            AbiConstants.ABI_ID_ERC_TOTAL_SUPPLY_TOKEN,
            AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN,
            AbiConstants.ABI_ID_ERC_TRANSFER,
            AbiConstants.ABI_ID_ERC_TRANSFER_FROM,
            AbiConstants.ABI_ID_ERC_ALLOWANCE,
            AbiConstants.ABI_ID_ERC_APPROVE,
            AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL,
            AbiConstants.ABI_ID_ERC_GET_APPROVED,
            AbiConstants.ABI_ID_ERC_IS_APPROVED_FOR_ALL,
            AbiConstants.ABI_ID_ERC_OWNER_OF_NFT,
            AbiConstants.ABI_ID_ERC_TOKEN_URI_NFT,
            AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE,
            AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE_V2,
            AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_NFT,
            AbiConstants.ABI_ID_IS_FROZEN,
            AbiConstants.ABI_ID_FREEZE,
            AbiConstants.ABI_ID_UNFREEZE,
            AbiConstants.ABI_ID_UPDATE_TOKEN_INFO,
            AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V2,
            AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V3,
            AbiConstants.ABI_ID_UPDATE_TOKEN_KEYS,
            AbiConstants.ABI_ID_GET_TOKEN_KEY,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V2,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V3,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V2,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V3,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V2,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V3,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V2,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V3,
            AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO,
            AbiConstants.ABI_ID_GET_TOKEN_INFO,
            AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO,
            AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS,
            AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS,
            AbiConstants.ABI_ID_IS_KYC,
            AbiConstants.ABI_ID_GRANT_TOKEN_KYC,
            AbiConstants.ABI_ID_REVOKE_TOKEN_KYC,
            AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES,
            AbiConstants.ABI_ID_IS_TOKEN,
            AbiConstants.ABI_ID_GET_TOKEN_TYPE,
            AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO,
            AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO,
            AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO_V2);

    public void setSupportedPrecompiles(final Set<Precompile> precompiles) {
        for (Precompile precompile : precompiles) {
            for (Integer selector : precompile.getFunctionSelectors()) {
                abiConstantToPrecompile.put(selector, precompile);
            }
        }
    }

    public Optional<Precompile> lookup(int functionSelector) {
        final var precompile = abiConstantToPrecompile.get(functionSelector);
        if (precompile != null) {
            return Optional.of(precompile);
        }
        // TODO If the function selector is not mapped but is from the list of HTS precompiles, throw an exception until
        // the given precompile is supported
        else if (precompileSelectors.contains(functionSelector)) {
            throw new UnsupportedOperationException(UNSUPPORTED_ERROR);
        } else {
            return Optional.empty();
        }
    }
}
