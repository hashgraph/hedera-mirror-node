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

import com.google.protobuf.GeneratedMessageV3;

import com.hedera.services.jproto.JKey;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.codec.DecoderException;
import java.util.List;
import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAddLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UncheckedSubmit;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UtilPrng;
import static java.util.Objects.requireNonNull;

public final class MiscUtils {

    private MiscUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static HederaFunctionality functionOf(@NonNull final TransactionBody txn) throws Exception {
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
            case FILEAPPEND -> FileAppend;
            case FILECREATE -> FileCreate;
            case FILEDELETE -> FileDelete;
            case FILEUPDATE -> FileUpdate;
            case SYSTEMDELETE -> SystemDelete;
            case SYSTEMUNDELETE -> SystemUndelete;
            case FREEZE -> Freeze;
            case CONSENSUSCREATETOPIC -> ConsensusCreateTopic;
            case CONSENSUSUPDATETOPIC -> ConsensusUpdateTopic;
            case CONSENSUSDELETETOPIC -> ConsensusDeleteTopic;
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
            case TOKEN_FEE_SCHEDULE_UPDATE -> TokenFeeScheduleUpdate;
            case TOKEN_PAUSE -> TokenPause;
            case TOKEN_UNPAUSE -> TokenUnpause;
            case SCHEDULECREATE -> ScheduleCreate;
            case SCHEDULEDELETE -> ScheduleDelete;
            case SCHEDULESIGN -> ScheduleSign;
            case UTIL_PRNG -> UtilPrng;
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


    public static JKey asFcKeyUnchecked(final Key key) {
        try {
            return JKey.mapKey(key);
        } catch (final DecoderException impermissible) {
            throw new IllegalArgumentException("Key " + key + " should have been decode-able!", impermissible);
        }
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

    public static boolean hasUnknownFieldsHere(final GeneratedMessageV3 msg) {
        return !msg.getUnknownFields().asMap().isEmpty();
    }
}
