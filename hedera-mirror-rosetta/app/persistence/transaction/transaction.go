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
	"fmt"
	"strconv"
	"strings"
	"sync"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	dbTypes "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/common"
	hexUtils "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/maphelper"
	log "github.com/sirupsen/logrus"
	"gorm.io/gorm"
)

const (
	tableNameTransaction        = "transaction"
	tableNameTransactionResults = "t_transaction_results"
	tableNameTransactionTypes   = "t_transaction_types"
	transactionResultSuccess    = 22
)

const (
	whereClauseBetweenConsensus string = `SELECT * FROM transaction
                                          WHERE consensus_ns >= @start AND consensus_ns <= @end`
	whereCryptoTransferConsensusTimestampInTimestampsAsc string = `SELECT * FROM crypto_transfer
                                                                   WHERE consensus_timestamp IN (@timestamps)
                                                                   ORDER BY consensus_timestamp`
	whereNonFeeTransferConsensusTimestampInTimestampsAsc string = `SELECT * FROM non_fee_transfer
                                                                   WHERE consensus_timestamp IN (@timestamps)
                                                                   ORDER BY consensus_timestamp`
	whereTransactionsByHashAndConsensusTimestamps string = `SELECT * FROM transaction
                                                            WHERE transaction_hash = @hash
                                                            AND consensus_ns BETWEEN @start AND @end`
	selectTransactionResults string = "SELECT * FROM t_transaction_results"
	selectTransactionTypes   string = "SELECT * FROM t_transaction_types"
)

type transaction struct {
	ConsensusNS          int64  `gorm:"type:bigint;primary_key"`
	ChargedTxFee         int64  `gorm:"type:bigint"`
	EntityID             int64  `gorm:"type:bigint"`
	InitialBalance       int64  `gorm:"type:bigint"`
	MaxFee               int64  `gorm:"type:bigint"`
	Memo                 []byte `gorm:"type:bytea"`
	NodeAccountID        int64  `gorm:"type:bigint"`
	PayerAccountID       int64  `gorm:"type:bigint"`
	Result               int    `gorm:"type:smallint"`
	Scheduled            bool   `gorm:"type:bool"`
	TransactionBytes     []byte `gorm:"type:bytea"`
	TransactionHash      []byte `gorm:"type:bytea"`
	Type                 int    `gorm:"type:smallint"`
	ValidDurationSeconds int64  `gorm:"type:bigint"`
	ValidStartNS         int64  `gorm:"type:bigint"`
}

type transactionType struct {
	ProtoID int    `gorm:"type:integer;primary_key"`
	Name    string `gorm:"size:30"`
}

type transactionResult struct {
	ProtoID int    `gorm:"type:integer;primary_key"`
	Result  string `gorm:"size:100"`
}

type aggregatedCryptoTransfer struct {
	entityId  int64
	amount    int64
	timestamp int64
}

// TableName - Set table name of the Transactions to be `record_file`
func (transaction) TableName() string {
	return tableNameTransaction
}

// TableName - Set table name of the Transaction Types to be `t_transaction_types`
func (transactionType) TableName() string {
	return tableNameTransactionTypes
}

// TableName - Set table name of the Transaction Results to be `t_transaction_results`
func (transactionResult) TableName() string {
	return tableNameTransactionResults
}

func (t *transaction) getHashString() string {
	return hexUtils.SafeAddHexPrefix(hex.EncodeToString(t.TransactionHash))
}

// TransactionRepository struct that has connection to the Database
type TransactionRepository struct {
	once     sync.Once
	dbClient *gorm.DB
	results  map[int]string
	types    map[int]string
}

// NewTransactionRepository creates an instance of a TransactionRepository struct
func NewTransactionRepository(dbClient *gorm.DB) *TransactionRepository {
	return &TransactionRepository{dbClient: dbClient}
}

// Types returns map of all transaction types
func (tr *TransactionRepository) Types() (map[int]string, *rTypes.Error) {
	if tr.types == nil {
		err := tr.retrieveTransactionTypesAndResults()
		if err != nil {
			return nil, err
		}
	}
	return tr.types, nil
}

// Results returns map of all transaction results
func (tr *TransactionRepository) Results() (map[int]string, *rTypes.Error) {
	if tr.results == nil {
		err := tr.retrieveTransactionTypesAndResults()
		if err != nil {
			return nil, err
		}
	}
	return tr.results, nil
}

// TypesAsArray returns all Transaction type names as an array
func (tr *TransactionRepository) TypesAsArray() ([]string, *rTypes.Error) {
	transactionTypes, err := tr.Types()
	if err != nil {
		return nil, err
	}
	return maphelper.GetStringValuesFromIntStringMap(transactionTypes), nil
}

// FindBetween retrieves all Transactions between the provided start and end timestamp
func (tr *TransactionRepository) FindBetween(start int64, end int64) ([]*types.Transaction, *rTypes.Error) {
	if start > end {
		return nil, errors.ErrStartMustNotBeAfterEnd
	}
	var transactions []transaction
	tr.dbClient.Raw(whereClauseBetweenConsensus, sql.Named("start", start), sql.Named("end", end)).Find(&transactions)

	sameHashMap := make(map[string][]transaction)
	for _, t := range transactions {
		h := t.getHashString()
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
func (tr *TransactionRepository) FindByHashInBlock(
	hashStr string,
	consensusStart int64,
	consensusEnd int64,
) (*types.Transaction, *rTypes.Error) {
	var transactions []transaction
	transactionHash, err := hex.DecodeString(hexUtils.SafeRemoveHexPrefix(hashStr))
	if err != nil {
		return nil, errors.ErrInvalidTransactionIdentifier
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
		return nil, errors.ErrTransactionNotFound
	}

	transaction, err1 := tr.constructTransaction(transactions)
	if err1 != nil {
		return nil, err1
	}
	return transaction, nil
}

func (tr *TransactionRepository) findCryptoTransfersAsc(timestamps []int64) []dbTypes.CryptoTransfer {
	var cryptoTransfers []dbTypes.CryptoTransfer
	tr.findTransfersAsc(whereCryptoTransferConsensusTimestampInTimestampsAsc, timestamps, &cryptoTransfers)
	return cryptoTransfers
}

func (tr *TransactionRepository) findNonFeeTransfersAsc(timestamps []int64) []dbTypes.NonFeeTransfer {
	var nonFeeTransfers []dbTypes.NonFeeTransfer
	tr.findTransfersAsc(whereNonFeeTransferConsensusTimestampInTimestampsAsc, timestamps, &nonFeeTransfers)
	return nonFeeTransfers
}

func (tr *TransactionRepository) findTransfersAsc(query string, timestamps []int64, out interface{}) {
	timestampsStr := intsToString(timestamps)
	tr.dbClient.Raw(query, sql.Named("timestamps", timestampsStr)).Find(out)
}

func (tr *TransactionRepository) retrieveTransactionTypes() []transactionType {
	var transactionTypes []transactionType
	tr.dbClient.Raw(selectTransactionTypes).Find(&transactionTypes)
	return transactionTypes
}

func (tr *TransactionRepository) retrieveTransactionResults() []transactionResult {
	var tResults []transactionResult
	tr.dbClient.Raw(selectTransactionResults).Find(&tResults)
	return tResults
}

func (tr *TransactionRepository) constructTransaction(sameHashTransactions []transaction) (
	*types.Transaction,
	*rTypes.Error,
) {
	tResult := &types.Transaction{Hash: sameHashTransactions[0].getHashString()}

	transactionsMap := make(map[int64]transaction)
	timestamps := make([]int64, len(sameHashTransactions))
	for i, t := range sameHashTransactions {
		transactionsMap[t.ConsensusNS] = t
		timestamps[i] = t.ConsensusNS
	}
	cryptoTransfers := tr.findCryptoTransfersAsc(timestamps)
	nonFeeTransfers := tr.findNonFeeTransfersAsc(timestamps)
	operations, err := tr.constructOperations(cryptoTransfers, nonFeeTransfers, transactionsMap)
	if err != nil {
		return nil, err
	}
	tResult.Operations = operations

	return tResult, nil
}

func (tr *TransactionRepository) constructOperations(
	cryptoTransfers []dbTypes.CryptoTransfer,
	nonFeeTransfers []dbTypes.NonFeeTransfer,
	transactions map[int64]transaction,
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
	return getOperations(nonFeeTransfers, adjustedCryptoTransfers, transactions, transactionResults, transactionTypes)
}

func (tr *TransactionRepository) retrieveTransactionTypesAndResults() *rTypes.Error {
	typeArray := tr.retrieveTransactionTypes()
	resultArray := tr.retrieveTransactionResults()

	if len(typeArray) == 0 {
		log.Warn("No Transaction Types were found in the database.")
		return errors.ErrOperationTypesNotFound
	}

	if len(resultArray) == 0 {
		log.Warn("No Transaction Results were found in the database.")
		return errors.ErrOperationResultsNotFound
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
	acc, err := types.NewAccountFromEncodedID(encodedID)
	if err != nil {
		log.Errorf(errors.CreateAccountDbIdFailed, encodedID)
		return nil, errors.ErrInternalServerError
	}
	return acc, nil
}

func intsToString(ints []int64) string {
	r := make([]string, len(ints))
	for i, v := range ints {
		r[i] = strconv.FormatInt(v, 10)
	}
	return strings.Join(r, ",")
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
		key := makeTransferKey(transfer.ConsensusTimestamp, transfer.EntityID)
		if aggregated, ok := cryptoTransferMap[key]; ok {
			aggregated.amount += transfer.Amount
		} else {
			cryptoTransferMap[key] = &aggregatedCryptoTransfer{
				entityId:  transfer.EntityID,
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
				EntityID:           aggregated.entityId,
				ConsensusTimestamp: aggregated.timestamp,
			})
		}
	}

	return adjusted
}

func aggregateNonFeeTransfers(nonFeeTransfers []dbTypes.NonFeeTransfer) map[string]int64 {
	nonFeeTransferMap := make(map[string]int64)

	// the original transfer list from the transaction body
	for _, transfer := range nonFeeTransfers {
		// the original transfer list may have multiple entries for one entity, so accumulate it
		nonFeeTransferMap[makeTransferKey(transfer.ConsensusTimestamp, transfer.EntityID)] += transfer.Amount
	}

	return nonFeeTransferMap
}

func getOperations(
	nonFeeTransfers []dbTypes.NonFeeTransfer,
	cryptoTransfers []dbTypes.CryptoTransfer,
	transactionsMap map[int64]transaction,
	transactionResults map[int]string,
	transactionTypes map[int]string,
) ([]*types.Operation, *rTypes.Error) {
	statusSuccess := transactionResults[transactionResultSuccess]
	count := len(nonFeeTransfers) + len(cryptoTransfers)
	operations := make([]*types.Operation, 0, count)
	transfers := make([]dbTypes.Transfer, 0, count)

	for _, nonFeeTransfer := range nonFeeTransfers {
		transfers = append(transfers, nonFeeTransfer)
	}

	for _, cryptoTransfer := range cryptoTransfers {
		transfers = append(transfers, cryptoTransfer)
	}

	for _, transfer := range transfers {
		account, err := constructAccount(transfer.GetEntityID())
		if err != nil {
			return nil, err
		}

		timestamp := transfer.GetConsensusTimestamp()
		operationType := transactionTypes[transactionsMap[timestamp].Type]

		// crypto transfer is always successful regardless of transaction result
		operationStatus := statusSuccess
		if _, ok := transfer.(dbTypes.CryptoTransfer); !ok {
			operationStatus = transactionResults[transactionsMap[timestamp].Result]
		}

		operations = append(operations, &types.Operation{
			Index:   int64(len(operations)),
			Type:    operationType,
			Status:  operationStatus,
			Account: account,
			Amount:  &types.Amount{Value: transfer.GetAmount()},
		})
	}

	return operations, nil
}
