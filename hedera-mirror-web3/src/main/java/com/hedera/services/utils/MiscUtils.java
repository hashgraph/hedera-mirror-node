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

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;

import com.hederahashgraph.api.proto.java.*;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.common.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.*;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

public final class MiscUtils {
    private static final long ONE_SEC_IN_NANOS = 1_000_000_000;
    private static final Logger log = LogManager.getLogger(MiscUtils.class);

    private MiscUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static final Set<HederaFunctionality> QUERY_FUNCTIONS = EnumSet.of(
            ConsensusGetTopicInfo,
            GetBySolidityID,
            ContractCallLocal,
            ContractGetInfo,
            ContractGetBytecode,
            ContractGetRecords,
            CryptoGetAccountBalance,
            CryptoGetAccountRecords,
            CryptoGetInfo,
            CryptoGetLiveHash,
            FileGetContents,
            FileGetInfo,
            TransactionGetReceipt,
            TransactionGetRecord,
            GetVersionInfo,
            TokenGetInfo,
            ScheduleGetInfo,
            TokenGetNftInfo,
            TokenGetNftInfos,
            TokenGetAccountNftInfos,
            NetworkGetExecutionTime,
            GetAccountDetails);

    private static final Set<HederaFunctionality> CONSENSUS_THROTTLED_FUNCTIONS =
            EnumSet.of(ContractCallLocal, ContractCall, ContractCreate, EthereumTransaction);

    public static final Function<TransactionBody, HederaFunctionality> FUNCTION_EXTRACTOR = trans -> {
        try {
            return functionOf(trans);
        } catch (UnknownHederaFunctionality ignore) {
            return NONE;
        }
    };

    static final String TOKEN_MINT_METRIC = "mintToken";
    static final String TOKEN_BURN_METRIC = "burnToken";
    static final String TOKEN_CREATE_METRIC = "createToken";
    static final String TOKEN_DELETE_METRIC = "deleteToken";
    static final String TOKEN_UPDATE_METRIC = "updateToken";
    static final String TOKEN_FREEZE_METRIC = "freezeTokenAccount";
    static final String TOKEN_UNFREEZE_METRIC = "unfreezeTokenAccount";
    static final String TOKEN_GRANT_KYC_METRIC = "grantKycToTokenAccount";
    static final String TOKEN_REVOKE_KYC_METRIC = "revokeKycFromTokenAccount";
    static final String TOKEN_WIPE_ACCOUNT_METRIC = "wipeTokenAccount";
    static final String TOKEN_ASSOCIATE_METRIC = "associateTokens";
    static final String TOKEN_DISSOCIATE_METRIC = "dissociateTokens";
    static final String TOKEN_GET_INFO_METRIC = "getTokenInfo";
    static final String TOKEN_GET_NFT_INFO_METRIC = "getTokenNftInfo";
    static final String TOKEN_GET_ACCOUNT_NFT_INFOS_METRIC = "getAccountNftInfos";
    static final String TOKEN_FEE_SCHEDULE_UPDATE_METRIC = "tokenFeeScheduleUpdate";
    static final String TOKEN_GET_NFT_INFOS_METRIC = "getTokenNftInfos";
    static final String TOKEN_PAUSE_METRIC = "tokenPause";
    static final String TOKEN_UNPAUSE_METRIC = "tokenUnpause";

    static final String SCHEDULE_CREATE_METRIC = "createSchedule";
    static final String SCHEDULE_DELETE_METRIC = "deleteSchedule";
    static final String SCHEDULE_SIGN_METRIC = "signSchedule";
    static final String SCHEDULE_GET_INFO_METRIC = "getScheduleInfo";
    static final String UTIL_PRNG_METRIC = "utilPrng";

    private static final Map<Query.QueryCase, HederaFunctionality> queryFunctions =
            new EnumMap<>(Query.QueryCase.class);

    static {
        queryFunctions.put(NETWORKGETVERSIONINFO, GetVersionInfo);
        queryFunctions.put(GETBYKEY, GetByKey);
        queryFunctions.put(CONSENSUSGETTOPICINFO, ConsensusGetTopicInfo);
        queryFunctions.put(GETBYSOLIDITYID, GetBySolidityID);
        queryFunctions.put(CONTRACTCALLLOCAL, ContractCallLocal);
        queryFunctions.put(CONTRACTGETINFO, ContractGetInfo);
        queryFunctions.put(CONTRACTGETBYTECODE, ContractGetBytecode);
        queryFunctions.put(CONTRACTGETRECORDS, ContractGetRecords);
        queryFunctions.put(CRYPTOGETACCOUNTBALANCE, CryptoGetAccountBalance);
        queryFunctions.put(CRYPTOGETACCOUNTRECORDS, CryptoGetAccountRecords);
        queryFunctions.put(CRYPTOGETINFO, CryptoGetInfo);
        queryFunctions.put(CRYPTOGETLIVEHASH, CryptoGetLiveHash);
        queryFunctions.put(FILEGETCONTENTS, FileGetContents);
        queryFunctions.put(FILEGETINFO, FileGetInfo);
        queryFunctions.put(TRANSACTIONGETRECEIPT, TransactionGetReceipt);
        queryFunctions.put(TRANSACTIONGETRECORD, TransactionGetRecord);
        queryFunctions.put(TOKENGETINFO, TokenGetInfo);
        queryFunctions.put(TOKENGETNFTINFO, TokenGetNftInfo);
        queryFunctions.put(TOKENGETNFTINFOS, TokenGetNftInfos);
        queryFunctions.put(TOKENGETACCOUNTNFTINFOS, TokenGetAccountNftInfos);
        queryFunctions.put(SCHEDULEGETINFO, ScheduleGetInfo);
        queryFunctions.put(NETWORKGETEXECUTIONTIME, NetworkGetExecutionTime);
        queryFunctions.put(ACCOUNTDETAILS, GetAccountDetails);
    }

    private static final Map<HederaFunctionality, String> BASE_STAT_NAMES = new EnumMap<>(HederaFunctionality.class);

    static {
        /* Transactions */
        BASE_STAT_NAMES.put(CryptoCreate, CRYPTO_CREATE_METRIC);
        BASE_STAT_NAMES.put(CryptoTransfer, CRYPTO_TRANSFER_METRIC);
        BASE_STAT_NAMES.put(CryptoUpdate, CRYPTO_UPDATE_METRIC);
        BASE_STAT_NAMES.put(CryptoDelete, CRYPTO_DELETE_METRIC);
        BASE_STAT_NAMES.put(CryptoAddLiveHash, ADD_LIVE_HASH_METRIC);
        BASE_STAT_NAMES.put(CryptoDeleteLiveHash, DELETE_LIVE_HASH_METRIC);
        BASE_STAT_NAMES.put(CryptoApproveAllowance, CRYPTO_APPROVE_ALLOWANCES);
        BASE_STAT_NAMES.put(CryptoDeleteAllowance, CRYPTO_DELETE_ALLOWANCE);
        BASE_STAT_NAMES.put(FileCreate, CREATE_FILE_METRIC);
        BASE_STAT_NAMES.put(FileUpdate, UPDATE_FILE_METRIC);
        BASE_STAT_NAMES.put(FileDelete, DELETE_FILE_METRIC);
        BASE_STAT_NAMES.put(FileAppend, FILE_APPEND_METRIC);
        BASE_STAT_NAMES.put(ContractCreate, CREATE_CONTRACT_METRIC);
        BASE_STAT_NAMES.put(ContractUpdate, UPDATE_CONTRACT_METRIC);
        BASE_STAT_NAMES.put(ContractCall, CALL_CONTRACT_METRIC);
        BASE_STAT_NAMES.put(EthereumTransaction, CALL_ETHEREUM_METRIC);
        BASE_STAT_NAMES.put(ContractDelete, DELETE_CONTRACT_METRIC);
        BASE_STAT_NAMES.put(ConsensusCreateTopic, CREATE_TOPIC_METRIC);
        BASE_STAT_NAMES.put(ConsensusUpdateTopic, UPDATE_TOPIC_METRIC);
        BASE_STAT_NAMES.put(ConsensusDeleteTopic, DELETE_TOPIC_METRIC);
        BASE_STAT_NAMES.put(ConsensusSubmitMessage, SUBMIT_MESSAGE_METRIC);
        BASE_STAT_NAMES.put(TokenCreate, TOKEN_CREATE_METRIC);
        BASE_STAT_NAMES.put(TokenFreezeAccount, TOKEN_FREEZE_METRIC);
        BASE_STAT_NAMES.put(TokenUnfreezeAccount, TOKEN_UNFREEZE_METRIC);
        BASE_STAT_NAMES.put(TokenGrantKycToAccount, TOKEN_GRANT_KYC_METRIC);
        BASE_STAT_NAMES.put(TokenRevokeKycFromAccount, TOKEN_REVOKE_KYC_METRIC);
        BASE_STAT_NAMES.put(TokenDelete, TOKEN_DELETE_METRIC);
        BASE_STAT_NAMES.put(TokenMint, TOKEN_MINT_METRIC);
        BASE_STAT_NAMES.put(TokenBurn, TOKEN_BURN_METRIC);
        BASE_STAT_NAMES.put(TokenAccountWipe, TOKEN_WIPE_ACCOUNT_METRIC);
        BASE_STAT_NAMES.put(TokenUpdate, TOKEN_UPDATE_METRIC);
        BASE_STAT_NAMES.put(TokenAssociateToAccount, TOKEN_ASSOCIATE_METRIC);
        BASE_STAT_NAMES.put(TokenDissociateFromAccount, TOKEN_DISSOCIATE_METRIC);
        BASE_STAT_NAMES.put(TokenPause, TOKEN_PAUSE_METRIC);
        BASE_STAT_NAMES.put(TokenUnpause, TOKEN_UNPAUSE_METRIC);
        BASE_STAT_NAMES.put(ScheduleCreate, SCHEDULE_CREATE_METRIC);
        BASE_STAT_NAMES.put(ScheduleSign, SCHEDULE_SIGN_METRIC);
        BASE_STAT_NAMES.put(ScheduleDelete, SCHEDULE_DELETE_METRIC);
        BASE_STAT_NAMES.put(UncheckedSubmit, UNCHECKED_SUBMIT_METRIC);
        BASE_STAT_NAMES.put(Freeze, FREEZE_METRIC);
        BASE_STAT_NAMES.put(SystemDelete, SYSTEM_DELETE_METRIC);
        BASE_STAT_NAMES.put(SystemUndelete, SYSTEM_UNDELETE_METRIC);
        BASE_STAT_NAMES.put(UtilPrng, UTIL_PRNG_METRIC);
        /* Queries */
        BASE_STAT_NAMES.put(ConsensusGetTopicInfo, GET_TOPIC_INFO_METRIC);
        BASE_STAT_NAMES.put(GetBySolidityID, GET_SOLIDITY_ADDRESS_INFO_METRIC);
        BASE_STAT_NAMES.put(ContractCallLocal, LOCALCALL_CONTRACT_METRIC);
        BASE_STAT_NAMES.put(ContractGetInfo, GET_CONTRACT_INFO_METRIC);
        BASE_STAT_NAMES.put(ContractGetBytecode, GET_CONTRACT_BYTECODE_METRIC);
        BASE_STAT_NAMES.put(ContractGetRecords, GET_CONTRACT_RECORDS_METRIC);
        BASE_STAT_NAMES.put(CryptoGetAccountBalance, GET_ACCOUNT_BALANCE_METRIC);
        BASE_STAT_NAMES.put(CryptoGetAccountRecords, GET_ACCOUNT_RECORDS_METRIC);
        BASE_STAT_NAMES.put(CryptoGetInfo, GET_ACCOUNT_INFO_METRIC);
        BASE_STAT_NAMES.put(CryptoGetLiveHash, GET_LIVE_HASH_METRIC);
        BASE_STAT_NAMES.put(FileGetContents, GET_FILE_CONTENT_METRIC);
        BASE_STAT_NAMES.put(FileGetInfo, GET_FILE_INFO_METRIC);
        BASE_STAT_NAMES.put(TransactionGetReceipt, GET_RECEIPT_METRIC);
        BASE_STAT_NAMES.put(TransactionGetRecord, GET_RECORD_METRIC);
        BASE_STAT_NAMES.put(GetVersionInfo, GET_VERSION_INFO_METRIC);
        BASE_STAT_NAMES.put(TokenGetInfo, TOKEN_GET_INFO_METRIC);
        BASE_STAT_NAMES.put(TokenGetNftInfo, TOKEN_GET_NFT_INFO_METRIC);
        BASE_STAT_NAMES.put(TokenGetNftInfos, TOKEN_GET_NFT_INFOS_METRIC);
        BASE_STAT_NAMES.put(ScheduleGetInfo, SCHEDULE_GET_INFO_METRIC);
        BASE_STAT_NAMES.put(TokenGetAccountNftInfos, TOKEN_GET_ACCOUNT_NFT_INFOS_METRIC);
        BASE_STAT_NAMES.put(TokenFeeScheduleUpdate, TOKEN_FEE_SCHEDULE_UPDATE_METRIC);
        BASE_STAT_NAMES.put(NetworkGetExecutionTime, GET_EXECUTION_TIME_METRIC);
        BASE_STAT_NAMES.put(GetAccountDetails, GET_ACCOUNT_DETAILS_METRIC);
    }

    public static String baseStatNameOf(final HederaFunctionality function) {
        return BASE_STAT_NAMES.getOrDefault(function, function.toString());
    }

    public static List<AccountAmount> canonicalDiffRepr(final List<AccountAmount> a, final List<AccountAmount> b) {
        return canonicalRepr(
                Stream.concat(a.stream(), b.stream().map(MiscUtils::negationOf)).toList());
    }

    private static AccountAmount negationOf(final AccountAmount adjustment) {
        return adjustment.toBuilder().setAmount(-1 * adjustment.getAmount()).build();
    }

    public static List<AccountAmount> canonicalRepr(final List<AccountAmount> transfers) {
        return transfers.stream()
                .collect(toMap(AccountAmount::getAccountID, AccountAmount::getAmount, Math::addExact))
                .entrySet()
                .stream()
                .filter(e -> e.getValue() != 0)
                .sorted(Map.Entry.comparingByKey(HederaLedger.ACCOUNT_ID_COMPARATOR))
                .map(e -> AccountAmount.newBuilder()
                        .setAccountID(e.getKey())
                        .setAmount(e.getValue())
                        .build())
                .toList();
    }

    public static String readableTransferList(final TransferList accountAmounts) {
        return accountAmounts.getAccountAmountsList().stream()
                .map(aa -> String.format(
                        "%s %s %s%s",
                        EntityIdUtils.readableId(aa.getAccountID()),
                        aa.getAmount() < 0 ? "->" : "<-",
                        aa.getAmount() < 0 ? "-" : "+",
                        BigInteger.valueOf(aa.getAmount()).abs().toString()))
                .toList()
                .toString();
    }

    public static String readableNftTransferList(final TokenTransferList tokenTransferList) {
        return tokenTransferList.getNftTransfersList().stream()
                .map(nftTransfer -> String.format(
                        "%s %s %s",
                        nftTransfer.getSerialNumber(),
                        EntityIdUtils.readableId(nftTransfer.getSenderAccountID()),
                        EntityIdUtils.readableId(nftTransfer.getReceiverAccountID())))
                .toList()
                .toString();
    }

    public static String readableProperty(final Object o) {
        if (o instanceof FCQueue) {
            return ExpirableTxnRecord.allToGrpc(new ArrayList<>((FCQueue<ExpirableTxnRecord>) o))
                    .toString();
        } else {
            return Objects.toString(o);
        }
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

    public static Key asKeyUnchecked(final JKey fcKey) {
        try {
            return mapJKey(fcKey);
        } catch (final Exception impossible) {
            return Key.getDefaultInstance();
        }
    }

    public static com.hedera.hapi.node.base.Key asPbjKeyUnchecked(final JKey fcKey) {
        try {
            return protoToPbj(mapJKey(fcKey), com.hedera.hapi.node.base.Key.class);
        } catch (final Exception impossible) {
            return com.hedera.hapi.node.base.Key.newBuilder().build();
        }
    }

    public static Timestamp asSecondsTimestamp(final long now) {
        return Timestamp.newBuilder().setSeconds(now).build();
    }

    public static Timestamp asTimestamp(final long packedTime) {
        return Timestamp.newBuilder()
                .setSeconds(unsignedHighOrder32From(packedTime))
                .setNanos(signedLowOrder32From(packedTime))
                .build();
    }

    public static Timestamp asTimestamp(final Instant when) {
        return Timestamp.newBuilder()
                .setSeconds(when.getEpochSecond())
                .setNanos(when.getNano())
                .build();
    }

    public static Timestamp asTimestamp(final RichInstant when) {
        return Timestamp.newBuilder()
                .setSeconds(when.getSeconds())
                .setNanos(when.getNanos())
                .build();
    }

    public static Instant timestampToInstant(final Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    public static Optional<QueryHeader> activeHeaderFrom(final Query query) {
        switch (query.getQueryCase()) {
            case TOKENGETNFTINFO:
                return Optional.of(query.getTokenGetNftInfo().getHeader());
            case TOKENGETNFTINFOS:
                return Optional.of(query.getTokenGetNftInfos().getHeader());
            case TOKENGETACCOUNTNFTINFOS:
                return Optional.of(query.getTokenGetAccountNftInfos().getHeader());
            case TOKENGETINFO:
                return Optional.of(query.getTokenGetInfo().getHeader());
            case SCHEDULEGETINFO:
                return Optional.of(query.getScheduleGetInfo().getHeader());
            case CONSENSUSGETTOPICINFO:
                return Optional.of(query.getConsensusGetTopicInfo().getHeader());
            case GETBYSOLIDITYID:
                return Optional.of(query.getGetBySolidityID().getHeader());
            case CONTRACTCALLLOCAL:
                return Optional.of(query.getContractCallLocal().getHeader());
            case CONTRACTGETINFO:
                return Optional.of(query.getContractGetInfo().getHeader());
            case CONTRACTGETBYTECODE:
                return Optional.of(query.getContractGetBytecode().getHeader());
            case CONTRACTGETRECORDS:
                return Optional.of(query.getContractGetRecords().getHeader());
            case CRYPTOGETACCOUNTBALANCE:
                return Optional.of(query.getCryptogetAccountBalance().getHeader());
            case CRYPTOGETACCOUNTRECORDS:
                return Optional.of(query.getCryptoGetAccountRecords().getHeader());
            case CRYPTOGETINFO:
                return Optional.of(query.getCryptoGetInfo().getHeader());
            case CRYPTOGETLIVEHASH:
                return Optional.of(query.getCryptoGetLiveHash().getHeader());
            case CRYPTOGETPROXYSTAKERS:
                return Optional.of(query.getCryptoGetProxyStakers().getHeader());
            case FILEGETCONTENTS:
                return Optional.of(query.getFileGetContents().getHeader());
            case FILEGETINFO:
                return Optional.of(query.getFileGetInfo().getHeader());
            case TRANSACTIONGETRECEIPT:
                return Optional.of(query.getTransactionGetReceipt().getHeader());
            case TRANSACTIONGETRECORD:
                return Optional.of(query.getTransactionGetRecord().getHeader());
            case TRANSACTIONGETFASTRECORD:
                return Optional.of(query.getTransactionGetFastRecord().getHeader());
            case NETWORKGETVERSIONINFO:
                return Optional.of(query.getNetworkGetVersionInfo().getHeader());
            case NETWORKGETEXECUTIONTIME:
                return Optional.of(query.getNetworkGetExecutionTime().getHeader());
            case ACCOUNTDETAILS:
                return Optional.of(query.getAccountDetails().getHeader());
            default:
                return Optional.empty();
        }
    }

    static String getTxnStat(final TransactionBody txn) {
        try {
            return BASE_STAT_NAMES.get(functionOf(txn));
        } catch (final UnknownHederaFunctionality unknownHederaFunctionality) {
            return "NotImplemented";
        }
    }

    public static Instant nonNegativeNanosOffset(final Instant start, final int nanosOff) {
        final var oldSecs = start.getEpochSecond();
        final var newNanos = start.getNano() + nanosOff;
        if (newNanos < 0) {
            return Instant.ofEpochSecond(oldSecs - 1, ONE_SEC_IN_NANOS + newNanos);
        } else if (newNanos >= ONE_SEC_IN_NANOS) {
            return Instant.ofEpochSecond(oldSecs + 1, newNanos - ONE_SEC_IN_NANOS);
        } else {
            return Instant.ofEpochSecond(oldSecs, newNanos);
        }
    }

    public static Optional<HederaFunctionality> functionalityOfQuery(final Query query) {
        return Optional.ofNullable(queryFunctions.get(query.getQueryCase()));
    }

    public static String describe(final JKey k) {
        if (k == null) {
            return "<N/A>";
        }
        try {
            return mapJKey(k).toString();
        } catch (final DecoderException ignore) {
            return "<N/A>";
        }
    }

    public static Set<AccountID> getNodeAccounts(final AddressBook addressBook) {
        return IntStream.range(0, addressBook.getSize())
                .mapToObj(addressBook::getAddress)
                .map(address -> parseAccount(address.getMemo()))
                .collect(toSet());
    }

    public static TransactionBody asOrdinary(
            final SchedulableTransactionBody scheduledTxn, final TransactionID scheduledTxnTransactionId) {
        final var ordinary = TransactionBody.newBuilder();
        ordinary.setTransactionFee(scheduledTxn.getTransactionFee())
                .setMemo(scheduledTxn.getMemo())
                .setTransactionID(TransactionID.newBuilder()
                        .mergeFrom(scheduledTxnTransactionId)
                        .setScheduled(true)
                        .build());
        if (scheduledTxn.hasContractCall()) {
            ordinary.setContractCall(scheduledTxn.getContractCall());
        } else if (scheduledTxn.hasContractCreateInstance()) {
            ordinary.setContractCreateInstance(scheduledTxn.getContractCreateInstance());
        } else if (scheduledTxn.hasContractUpdateInstance()) {
            ordinary.setContractUpdateInstance(scheduledTxn.getContractUpdateInstance());
        } else if (scheduledTxn.hasContractDeleteInstance()) {
            ordinary.setContractDeleteInstance(scheduledTxn.getContractDeleteInstance());
        } else if (scheduledTxn.hasCryptoCreateAccount()) {
            ordinary.setCryptoCreateAccount(scheduledTxn.getCryptoCreateAccount());
        } else if (scheduledTxn.hasCryptoDelete()) {
            ordinary.setCryptoDelete(scheduledTxn.getCryptoDelete());
        } else if (scheduledTxn.hasCryptoTransfer()) {
            ordinary.setCryptoTransfer(scheduledTxn.getCryptoTransfer());
        } else if (scheduledTxn.hasCryptoUpdateAccount()) {
            ordinary.setCryptoUpdateAccount(scheduledTxn.getCryptoUpdateAccount());
        } else if (scheduledTxn.hasFileAppend()) {
            ordinary.setFileAppend(scheduledTxn.getFileAppend());
        } else if (scheduledTxn.hasFileCreate()) {
            ordinary.setFileCreate(scheduledTxn.getFileCreate());
        } else if (scheduledTxn.hasFileDelete()) {
            ordinary.setFileDelete(scheduledTxn.getFileDelete());
        } else if (scheduledTxn.hasFileUpdate()) {
            ordinary.setFileUpdate(scheduledTxn.getFileUpdate());
        } else if (scheduledTxn.hasSystemDelete()) {
            ordinary.setSystemDelete(scheduledTxn.getSystemDelete());
        } else if (scheduledTxn.hasSystemUndelete()) {
            ordinary.setSystemUndelete(scheduledTxn.getSystemUndelete());
        } else if (scheduledTxn.hasFreeze()) {
            ordinary.setFreeze(scheduledTxn.getFreeze());
        } else if (scheduledTxn.hasConsensusCreateTopic()) {
            ordinary.setConsensusCreateTopic(scheduledTxn.getConsensusCreateTopic());
        } else if (scheduledTxn.hasConsensusUpdateTopic()) {
            ordinary.setConsensusUpdateTopic(scheduledTxn.getConsensusUpdateTopic());
        } else if (scheduledTxn.hasConsensusDeleteTopic()) {
            ordinary.setConsensusDeleteTopic(scheduledTxn.getConsensusDeleteTopic());
        } else if (scheduledTxn.hasConsensusSubmitMessage()) {
            ordinary.setConsensusSubmitMessage(scheduledTxn.getConsensusSubmitMessage());
        } else if (scheduledTxn.hasTokenCreation()) {
            ordinary.setTokenCreation(scheduledTxn.getTokenCreation());
        } else if (scheduledTxn.hasTokenFreeze()) {
            ordinary.setTokenFreeze(scheduledTxn.getTokenFreeze());
        } else if (scheduledTxn.hasTokenUnfreeze()) {
            ordinary.setTokenUnfreeze(scheduledTxn.getTokenUnfreeze());
        } else if (scheduledTxn.hasTokenGrantKyc()) {
            ordinary.setTokenGrantKyc(scheduledTxn.getTokenGrantKyc());
        } else if (scheduledTxn.hasTokenRevokeKyc()) {
            ordinary.setTokenRevokeKyc(scheduledTxn.getTokenRevokeKyc());
        } else if (scheduledTxn.hasTokenDeletion()) {
            ordinary.setTokenDeletion(scheduledTxn.getTokenDeletion());
        } else if (scheduledTxn.hasTokenUpdate()) {
            ordinary.setTokenUpdate(scheduledTxn.getTokenUpdate());
        } else if (scheduledTxn.hasTokenMint()) {
            ordinary.setTokenMint(scheduledTxn.getTokenMint());
        } else if (scheduledTxn.hasTokenBurn()) {
            ordinary.setTokenBurn(scheduledTxn.getTokenBurn());
        } else if (scheduledTxn.hasTokenWipe()) {
            ordinary.setTokenWipe(scheduledTxn.getTokenWipe());
        } else if (scheduledTxn.hasTokenAssociate()) {
            ordinary.setTokenAssociate(scheduledTxn.getTokenAssociate());
        } else if (scheduledTxn.hasTokenDissociate()) {
            ordinary.setTokenDissociate(scheduledTxn.getTokenDissociate());
        } else if (scheduledTxn.hasScheduleDelete()) {
            ordinary.setScheduleDelete(scheduledTxn.getScheduleDelete());
        } else if (scheduledTxn.hasTokenPause()) {
            ordinary.setTokenPause(scheduledTxn.getTokenPause());
        } else if (scheduledTxn.hasTokenUnpause()) {
            ordinary.setTokenUnpause(scheduledTxn.getTokenUnpause());
        } else if (scheduledTxn.hasUtilPrng()) {
            ordinary.setUtilPrng(scheduledTxn.getUtilPrng());
        } else if (scheduledTxn.hasCryptoApproveAllowance()) {
            ordinary.setCryptoApproveAllowance(scheduledTxn.getCryptoApproveAllowance());
        }
        return ordinary.build();
    }

    /**
     * @param functionality any {@link HederaFunctionality}
     * @return true if the functionality could possibly be allowed to be scheduled. Some
     *     functionally may not be in {@link SchedulableTransactionBody} yet but could be in the
     *     future. The scheduling.whitelist configuration property is separate from this and
     *     provides the final list of functionality that can be scheduled.
     */
    public static boolean isSchedulable(final HederaFunctionality functionality) {
        if (functionality == null) {
            return false;
        }
        return switch (functionality) {
            case ScheduleCreate, ScheduleSign -> false;
            default -> !QUERY_FUNCTIONS.contains(functionality);
        };
    }

    /**
     * A permutation (invertible function) on 64 bits. The constants were found by automated search,
     * to optimize avalanche. Avalanche means that for a random number x, flipping bit i of x has
     * about a 50 percent chance of flipping bit j of perm64(x). For each possible pair (i,j), this
     * function achieves a probability between 49.8 and 50.2 percent.
     *
     * @param x the value to permute
     * @return the avalanche-optimized permutation
     */
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

    public static void withLoggedDuration(final Runnable blockingTask, final Logger logger, final String desc) {
        logger.info("Starting {}", desc);
        final var watch = StopWatch.createStarted();
        blockingTask.run();
        logger.info("Done with {} in {}ms", desc, watch.getTime(TimeUnit.MILLISECONDS));
    }

    public static <K, V extends MerkleNode & Keyed<K>> void forEach(
            final MerkleMapLike<K, V> map, final BiConsumer<? super K, ? super V> action) {
        map.forEachNode(action);
    }

    public static void putIfNotNull(@Nullable final Map<String, Object> map, final String key, final Object value) {
        if (null != map) {
            map.put(key, value);
        }
    }

    /**
     * Verifies whether a {@link HederaFunctionality} should be throttled by the consensus throttle
     *
     * @param hederaFunctionality - the {@link HederaFunctionality} to verify
     * @return - whether this {@link HederaFunctionality} should be throttled by the consensus
     *     throttle
     */
    public static boolean isGasThrottled(final HederaFunctionality hederaFunctionality) {
        return CONSENSUS_THROTTLED_FUNCTIONS.contains(hederaFunctionality);
    }

    public static long getGasLimitForContractTx(
            final TransactionBody txn,
            final HederaFunctionality function,
            @Nullable final Supplier<EthTxData> getEthData) {
        return switch (function) {
            case ContractCreate -> txn.getContractCreateInstance().getGas();
            case ContractCall -> txn.getContractCall().getGas();
            case EthereumTransaction -> getEthData != null
                    ? getEthData.get().gasLimit()
                    : EthTxData.populateEthTxData(txn.getEthereumTransaction()
                                    .getEthereumData()
                                    .toByteArray())
                            .gasLimit();
            default -> 0L;
        };
    }

    /**
     * Attempts to parse a {@code Key} from given alias {@code ByteString}. If the Key is of type
     * Ed25519 or ECDSA(secp256k1), returns true if it is a valid key; and false otherwise.
     *
     * @param alias given alias byte string
     * @return whether it parses to a valid primitive key
     */
    public static boolean isSerializedProtoKey(final ByteString alias) {
        try {
            final var key = Key.parseFrom(alias);
            if (!key.getECDSASecp256K1().isEmpty()) {
                return JECDSASecp256k1Key.isValidProto(key);
            } else if (!key.getEd25519().isEmpty()) {
                return JEd25519Key.isValidProto(key);
            } else {
                return false;
            }
        } catch (final InvalidProtocolBufferException ignore) {
            return false;
        }
    }

    public static Transaction synthFromBody(final TransactionBody txnBody) {
        final var signedTxn = SignedTransaction.newBuilder()
                .setBodyBytes(txnBody.toByteString())
                .build();
        return Transaction.newBuilder()
                .setSignedTransactionBytes(signedTxn.toByteString())
                .build();
    }

    public static void safeResetThrottles(
            final List<DeterministicThrottle> throttles,
            final DeterministicThrottle.UsageSnapshot[] snapshots,
            final String source) {
        final var currUsageSnapshots =
                throttles.stream().map(DeterministicThrottle::usageSnapshot).toList();
        for (int i = 0, n = snapshots.length; i < n; i++) {
            final var savedUsageSnapshot = snapshots[i];
            final var throttle = throttles.get(i);
            try {
                throttle.resetUsageTo(savedUsageSnapshot);
                log.info("Reset {} with saved usage snapshot", throttle);
            } catch (final Exception e) {
                log.warn(
                        "Saved {} usage snapshot #{} was not compatible with the corresponding"
                                + " active throttle ({}) not performing a reset !",
                        source,
                        (i + 1),
                        e.getMessage());
                resetUnconditionally(throttles, currUsageSnapshots);
                break;
            }
        }
    }

    public static <T extends Enum<T>> List<T> csvList(final String propertyValue, final Function<String, T> parser) {
        return csvStream(propertyValue, parser).toList();
    }

    public static <T extends Enum<T>> Set<T> csvSet(
            final String propertyValue, final Function<String, T> parser, final Class<T> type) {
        return csvStream(propertyValue, parser).collect(Collectors.toCollection(() -> EnumSet.noneOf(type)));
    }

    public static <T> Stream<T> csvStream(final String propertyValue, final Function<String, T> parser) {
        return Arrays.stream(propertyValue.split(","))
                .map(String::strip)
                .filter(desc -> desc.length() > 0)
                .map(parser);
    }

    private static void resetUnconditionally(
            final List<DeterministicThrottle> throttles,
            final List<DeterministicThrottle.UsageSnapshot> knownCompatible) {
        for (int i = 0, n = knownCompatible.size(); i < n; i++) {
            throttles.get(i).resetUsageTo(knownCompatible.get(i));
        }
    }

    public static Transaction synthWithRecordTxnId(
            final TransactionBody.Builder txnBody, final ExpirableTxnRecord.Builder inProgress) {
        final var synthTxn = synthFromBody(
                txnBody.setTransactionID(inProgress.getTxnId().toGrpc()).build());
        final var synthHash = noThrowSha384HashOf(unwrapUnsafelyIfPossible(synthTxn.getSignedTransactionBytes()));
        inProgress.setTxnHash(synthHash);
        return synthTxn;
    }

    public static Key asPrimitiveKeyUnchecked(final ByteString alias) {
        try {
            return Key.parseFrom(alias);
        } catch (final InvalidProtocolBufferException internal) {
            throw new IllegalStateException(internal);
        }
    }

    public static boolean isRecoveredEvmAddress(final byte[] address) {
        return address != null && address.length == EVM_ADDRESS_LEN;
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
