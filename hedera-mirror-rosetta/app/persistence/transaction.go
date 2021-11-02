/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

package persistence

import (
	"context"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"sync"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/tools"
	log "github.com/sirupsen/logrus"
)

const (
	batchSize                 = 2000
	tableNameTransactionTypes = "t_transaction_types"
	transactionResultSuccess  = 22
)

const (
	andTransactionHashFilter  = " and transaction_hash = @hash"
	orderByConsensusTimestamp = " order by consensus_timestamp"
	selectTransactionTypes    = "select * from " + tableNameTransactionTypes
	// selectTransactionsInTimestampRange selects the transactions with its crypto transfers in json, non-fee transfers
	// in json, token transfers in json, and optionally the token information when the transaction is token create,
	// token delete, or token update. Note the three token transactions are the ones the entity_id in the transaction
	// table is its related token id and require an extra rosetta operation
	selectTransactionsInTimestampRange = `select
                                            t.consensus_timestamp,
                                            t.payer_account_id,
                                            t.transaction_hash as hash,
                                            t.result,
                                            t.type,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                'account_id', entity_id,
                                                'amount', amount))
                                              from crypto_transfer where consensus_timestamp = t.consensus_timestamp
                                            ), '[]') as crypto_transfers,
                                            case
                                              when t.type = 14 then coalesce((
                                                  select json_agg(json_build_object(
                                                      'account_id', entity_id,
                                                      'amount', amount
                                                    ))
                                                  from non_fee_transfer
                                                  where consensus_timestamp = t.consensus_timestamp
                                                ), '[]')
                                              else '[]'
                                            end as non_fee_transfers,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                  'account_id', account_id,
                                                  'amount', amount,
                                                  'decimals', tk.decimals,
                                                  'token_id', tkt.token_id,
                                                  'type', tk.type
                                                ))
                                              from token_transfer tkt
                                              join token tk on tk.token_id = tkt.token_id
                                              where tkt.consensus_timestamp = t.consensus_timestamp
                                            ), '[]') as token_transfers,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                  'receiver_account_id', receiver_account_id,
                                                  'sender_account_id', sender_account_id,
                                                  'serial_number', serial_number,
                                                  'token_id', tk.token_id
                                                ))
                                              from nft_transfer nftt
                                              join token tk on tk.token_id = nftt.token_id
                                              where nftt.consensus_timestamp = t.consensus_timestamp and serial_number <> -1
                                            ), '[]') as nft_transfers,
                                            case
                                              when t.type in (29, 35, 36) then coalesce((
                                                  select json_build_object(
                                                    'decimals', decimals,
                                                    'freeze_default', freeze_default,
                                                    'initial_supply', initial_supply,
                                                    'token_id', token_id,
                                                    'type', type
                                                  )
                                                  from token
                                                  where token_id = t.entity_id
                                                ), '{}')
                                              else '{}'
                                            end as token
                                          from transaction t
                                          where consensus_timestamp >= @start and consensus_timestamp <= @end`
	selectTransactionsByHashInTimestampRange  = selectTransactionsInTimestampRange + andTransactionHashFilter
	selectTransactionsInTimestampRangeOrdered = selectTransactionsInTimestampRange + orderByConsensusTimestamp
)

var transactionResults = map[int]string{
	0:   "OK",
	1:   "INVALID_TRANSACTION",
	2:   "PAYER_ACCOUNT_NOT_FOUND",
	3:   "INVALID_NODE_ACCOUNT",
	4:   "TRANSACTION_EXPIRED",
	5:   "INVALID_TRANSACTION_START",
	6:   "INVALID_TRANSACTION_DURATION",
	7:   "INVALID_SIGNATURE",
	8:   "MEMO_TOO_LONG",
	9:   "INSUFFICIENT_TX_FEE",
	10:  "INSUFFICIENT_PAYER_BALANCE",
	11:  "DUPLICATE_TRANSACTION",
	12:  "BUSY",
	13:  "NOT_SUPPORTED",
	14:  "INVALID_FILE_ID",
	15:  "INVALID_ACCOUNT_ID",
	16:  "INVALID_CONTRACT_ID",
	17:  "INVALID_TRANSACTION_ID",
	18:  "RECEIPT_NOT_FOUND",
	19:  "RECORD_NOT_FOUND",
	20:  "INVALID_SOLIDITY_ID",
	21:  "UNKNOWN",
	22:  "SUCCESS",
	23:  "FAIL_INVALID",
	24:  "FAIL_FEE",
	25:  "FAIL_BALANCE",
	26:  "KEY_REQUIRED",
	27:  "BAD_ENCODING",
	28:  "INSUFFICIENT_ACCOUNT_BALANCE",
	29:  "INVALID_SOLIDITY_ADDRESS",
	30:  "INSUFFICIENT_GAS",
	31:  "CONTRACT_SIZE_LIMIT_EXCEEDED",
	32:  "LOCAL_CALL_MODIFICATION_EXCEPTION",
	33:  "CONTRACT_REVERT_EXECUTED",
	34:  "CONTRACT_EXECUTION_EXCEPTION",
	35:  "INVALID_RECEIVING_NODE_ACCOUNT",
	36:  "MISSING_QUERY_HEADER",
	37:  "ACCOUNT_UPDATE_FAILED",
	38:  "INVALID_KEY_ENCODING",
	39:  "NULL_SOLIDITY_ADDRESS",
	40:  "CONTRACT_UPDATE_FAILED",
	41:  "INVALID_QUERY_HEADER",
	42:  "INVALID_FEE_SUBMITTED",
	43:  "INVALID_PAYER_SIGNATURE",
	44:  "KEY_NOT_PROVIDED",
	45:  "INVALID_EXPIRATION_TIME",
	46:  "NO_WACL_KEY",
	47:  "FILE_CONTENT_EMPTY",
	48:  "INVALID_ACCOUNT_AMOUNTS",
	49:  "EMPTY_TRANSACTION_BODY",
	50:  "INVALID_TRANSACTION_BODY",
	51:  "INVALID_SIGNATURE_TYPE_MISMATCHING_KEY",
	52:  "INVALID_SIGNATURE_COUNT_MISMATCHING_KEY",
	53:  "EMPTY_LIVE_HASH_BODY",
	54:  "EMPTY_LIVE_HASH",
	55:  "EMPTY_LIVE_HASH_KEYS",
	56:  "INVALID_LIVE_HASH_SIZE",
	57:  "EMPTY_QUERY_BODY",
	58:  "EMPTY_LIVE_HASH_QUERY",
	59:  "LIVE_HASH_NOT_FOUND",
	60:  "ACCOUNT_ID_DOES_NOT_EXIST",
	61:  "LIVE_HASH_ALREADY_EXISTS",
	62:  "INVALID_FILE_WACL",
	63:  "SERIALIZATION_FAILED",
	64:  "TRANSACTION_OVERSIZE",
	65:  "TRANSACTION_TOO_MANY_LAYERS",
	66:  "CONTRACT_DELETED",
	67:  "PLATFORM_NOT_ACTIVE",
	68:  "KEY_PREFIX_MISMATCH",
	69:  "PLATFORM_TRANSACTION_NOT_CREATED",
	70:  "INVALID_RENEWAL_PERIOD",
	71:  "INVALID_PAYER_ACCOUNT_ID",
	72:  "ACCOUNT_DELETED",
	73:  "FILE_DELETED",
	74:  "ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS",
	75:  "SETTING_NEGATIVE_ACCOUNT_BALANCE",
	76:  "OBTAINER_REQUIRED",
	77:  "OBTAINER_SAME_CONTRACT_ID",
	78:  "OBTAINER_DOES_NOT_EXIST",
	79:  "MODIFYING_IMMUTABLE_CONTRACT",
	80:  "FILE_SYSTEM_EXCEPTION",
	81:  "AUTORENEW_DURATION_NOT_IN_RANGE",
	82:  "ERROR_DECODING_BYTESTRING",
	83:  "CONTRACT_FILE_EMPTY",
	84:  "CONTRACT_BYTECODE_EMPTY",
	85:  "INVALID_INITIAL_BALANCE",
	86:  "INVALID_RECEIVE_RECORD_THRESHOLD",
	87:  "INVALID_SEND_RECORD_THRESHOLD",
	88:  "ACCOUNT_IS_NOT_GENESIS_ACCOUNT",
	89:  "PAYER_ACCOUNT_UNAUTHORIZED",
	90:  "INVALID_FREEZE_TRANSACTION_BODY",
	91:  "FREEZE_TRANSACTION_BODY_NOT_FOUND",
	92:  "TRANSFER_LIST_SIZE_LIMIT_EXCEEDED",
	93:  "RESULT_SIZE_LIMIT_EXCEEDED",
	94:  "NOT_SPECIAL_ACCOUNT",
	95:  "CONTRACT_NEGATIVE_GAS",
	96:  "CONTRACT_NEGATIVE_VALUE",
	97:  "INVALID_FEE_FILE",
	98:  "INVALID_EXCHANGE_RATE_FILE",
	99:  "INSUFFICIENT_LOCAL_CALL_GAS",
	100: "ENTITY_NOT_ALLOWED_TO_DELETE",
	101: "AUTHORIZATION_FAILED",
	102: "FILE_UPLOADED_PROTO_INVALID",
	103: "FILE_UPLOADED_PROTO_NOT_SAVED_TO_DISK",
	104: "FEE_SCHEDULE_FILE_PART_UPLOADED",
	105: "EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED",
	106: "MAX_CONTRACT_STORAGE_EXCEEDED",
	107: "TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT",
	108: "TOTAL_LEDGER_BALANCE_INVALID",
	110: "EXPIRATION_REDUCTION_NOT_ALLOWED",
	111: "MAX_GAS_LIMIT_EXCEEDED",
	112: "MAX_FILE_SIZE_EXCEEDED",
	113: "RECEIVER_SIG_REQUIRED",
	150: "INVALID_TOPIC_ID",
	155: "INVALID_ADMIN_KEY",
	156: "INVALID_SUBMIT_KEY",
	157: "UNAUTHORIZED",
	158: "INVALID_TOPIC_MESSAGE",
	159: "INVALID_AUTORENEW_ACCOUNT",
	160: "AUTORENEW_ACCOUNT_NOT_ALLOWED",
	162: "TOPIC_EXPIRED",
	163: "INVALID_CHUNK_NUMBER",
	164: "INVALID_CHUNK_TRANSACTION_ID",
	165: "ACCOUNT_FROZEN_FOR_TOKEN",
	166: "TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED",
	167: "INVALID_TOKEN_ID",
	168: "INVALID_TOKEN_DECIMALS",
	169: "INVALID_TOKEN_INITIAL_SUPPLY",
	170: "INVALID_TREASURY_ACCOUNT_FOR_TOKEN",
	171: "INVALID_TOKEN_SYMBOL",
	172: "TOKEN_HAS_NO_FREEZE_KEY",
	173: "TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN",
	174: "MISSING_TOKEN_SYMBOL",
	175: "TOKEN_SYMBOL_TOO_LONG",
	176: "ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN",
	177: "TOKEN_HAS_NO_KYC_KEY",
	178: "INSUFFICIENT_TOKEN_BALANCE",
	179: "TOKEN_WAS_DELETED",
	180: "TOKEN_HAS_NO_SUPPLY_KEY",
	181: "TOKEN_HAS_NO_WIPE_KEY",
	182: "INVALID_TOKEN_MINT_AMOUNT",
	183: "INVALID_TOKEN_BURN_AMOUNT",
	184: "TOKEN_NOT_ASSOCIATED_TO_ACCOUNT",
	185: "CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT",
	186: "INVALID_KYC_KEY",
	187: "INVALID_WIPE_KEY",
	188: "INVALID_FREEZE_KEY",
	189: "INVALID_SUPPLY_KEY",
	190: "MISSING_TOKEN_NAME",
	191: "TOKEN_NAME_TOO_LONG",
	192: "INVALID_WIPING_AMOUNT",
	193: "TOKEN_IS_IMMUTABLE",
	194: "TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT",
	195: "TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES",
	196: "ACCOUNT_IS_TREASURY",
	197: "TOKEN_ID_REPEATED_IN_TOKEN_LIST",
	198: "TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED",
	199: "EMPTY_TOKEN_TRANSFER_BODY",
	200: "EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS",
	201: "INVALID_SCHEDULE_ID",
	202: "SCHEDULE_IS_IMMUTABLE",
	205: "NO_NEW_VALID_SIGNATURES",
	204: "INVALID_SCHEDULE_ACCOUNT_ID",
	203: "INVALID_SCHEDULE_PAYER_ID",
	206: "UNRESOLVABLE_REQUIRED_SIGNERS",
	207: "SCHEDULED_TRANSACTION_NOT_IN_WHITELIST",
	208: "SOME_SIGNATURES_WERE_INVALID",
	209: "TRANSACTION_ID_FIELD_NOT_ALLOWED",
	210: "IDENTICAL_SCHEDULE_ALREADY_CREATED",
	211: "INVALID_ZERO_BYTE_IN_STRING",
	212: "SCHEDULE_ALREADY_DELETED",
	213: "SCHEDULE_ALREADY_EXECUTED",
	214: "MESSAGE_SIZE_TOO_LARGE",
	215: "OPERATION_REPEATED_IN_BUCKET_GROUPS",
	216: "BUCKET_CAPACITY_OVERFLOW",
	217: "NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION",
	218: "BUCKET_HAS_NO_THROTTLE_GROUPS",
	219: "THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC",
	220: "SUCCESS_BUT_MISSING_EXPECTED_OPERATION",
	221: "UNPARSEABLE_THROTTLE_DEFINITIONS",
	222: "INVALID_THROTTLE_DEFINITIONS",
	223: "ACCOUNT_EXPIRED_AND_PENDING_REMOVAL",
	224: "INVALID_TOKEN_MAX_SUPPLY",
	225: "INVALID_TOKEN_NFT_SERIAL_NUMBER",
	226: "INVALID_NFT_ID",
	227: "METADATA_TOO_LONG",
	228: "BATCH_SIZE_LIMIT_EXCEEDED",
	229: "INVALID_QUERY_RANGE",
	230: "FRACTION_DIVIDES_BY_ZERO",
	231: "INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE",
	232: "CUSTOM_FEES_LIST_TOO_LONG",
	233: "INVALID_CUSTOM_FEE_COLLECTOR",
	234: "INVALID_TOKEN_ID_IN_CUSTOM_FEES",
	235: "TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR",
	236: "TOKEN_MAX_SUPPLY_REACHED",
	237: "SENDER_DOES_NOT_OWN_NFT_SERIAL_NO",
	238: "CUSTOM_FEE_NOT_FULLY_SPECIFIED",
	239: "CUSTOM_FEE_MUST_BE_POSITIVE",
	240: "TOKEN_HAS_NO_FEE_SCHEDULE_KEY",
	241: "CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE",
	242: "ROYALTY_FRACTION_CANNOT_EXCEED_ONE",
	243: "FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT",
	244: "CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES",
	245: "CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON",
	246: "CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON",
	247: "INVALID_CUSTOM_FEE_SCHEDULE_KEY",
	248: "INVALID_TOKEN_MINT_METADATA",
	249: "INVALID_TOKEN_BURN_METADATA",
	250: "CURRENT_TREASURY_STILL_OWNS_NFTS",
	251: "ACCOUNT_STILL_OWNS_NFTS",
	252: "TREASURY_MUST_OWN_BURNED_NFT",
	253: "ACCOUNT_DOES_NOT_OWN_WIPED_NFT",
	254: "ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON",
	255: "MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED",
	256: "PAYER_ACCOUNT_DELETED",
	257: "CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH",
	258: "CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS",
	259: "INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE",
	260: "SERIAL_NUMBER_LIMIT_REACHED",
	261: "CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE",
	262: "NO_REMAINING_AUTOMATIC_ASSOCIATIONS",
	263: "EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT",
	264: "REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT",
	265: "TOKEN_IS_PAUSED",
	266: "TOKEN_HAS_NO_PAUSE_KEY",
	267: "INVALID_PAUSE_KEY",
	268: "FREEZE_UPDATE_FILE_DOES_NOT_EXIST",
	269: "FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH",
	270: "NO_UPGRADE_HAS_BEEN_PREPARED",
	271: "NO_FREEZE_IS_SCHEDULED",
	272: "UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE",
	273: "FREEZE_START_TIME_MUST_BE_FUTURE",
	274: "PREPARED_UPDATE_FILE_IS_IMMUTABLE",
	275: "FREEZE_ALREADY_SCHEDULED",
	276: "FREEZE_UPGRADE_IN_PROGRESS",
	277: "UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED",
	278: "UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED",
}

type transactionType struct {
	ProtoID int    `gorm:"type:integer;primaryKey"`
	Name    string `gorm:"size:30"`
}

type transactionResult struct {
	ProtoID int    `gorm:"type:integer;primaryKey"`
	Result  string `gorm:"size:100"`
}

// TableName - Set table name of the Transaction Types to be `t_transaction_types`
func (transactionType) TableName() string {
	return tableNameTransactionTypes
}

// transaction maps to the transaction query which returns the required transaction fields, CryptoTransfers json string,
// NonFeeTransfers json string, TokenTransfers json string, and Token definition json string
type transaction struct {
	ConsensusTimestamp int64
	Hash               []byte
	PayerAccountId     int64
	Result             int16
	Type               int16
	CryptoTransfers    string
	NftTransfers       string
	NonFeeTransfers    string
	TokenTransfers     string
	Token              string
}

func (t transaction) getHashString() string {
	return tools.SafeAddHexPrefix(hex.EncodeToString(t.Hash))
}

type transfer interface {
	getAccountId() domain.EntityId
	getAmount() types.Amount
}

type hbarTransfer struct {
	AccountId domain.EntityId `json:"account_id"`
	Amount    int64           `json:"amount"`
}

func (t hbarTransfer) getAccountId() domain.EntityId {
	return t.AccountId
}

func (t hbarTransfer) getAmount() types.Amount {
	return &types.HbarAmount{Value: t.Amount}
}

type singleNftTransfer struct {
	accountId    domain.EntityId
	receiver     bool
	serialNumber int64
	tokenId      domain.EntityId
}

func (n singleNftTransfer) getAccountId() domain.EntityId {
	return n.accountId
}

func (n singleNftTransfer) getAmount() types.Amount {
	amount := int64(1)
	if !n.receiver {
		amount = -1
	}

	return &types.TokenAmount{
		SerialNumbers: []int64{n.serialNumber},
		TokenId:       n.tokenId,
		Type:          domain.TokenTypeNonFungibleUnique,
		Value:         amount,
	}
}

type tokenTransfer struct {
	AccountId domain.EntityId `json:"account_id"`
	Amount    int64           `json:"amount"`
	Decimals  int64           `json:"decimals"`
	TokenId   domain.EntityId `json:"token_id"`
	Type      string          `json:"type"`
}

func (t tokenTransfer) getAccountId() domain.EntityId {
	return t.AccountId
}

func (t tokenTransfer) getAmount() types.Amount {
	return &types.TokenAmount{
		Decimals: t.Decimals,
		TokenId:  t.TokenId,
		Type:     t.Type,
		Value:    t.Amount,
	}
}

// transactionRepository struct that has connection to the Database
type transactionRepository struct {
	once     sync.Once
	dbClient interfaces.DbClient
	types    map[int]string
}

// NewTransactionRepository creates an instance of a TransactionRepository struct
func NewTransactionRepository(dbClient interfaces.DbClient) interfaces.TransactionRepository {
	return &transactionRepository{dbClient: dbClient}
}

func (tr *transactionRepository) FindBetween(ctx context.Context, start, end int64) (
		[]*types.Transaction,
		*rTypes.Error,
) {
	if start > end {
		return nil, hErrors.ErrStartMustNotBeAfterEnd
	}

	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	transactions := make([]*transaction, 0)
	for start <= end {
		transactionsBatch := make([]*transaction, 0)
		err := db.
			Raw(selectTransactionsInTimestampRangeOrdered, sql.Named("start", start), sql.Named("end", end)).
			Limit(batchSize).
			Find(&transactionsBatch).
			Error
		if err != nil {
			log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
			return nil, hErrors.ErrDatabaseError
		}

		transactions = append(transactions, transactionsBatch...)

		if len(transactionsBatch) < batchSize {
			break
		}

		start = transactionsBatch[len(transactionsBatch)-1].ConsensusTimestamp + 1
	}

	hashes := make([]string, 0)
	sameHashMap := make(map[string][]*transaction)
	for _, t := range transactions {
		h := t.getHashString()
		if _, ok := sameHashMap[h]; !ok {
			// save the unique hashes in chronological order
			hashes = append(hashes, h)
		}

		sameHashMap[h] = append(sameHashMap[h], t)
	}

	res := make([]*types.Transaction, 0, len(sameHashMap))
	for _, hash := range hashes {
		sameHashTransactions := sameHashMap[hash]
		transaction, err := tr.constructTransaction(ctx, sameHashTransactions)
		if err != nil {
			return nil, err
		}
		res = append(res, transaction)
	}
	return res, nil
}

func (tr *transactionRepository) FindByHashInBlock(
		ctx context.Context,
		hashStr string,
		consensusStart int64,
		consensusEnd int64,
) (*types.Transaction, *rTypes.Error) {
	var transactions []*transaction
	transactionHash, err := hex.DecodeString(tools.SafeRemoveHexPrefix(hashStr))
	if err != nil {
		return nil, hErrors.ErrInvalidTransactionIdentifier
	}

	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	if err = db.Raw(
		selectTransactionsByHashInTimestampRange,
		sql.Named("hash", transactionHash),
		sql.Named("start", consensusStart),
		sql.Named("end", consensusEnd),
	).Find(&transactions).Error; err != nil {
		log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
		return nil, hErrors.ErrDatabaseError
	}

	if len(transactions) == 0 {
		return nil, hErrors.ErrTransactionNotFound
	}

	transaction, rErr := tr.constructTransaction(ctx, transactions)
	if rErr != nil {
		return nil, rErr
	}

	return transaction, nil
}

func (tr *transactionRepository) Types(ctx context.Context) (map[int]string, *rTypes.Error) {
	if tr.types == nil {
		if err := tr.retrieveTransactionTypes(ctx); err != nil {
			return nil, err
		}
	}
	return tr.types, nil
}

func (tr *transactionRepository) TypesAsArray(ctx context.Context) ([]string, *rTypes.Error) {
	transactionTypes, err := tr.Types(ctx)
	if err != nil {
		return nil, err
	}
	return tools.GetStringValuesFromIntStringMap(transactionTypes), nil
}

func (tr *transactionRepository) constructTransaction(ctx context.Context, sameHashTransactions []*transaction) (
		*types.Transaction,
		*rTypes.Error,
) {
	if err := tr.retrieveTransactionTypes(ctx); err != nil {
		return nil, err
	}

	tResult := &types.Transaction{Hash: sameHashTransactions[0].getHashString()}
	operations := make([]*types.Operation, 0)
	success := transactionResults[transactionResultSuccess]

	for _, transaction := range sameHashTransactions {
		cryptoTransfers := make([]hbarTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.CryptoTransfers), &cryptoTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		nonFeeTransfers := make([]hbarTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.NonFeeTransfers), &nonFeeTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		tokenTransfers := make([]tokenTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.TokenTransfers), &tokenTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		nftTransfers := make([]domain.NftTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.NftTransfers), &nftTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		token := domain.Token{}
		if err := json.Unmarshal([]byte(transaction.Token), &token); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		transactionResult := transactionResults[int(transaction.Result)]
		transactionType := tr.types[int(transaction.Type)]

		nonFeeTransferMap := aggregateNonFeeTransfers(nonFeeTransfers)
		adjustedCryptoTransfers := adjustCryptoTransfers(cryptoTransfers, nonFeeTransferMap)

		operations = tr.appendHbarTransferOperations(transactionResult, transactionType, nonFeeTransfers, operations)
		// crypto transfers are always successful regardless of the transaction result
		operations = tr.appendHbarTransferOperations(success, transactionType, adjustedCryptoTransfers, operations)
		operations = tr.appendTokenTransferOperations(transactionResult, transactionType, tokenTransfers, operations)
		operations = tr.appendNftTransferOperations(transactionResult, transactionType, nftTransfers, operations)

		if !token.TokenId.IsZero() {
			// only for TokenCreate, TokenDeletion, and TokenUpdate, TokenId is non-zero
			operation, err := getTokenOperation(len(operations), token, transaction, transactionResult, transactionType)
			if err != nil {
				return nil, err
			}
			operations = append(operations, operation)
		}
	}

	tResult.Operations = operations
	return tResult, nil
}

func (tr *transactionRepository) appendHbarTransferOperations(
		transactionResult string,
		transactionType string,
		hbarTransfers []hbarTransfer,
		operations []*types.Operation,
) []*types.Operation {
	transfers := make([]transfer, 0, len(hbarTransfers))
	for _, hbarTransfer := range hbarTransfers {
		transfers = append(transfers, hbarTransfer)
	}

	return tr.appendTransferOperations(transactionResult, transactionType, transfers, operations)
}

func (tr *transactionRepository) appendNftTransferOperations(
		transactionResult string,
		transactionType string,
		nftTransfers []domain.NftTransfer,
		operations []*types.Operation,
) []*types.Operation {
	transfers := make([]transfer, 0, 2*len(nftTransfers))
	for _, nftTransfer := range nftTransfers {
		transfers = append(transfers, getSingleNftTransfers(nftTransfer)...)
	}

	return tr.appendTransferOperations(transactionResult, transactionType, transfers, operations)
}

func (tr *transactionRepository) appendTokenTransferOperations(
		transactionResult string,
		transactionType string,
		tokenTransfers []tokenTransfer,
		operations []*types.Operation,
) []*types.Operation {
	transfers := make([]transfer, 0, len(tokenTransfers))
	for _, tokenTransfer := range tokenTransfers {
		// The wiped amount of a deleted NFT class by a TokenDissociate is presented as tokenTransferList and
		// saved to token_transfer table, filter it
		if tokenTransfer.Type != domain.TokenTypeFungibleCommon {
			continue
		}

		transfers = append(transfers, tokenTransfer)
	}

	return tr.appendTransferOperations(transactionResult, transactionType, transfers, operations)
}

func (tr *transactionRepository) appendTransferOperations(
		transactionResult string,
		transactionType string,
		transfers []transfer,
		operations []*types.Operation,
) []*types.Operation {
	for _, transfer := range transfers {
		operations = append(operations, &types.Operation{
			Index:   int64(len(operations)),
			Type:    transactionType,
			Status:  transactionResult,
			Account: types.Account{EntityId: transfer.getAccountId()},
			Amount:  transfer.getAmount(),
		})
	}
	return operations
}

func (tr *transactionRepository) retrieveTransactionTypes(ctx context.Context) *rTypes.Error {
	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	var typeArray []transactionType
	if err := db.Raw(selectTransactionTypes).Find(&typeArray).Error; err != nil {
		log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
		return hErrors.ErrDatabaseError
	}

	if len(typeArray) == 0 {
		log.Warn("No Transaction Types were found in the database.")
		return hErrors.ErrOperationTypesNotFound
	}

	tr.once.Do(func() {
		tr.types = make(map[int]string)
		for _, t := range typeArray {
			tr.types[t.ProtoID] = t.Name
		}
	})

	return nil
}

func IsTransactionResultSuccessful(result int) bool {
	return result == transactionResultSuccess
}

func constructAccount(encodedId int64) (types.Account, *rTypes.Error) {
	account, err := types.NewAccountFromEncodedID(encodedId)
	if err != nil {
		log.Errorf(hErrors.CreateAccountDbIdFailed, encodedId)
		return types.Account{}, hErrors.ErrInternalServerError
	}
	return account, nil
}

func adjustCryptoTransfers(
		cryptoTransfers []hbarTransfer,
		nonFeeTransferMap map[int64]int64,
) []hbarTransfer {
	cryptoTransferMap := make(map[int64]hbarTransfer)
	for _, transfer := range cryptoTransfers {
		key := transfer.AccountId.EncodedId
		cryptoTransferMap[key] = hbarTransfer{
			AccountId: transfer.AccountId,
			Amount:    transfer.Amount + cryptoTransferMap[key].Amount,
		}
	}

	adjusted := make([]hbarTransfer, 0, len(cryptoTransfers))
	for key, aggregated := range cryptoTransferMap {
		amount := aggregated.Amount - nonFeeTransferMap[key]
		if amount != 0 {
			adjusted = append(adjusted, hbarTransfer{
				AccountId: aggregated.AccountId,
				Amount:    amount,
			})
		}
	}

	return adjusted
}

func aggregateNonFeeTransfers(nonFeeTransfers []hbarTransfer) map[int64]int64 {
	nonFeeTransferMap := make(map[int64]int64)

	// the original transfer list from the transaction body
	for _, transfer := range nonFeeTransfers {
		// the original transfer list may have multiple entries for one entity, so accumulate it
		nonFeeTransferMap[transfer.AccountId.EncodedId] += transfer.Amount
	}

	return nonFeeTransferMap
}

func getSingleNftTransfers(nftTransfer domain.NftTransfer) []transfer {
	transfers := make([]transfer, 0)
	if nftTransfer.ReceiverAccountId != nil {
		transfers = append(transfers, singleNftTransfer{
			accountId:    *nftTransfer.ReceiverAccountId,
			receiver:     true,
			serialNumber: nftTransfer.SerialNumber,
			tokenId:      nftTransfer.TokenId,
		})
	}

	if nftTransfer.SenderAccountId != nil {
		transfers = append(transfers, singleNftTransfer{
			accountId:    *nftTransfer.SenderAccountId,
			serialNumber: nftTransfer.SerialNumber,
			tokenId:      nftTransfer.TokenId,
		})
	}
	return transfers
}

func getTokenOperation(
		index int,
		token domain.Token,
		transaction *transaction,
		transactionResult string,
		transactionType string,
) (*types.Operation, *rTypes.Error) {
	payerId, err := constructAccount(transaction.PayerAccountId)
	if err != nil {
		return nil, err
	}

	operation := &types.Operation{
		Index:   int64(index),
		Type:    transactionType,
		Status:  transactionResult,
		Account: payerId,
	}

	metadata := make(map[string]interface{})
	metadata["currency"] = types.Token{Token: token}.ToRosettaCurrency()
	metadata["freeze_default"] = token.FreezeDefault
	metadata["initial_supply"] = token.InitialSupply
	operation.Metadata = metadata

	return operation, nil
}
