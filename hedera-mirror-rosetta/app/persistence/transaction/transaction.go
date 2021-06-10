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

package transaction

import (
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"sync"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
	entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	dbTypes "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/types"
	hexUtils "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/maphelper"
	log "github.com/sirupsen/logrus"
	"gorm.io/gorm"
)

const (
	tableNameTransactionResults = "t_transaction_results"
	tableNameTransactionTypes   = "t_transaction_types"
	transactionResultSuccess    = 22
)

const (
	whereClauseBetweenConsensus string = `SELECT * FROM transaction
                                          WHERE consensus_ns >= @start AND consensus_ns <= @end`
	whereCryptoTransferConsensusTimestampInTimestampsAsc string = `SELECT * FROM crypto_transfer
                                                                   WHERE consensus_timestamp IN @timestamps
                                                                   ORDER BY consensus_timestamp`
	whereNonFeeTransferConsensusTimestampInTimestampsAsc string = `SELECT * FROM non_fee_transfer
                                                                   WHERE consensus_timestamp IN @timestamps
                                                                   ORDER BY consensus_timestamp`
	/* #nosec */
	whereTokenTransferConsensusTimestampInTimestampsAsc string = `SELECT tt.*, t.decimals FROM token_transfer tt
                                                                  JOIN token t on t.token_id = tt.token_id
                                                                  WHERE tt.consensus_timestamp IN @timestamps
                                                                  ORDER BY tt.consensus_timestamp`
	whereTransactionsByHashAndConsensusTimestamps string = `SELECT * FROM transaction
                                                            WHERE transaction_hash = @hash
                                                            AND consensus_ns BETWEEN @start AND @end`
	selectToken string = `SELECT
		                    t.*,
		                    e.auto_renew_account_id,
		                    e.auto_renew_period,
		                    e.expiration_timestamp,
		                    e.memo,
		                    e.public_key
		                  FROM token t
		                  JOIN entity e ON e.id = t.token_id
		                  WHERE t.token_id = @token_id`
	selectTransactionResults string = "SELECT * FROM t_transaction_results"
	selectTransactionTypes   string = "SELECT * FROM t_transaction_types"
)

type tokenTransfer struct {
	AccountId          int64
	Amount             int64
	Decimals           int64
	TokenId            int64
	ConsensusTimestamp int64
}

func (t tokenTransfer) getTokenAmount() (*types.TokenAmount, error) {
	token, err := entityid.Decode(t.TokenId)
	if err != nil {
		return nil, err
	}

	return &types.TokenAmount{
		TokenId:  token,
		Decimals: t.Decimals,
		Value:    t.Amount,
	}, nil
}

type token struct {
	TokenId             int64
	AdminKey            string `gorm:"public_key"`
	AutoRenewAccountId  int64
	AutoRenewPeriod     int64
	ExpirationTimestamp int64
	Decimals            int64
	FreezeDefault       bool
	FreezeKeyEd25519Hex string
	InitialSupply       int64
	KycKeyEd25519Hex    string
	Name                string
	Memo                string
	SupplyKeyEd25519Hex string
	Symbol              string
	TotalSupply         int64
	Treasury            int64
	WipeKeyEd25519Hex   string
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

// TableName - Set table name of the Transaction Results to be `t_transaction_results`
func (transactionResult) TableName() string {
	return tableNameTransactionResults
}

type aggregatedCryptoTransfer struct {
	amount    int64
	entityId  int64
	timestamp int64
}

// TransactionRepository struct that has connection to the Database
type transactionRepository struct {
	once     sync.Once
	dbClient *gorm.DB
	results  map[int]string
	types    map[int]string
}

// NewTransactionRepository creates an instance of a TransactionRepository struct
func NewTransactionRepository(dbClient *gorm.DB) repositories.TransactionRepository {
	return &transactionRepository{dbClient: dbClient}
}

// Types returns map of all transaction types
func (tr *transactionRepository) Types() (map[int]string, *rTypes.Error) {
	if tr.types == nil {
		err := tr.retrieveTransactionTypesAndResults()
		if err != nil {
			return nil, err
		}
	}
	return tr.types, nil
}

// Results returns map of all transaction results
func (tr *transactionRepository) Results() (map[int]string, *rTypes.Error) {
	if tr.results == nil {
		err := tr.retrieveTransactionTypesAndResults()
		if err != nil {
			return nil, err
		}
	}
	return tr.results, nil
}

// TypesAsArray returns all Transaction type names as an array
func (tr *transactionRepository) TypesAsArray() ([]string, *rTypes.Error) {
	transactionTypes, err := tr.Types()
	if err != nil {
		return nil, err
	}
	return maphelper.GetStringValuesFromIntStringMap(transactionTypes), nil
}

// FindBetween retrieves all Transactions between the provided start and end timestamp
func (tr *transactionRepository) FindBetween(start int64, end int64) ([]*types.Transaction, *rTypes.Error) {
	if start > end {
		return nil, hErrors.ErrStartMustNotBeAfterEnd
	}
	var transactions []dbTypes.Transaction
	tr.dbClient.
		Raw(whereClauseBetweenConsensus, sql.Named("start", start), sql.Named("end", end)).
		Find(&transactions)

	sameHashMap := make(map[string][]dbTypes.Transaction)
	for _, t := range transactions {
		h := t.GetHashString()
		sameHashMap[h] = append(sameHashMap[h], t)
	}
	res := make([]*types.Transaction, 0, len(sameHashMap))
	for _, sameHashTransactions := range sameHashMap {
		transaction, err := tr.constructTransaction(sameHashTransactions)
		if err != nil {
			return nil, err
		}
		res = append(res, transaction)
	}
	return res, nil
}

// FindByHashInBlock retrieves a transaction by Hash
func (tr *transactionRepository) FindByHashInBlock(
	hashStr string,
	consensusStart int64,
	consensusEnd int64,
) (*types.Transaction, *rTypes.Error) {
	var transactions []dbTypes.Transaction
	transactionHash, err := hex.DecodeString(hexUtils.SafeRemoveHexPrefix(hashStr))
	if err != nil {
		return nil, hErrors.ErrInvalidTransactionIdentifier
	}
	tr.dbClient.
		Raw(
			whereTransactionsByHashAndConsensusTimestamps,
			sql.Named("hash", transactionHash),
			sql.Named("start", consensusStart),
			sql.Named("end", consensusEnd),
		).
		Find(&transactions)

	if len(transactions) == 0 {
		return nil, hErrors.ErrTransactionNotFound
	}

	transaction, err1 := tr.constructTransaction(transactions)
	if err1 != nil {
		return nil, err1
	}
	return transaction, nil
}

func (tr *transactionRepository) findCryptoTransfersAsc(timestamps []int64) []dbTypes.CryptoTransfer {
	var cryptoTransfers []dbTypes.CryptoTransfer
	tr.findTransfersAsc(whereCryptoTransferConsensusTimestampInTimestampsAsc, timestamps, &cryptoTransfers)
	return cryptoTransfers
}

func (tr *transactionRepository) findNonFeeTransfersAsc(timestamps []int64) []dbTypes.CryptoTransfer {
	var nonFeeTransfers []dbTypes.CryptoTransfer
	tr.findTransfersAsc(whereNonFeeTransferConsensusTimestampInTimestampsAsc, timestamps, &nonFeeTransfers)
	return nonFeeTransfers
}

func (tr *transactionRepository) findTokenTransfersAsc(timestamps []int64) []tokenTransfer {
	var tokenTransfers []tokenTransfer
	tr.findTransfersAsc(whereTokenTransferConsensusTimestampInTimestampsAsc, timestamps, &tokenTransfers)
	return tokenTransfers
}

func (tr *transactionRepository) findTransfersAsc(query string, timestamps []int64, out interface{}) {
	// timestampsStr := intsToString(timestamps)
	tr.dbClient.Raw(query, sql.Named("timestamps", timestamps)).Find(out)
}

func (tr *transactionRepository) findToken(transaction dbTypes.Transaction) (*token, *rTypes.Error) {
	if !transaction.HasTokenOperation() {
		return nil, nil
	}

	token := &token{}
	err := tr.dbClient.Raw(selectToken, sql.Named("token_id", transaction.EntityId)).First(token).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	} else if err != nil {
		return nil, hErrors.ErrDatabaseError
	}

	return token, nil
}

func (tr *transactionRepository) retrieveTransactionTypes() []transactionType {
	var transactionTypes []transactionType
	tr.dbClient.Raw(selectTransactionTypes).Find(&transactionTypes)
	return transactionTypes
}

func (tr *transactionRepository) retrieveTransactionResults() []transactionResult {
	var tResults []transactionResult
	tr.dbClient.Raw(selectTransactionResults).Find(&tResults)
	return tResults
}

func (tr *transactionRepository) constructTransaction(sameHashTransactions []dbTypes.Transaction) (
	*types.Transaction,
	*rTypes.Error,
) {
	tResult := &types.Transaction{Hash: sameHashTransactions[0].GetHashString()}

	transactionsMap := make(map[int64]dbTypes.Transaction)
	timestamps := make([]int64, len(sameHashTransactions))
	for i, t := range sameHashTransactions {
		transactionsMap[t.ConsensusNs] = t
		timestamps[i] = t.ConsensusNs
	}
	cryptoTransfers := tr.findCryptoTransfersAsc(timestamps)
	nonFeeTransfers := tr.findNonFeeTransfersAsc(timestamps)
	tokenTransfers := tr.findTokenTransfersAsc(timestamps)
	token, err := tr.findToken(sameHashTransactions[0])
	if err != nil {
		return nil, err
	}

	tResult.Operations, err = tr.constructOperations(
		cryptoTransfers,
		nonFeeTransfers,
		tokenTransfers,
		transactionsMap,
		token,
	)
	if err != nil {
		return nil, err
	}

	return tResult, nil
}

func (tr *transactionRepository) constructOperations(
	cryptoTransfers []dbTypes.CryptoTransfer,
	nonFeeTransfers []dbTypes.CryptoTransfer,
	tokenTransfers []tokenTransfer,
	transactions map[int64]dbTypes.Transaction,
	token *token,
) ([]*types.Operation, *rTypes.Error) {
	transactionTypes, err := tr.Types()
	if err != nil {
		return nil, err
	}

	transactionResults, err := tr.Results()
	if err != nil {
		return nil, err
	}

	nonFeeTransferMap := aggregateNonFeeTransfers(nonFeeTransfers)
	adjustedCryptoTransfers := adjustCryptoTransfers(cryptoTransfers, nonFeeTransferMap)
	operations, err := getTransferOperations(
		nonFeeTransfers,
		adjustedCryptoTransfers,
		tokenTransfers,
		transactions,
		transactionResults,
		transactionTypes,
	)
	if err != nil {
		return nil, err
	}

	tokenOperation, err := getTokenOperation(len(operations), token, transactions, transactionResults, transactionTypes)
	if err != nil {
		return nil, err
	} else if tokenOperation != nil {
		operations = append(operations, tokenOperation)
	}

	return operations, nil
}

func (tr *transactionRepository) retrieveTransactionTypesAndResults() *rTypes.Error {
	typeArray := tr.retrieveTransactionTypes()
	resultArray := tr.retrieveTransactionResults()

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

func constructAccount(encodedID int64) (*types.Account, *rTypes.Error) {
	account, err := types.NewAccountFromEncodedID(encodedID)
	if err != nil {
		log.Errorf(hErrors.CreateAccountDbIdFailed, encodedID)
		return nil, hErrors.ErrInternalServerError
	}
	return account, nil
}

func makeTransferKey(timestamp int64, entityId int64) string {
	return fmt.Sprintf("%d-%d", timestamp, entityId)
}

func adjustCryptoTransfers(
	cryptoTransfers []dbTypes.CryptoTransfer,
	nonFeeTransferMap map[string]int64,
) []dbTypes.CryptoTransfer {
	cryptoTransferMap := make(map[string]*aggregatedCryptoTransfer)
	for _, transfer := range cryptoTransfers {
		key := makeTransferKey(transfer.ConsensusTimestamp, transfer.EntityId)
		if aggregated, ok := cryptoTransferMap[key]; ok {
			aggregated.amount += transfer.Amount
		} else {
			cryptoTransferMap[key] = &aggregatedCryptoTransfer{
				entityId:  transfer.EntityId,
				amount:    transfer.Amount,
				timestamp: transfer.ConsensusTimestamp,
			}
		}
	}

	adjusted := make([]dbTypes.CryptoTransfer, 0, len(cryptoTransfers))
	for key, aggregated := range cryptoTransferMap {
		amount := aggregated.amount - nonFeeTransferMap[key]
		if amount != 0 {
			adjusted = append(adjusted, dbTypes.CryptoTransfer{
				Amount:             amount,
				EntityId:           aggregated.entityId,
				ConsensusTimestamp: aggregated.timestamp,
			})
		}
	}

	return adjusted
}

func aggregateNonFeeTransfers(nonFeeTransfers []dbTypes.CryptoTransfer) map[string]int64 {
	nonFeeTransferMap := make(map[string]int64)

	// the original transfer list from the transaction body
	for _, transfer := range nonFeeTransfers {
		// the original transfer list may have multiple entries for one entity, so accumulate it
		nonFeeTransferMap[makeTransferKey(transfer.ConsensusTimestamp, transfer.EntityId)] += transfer.Amount
	}

	return nonFeeTransferMap
}

func getTransferOperations(
	nonFeeTransfers []dbTypes.CryptoTransfer,
	cryptoTransfers []dbTypes.CryptoTransfer,
	tokenTransfers []tokenTransfer,
	transactionsMap map[int64]dbTypes.Transaction,
	transactionResults map[int]string,
	transactionTypes map[int]string,
) ([]*types.Operation, *rTypes.Error) {
	statusSuccess := transactionResults[transactionResultSuccess]
	count := len(nonFeeTransfers) + len(cryptoTransfers)
	operations := make([]*types.Operation, 0, count+len(tokenTransfers))
	hbarTransfers := make([]dbTypes.CryptoTransfer, 0, count)
	hbarTransfers = append(hbarTransfers, nonFeeTransfers...)
	hbarTransfers = append(hbarTransfers, cryptoTransfers...)

	for i, transfer := range hbarTransfers {
		account, err := constructAccount(transfer.EntityId)
		if err != nil {
			return nil, err
		}

		timestamp := transfer.ConsensusTimestamp
		operationType := transactionTypes[transactionsMap[timestamp].Type]

		// crypto transfer is always successful regardless of transaction result
		operationStatus := transactionResults[transactionsMap[timestamp].Result]
		if i >= len(nonFeeTransfers) {
			operationStatus = statusSuccess
		}

		operations = append(operations, &types.Operation{
			Index:   int64(len(operations)),
			Type:    operationType,
			Status:  operationStatus,
			Account: account,
			Amount:  &types.HbarAmount{Value: transfer.Amount},
		})
	}

	for _, transfer := range tokenTransfers {
		account, rErr := constructAccount(transfer.AccountId)
		if rErr != nil {
			return nil, rErr
		}

		amount, err := transfer.getTokenAmount()
		if err != nil {
			return nil, hErrors.ErrInvalidToken
		}

		timestamp := transfer.ConsensusTimestamp
		operationType := transactionTypes[transactionsMap[timestamp].Type]
		operationStatus := transactionResults[transactionsMap[timestamp].Result]

		operations = append(operations, &types.Operation{
			Index:   int64(len(operations)),
			Type:    operationType,
			Status:  operationStatus,
			Account: account,
			Amount:  amount,
		})
	}

	return operations, nil
}

func getTokenOperation(
	index int,
	token *token,
	transactionsMap map[int64]dbTypes.Transaction,
	transactionResults map[int]string,
	transactionTypes map[int]string,
) (*types.Operation, *rTypes.Error) {
	if token == nil {
		return nil, nil
	}

	transactionType := 0
	transactionResult := 0
	payer := int64(0)
	for _, transaction := range transactionsMap {
		payer = transaction.PayerAccountId
		transactionType = transaction.Type
		transactionResult = transaction.Result
		if transactionResult == transactionResultSuccess {
			break
		}
	}

	payerId, rErr := constructAccount(payer)
	if rErr != nil {
		return nil, rErr
	}

	tokenId, err := entityid.Decode(token.TokenId)
	if err != nil {
		return nil, hErrors.ErrInvalidToken
	}

	operationType := transactionTypes[transactionType]
	operationStatus := transactionResults[transactionResult]

	operation := &types.Operation{
		Index:   int64(index),
		Type:    operationType,
		Status:  operationStatus,
		Account: payerId,
		Amount: &types.TokenAmount{
			TokenId:  tokenId,
			Decimals: token.Decimals,
			Value:    0,
		},
	}

	if transactionType == dbTypes.TransactionTypeTokenCreation {
		// token creation shouldn't have Amount
		operation.Amount = nil
		metadata := make(map[string]interface{})
		operation.Metadata = metadata

		// best effort for immutable fields
		metadata["decimals"] = token.Decimals
		metadata["freeze_default"] = token.FreezeDefault
		metadata["initial_supply"] = token.InitialSupply
	}

	return operation, nil
}
