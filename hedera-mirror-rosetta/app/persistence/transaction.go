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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools"
	types2 "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/types"
	log "github.com/sirupsen/logrus"
)

const (
	batchSize                   = 2000
	tableNameTransactionResults = "t_transaction_results"
	tableNameTransactionTypes   = "t_transaction_types"
	transactionResultSuccess    = 22
)

const (
	andTransactionHashFilter = " and transaction_hash = @hash"
	orderByConsensusNs       = " order by consensus_ns"
	selectTransactionResults = "select * from " + tableNameTransactionResults
	selectTransactionTypes   = "select * from " + tableNameTransactionTypes
	// selectTransactionsInTimestampRange selects the transactions with its crypto transfers in json, non-fee transfers
	// in json, token transfers in json, and optionally the token information when the transaction is token create,
	// token delete, or token update. Note the three token transactions are the ones the entity_id in the transaction
	// table is its related token id and require an extra rosetta operation
	selectTransactionsInTimestampRange = `select
                                            t.consensus_ns,
                                            t.payer_account_id,
                                            t.transaction_hash as hash,
                                            t.result,
                                            t.type,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                'account_id', entity_id,
                                                'amount', amount))
                                              from crypto_transfer where consensus_timestamp = t.consensus_ns
                                            ), '[]') as crypto_transfers,
                                            case
                                              when t.type = 14 then coalesce((
                                                  select json_agg(json_build_object(
                                                      'account_id', entity_id, 
                                                      'amount', amount
                                                    ))
                                                  from non_fee_transfer
                                                  where consensus_timestamp = t.consensus_ns
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
                                              where tkt.consensus_timestamp = t.consensus_ns
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
                                              where nftt.consensus_timestamp = t.consensus_ns and serial_number <> -1
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
                                          where consensus_ns >= @start and consensus_ns <= @end`
	selectTransactionsByHashInTimestampRange  = selectTransactionsInTimestampRange + andTransactionHashFilter
	selectTransactionsInTimestampRangeOrdered = selectTransactionsInTimestampRange + orderByConsensusNs
)

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

// TableName - Set table name of the Transaction Results to be `t_transaction_results`
func (transactionResult) TableName() string {
	return tableNameTransactionResults
}

// transaction maps to the transaction query which returns the required transaction fields, CryptoTransfers json string,
// NonFeeTransfers json string, TokenTransfers json string, and Token definition json string
type transaction struct {
	ConsensusNs     int64
	Hash            []byte
	PayerAccountId  int64
	Result          int16
	Type            int16
	CryptoTransfers string
	NftTransfers    string
	NonFeeTransfers string
	TokenTransfers  string
	Token           string
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
	dbClient *types2.DbClient
	results  map[int]string
	types    map[int]string
}

// NewTransactionRepository creates an instance of a TransactionRepository struct
func NewTransactionRepository(dbClient *types2.DbClient) interfaces.TransactionRepository {
	return &transactionRepository{dbClient: dbClient}
}

// Types returns map of all transaction types
func (tr *transactionRepository) Types(ctx context.Context) (map[int]string, *rTypes.Error) {
	if tr.types == nil {
		if err := tr.retrieveTransactionTypesAndResults(ctx); err != nil {
			return nil, err
		}
	}
	return tr.types, nil
}

// Results returns map of all transaction results
func (tr *transactionRepository) Results(ctx context.Context) (map[int]string, *rTypes.Error) {
	if tr.results == nil {
		if err := tr.retrieveTransactionTypesAndResults(ctx); err != nil {
			return nil, err
		}
	}
	return tr.results, nil
}

// TypesAsArray returns all Transaction type names as an array
func (tr *transactionRepository) TypesAsArray(ctx context.Context) ([]string, *rTypes.Error) {
	transactionTypes, err := tr.Types(ctx)
	if err != nil {
		return nil, err
	}
	return tools.GetStringValuesFromIntStringMap(transactionTypes), nil
}

// FindBetween retrieves all Transactions between the provided start and end timestamp
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

		start = transactionsBatch[len(transactionsBatch)-1].ConsensusNs + 1
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

// FindByHashInBlock retrieves a transaction by Hash
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

func (tr *transactionRepository) retrieveTransactionTypes(ctx context.Context) ([]transactionType, *rTypes.Error) {
	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	var transactionTypes []transactionType
	if err := db.Raw(selectTransactionTypes).Find(&transactionTypes).Error; err != nil {
		log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
		return nil, hErrors.ErrDatabaseError
	}
	return transactionTypes, nil
}

func (tr *transactionRepository) retrieveTransactionResults(ctx context.Context) ([]transactionResult, *rTypes.Error) {
	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	var tResults []transactionResult
	if err := db.Raw(selectTransactionResults).Find(&tResults).Error; err != nil {
		log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
		return nil, hErrors.ErrDatabaseError
	}
	return tResults, nil
}

func (tr *transactionRepository) constructTransaction(ctx context.Context, sameHashTransactions []*transaction) (
	*types.Transaction,
	*rTypes.Error,
) {
	if err := tr.retrieveTransactionTypesAndResults(ctx); err != nil {
		return nil, err
	}

	tResult := &types.Transaction{Hash: sameHashTransactions[0].getHashString()}
	operations := make([]*types.Operation, 0)
	success := tr.results[transactionResultSuccess]

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

		transactionResult := tr.results[int(transaction.Result)]
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

func (tr *transactionRepository) retrieveTransactionTypesAndResults(ctx context.Context) *rTypes.Error {
	typeArray, err := tr.retrieveTransactionTypes(ctx)
	if err != nil {
		return err
	}

	resultArray, err := tr.retrieveTransactionResults(ctx)
	if err != nil {
		return err
	}

	if len(typeArray) == 0 {
		log.Warn("No Transaction Types were found in the database.")
		return hErrors.ErrOperationTypesNotFound
	}

	if len(resultArray) == 0 {
		log.Warn("No Transaction Results were found in the database.")
		return hErrors.ErrOperationResultsNotFound
	}

	tr.once.Do(func() {
		tr.types = make(map[int]string)
		for _, t := range typeArray {
			tr.types[t.ProtoID] = t.Name
		}

		tr.results = make(map[int]string)
		for _, s := range resultArray {
			tr.results[s.ProtoID] = s.Result
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
