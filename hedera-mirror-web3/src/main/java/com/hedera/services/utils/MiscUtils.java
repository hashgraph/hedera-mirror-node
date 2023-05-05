/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.utils;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.GeneratedMessageV3;
import com.hedera.services.jproto.JKey;
import com.hederahashgraph.api.proto.java.*;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.codec.DecoderException;

public final class MiscUtils {

    private MiscUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static HederaFunctionality functionOf(final TransactionBody txn) throws Exception {
        requireNonNull(txn);
        TransactionBody.DataCase dataCase = txn.getDataCase();

        return switch (dataCase) {
            case CONTRACTCALL -> ContractCall;
            case CONTRACTCREATEINSTANCE -> ContractCreate;
            case CONTRACTUPDATEINSTANCE -> ContractUpdate;
            case CONTRACTDELETEINSTANCE -> ContractDelete;
            case ETHEREUMTRANSACTION -> EthereumTransaction;
            case CRYPTOADDLIVEHASH -> CryptoAddLiveHash;
            case CRYPTOAPPROVEALLOWANCE -> CryptoApproveAllowance;
            case CRYPTODELETEALLOWANCE -> CryptoDeleteAllowance;
            case CRYPTOCREATEACCOUNT -> CryptoCreate;
            case CRYPTODELETE -> CryptoDelete;
            case CRYPTODELETELIVEHASH -> CryptoDeleteLiveHash;
            case CRYPTOTRANSFER -> CryptoTransfer;
            case CRYPTOUPDATEACCOUNT -> CryptoUpdate;
            case FREEZE -> Freeze;
            case CONSENSUSSUBMITMESSAGE -> ConsensusSubmitMessage;
            case UNCHECKEDSUBMIT -> UncheckedSubmit;
            case TOKENCREATION -> TokenCreate;
            case TOKENFREEZE -> TokenFreezeAccount;
            case TOKENUNFREEZE -> TokenUnfreezeAccount;
            case TOKENGRANTKYC -> TokenGrantKycToAccount;
            case TOKENREVOKEKYC -> TokenRevokeKycFromAccount;
            case TOKENDELETION -> TokenDelete;
            case TOKENUPDATE -> TokenUpdate;
            case TOKENMINT -> TokenMint;
            case TOKENBURN -> TokenBurn;
            case TOKENWIPE -> TokenAccountWipe;
            case TOKENASSOCIATE -> TokenAssociateToAccount;
            case TOKENDISSOCIATE -> TokenDissociateFromAccount;
            case TOKEN_PAUSE -> TokenPause;
            case TOKEN_UNPAUSE -> TokenUnpause;
            default -> throw new Exception("Unknown HederaFunctionality for " + txn);
        };
    }

    public static final Function<TransactionBody, HederaFunctionality> FUNCTION_EXTRACTOR = trans -> {
        try {
            return functionOf(trans);
        } catch (Exception ignore) {
            return NONE;
        }
    };

    public static long perm64(long x) {
        // Shifts: {30, 27, 16, 20, 5, 18, 10, 24, 30}
        x += x << 30;
        x ^= x >>> 27;
        x += x << 16;
        x ^= x >>> 20;
        x += x << 5;
        x ^= x >>> 18;
        x += x << 10;
        x ^= x >>> 24;
        x += x << 30;
        return x;
    }

    public static boolean hasUnknownFieldsHere(final GeneratedMessageV3 msg) {
        return !msg.getUnknownFields().asMap().isEmpty();
    }

    public static boolean hasUnknownFields(final GeneratedMessageV3 msg) {
        if (hasUnknownFieldsHere(msg)) {
            return true;
        }
        var ans = false;
        for (final var field : msg.getAllFields().values()) {
            if (field instanceof GeneratedMessageV3 generatedMessageV3) {
                ans |= hasUnknownFields(generatedMessageV3);
            } else if (field instanceof List<? extends Object> list) {
                for (final var item : list) {
                    if (item instanceof GeneratedMessageV3 generatedMessageV3) {
                        ans |= hasUnknownFields(generatedMessageV3);
                    }
                }
            }
            /* Otherwise the field is a primitive and cannot include unknown fields */
        }
        return ans;
    }

    public static JKey asFcKeyUnchecked(final Key key) {
        try {
            return JKey.mapKey(key);
        } catch (final DecoderException impermissible) {
            throw new IllegalArgumentException("Key " + key + " should have been decode-able!", impermissible);
        }
    }

    public static Optional<JKey> asUsableFcKey(final Key key) {
        try {
            final var fcKey = JKey.mapKey(key);
            if (!fcKey.isValid()) {
                return Optional.empty();
            }
            return Optional.of(fcKey);
        } catch (final DecoderException ignore) {
            return Optional.empty();
        }
    }
}
