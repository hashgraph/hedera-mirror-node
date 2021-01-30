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
	"encoding/hex"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	dbTypes "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/common"
	hexUtils "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/maphelper"
	"log"
	"strconv"
	"strings"
	"sync"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/jinzhu/gorm"
)

const (
	tableNameTransaction        = "transaction"
	tableNameTransactionResults = "t_transaction_results"
	tableNameTransactionTypes   = "t_transaction_types"
)

const (
	whereClauseBetweenConsensus string = `SELECT * FROM transaction
                                          WHERE consensus_ns >= $1 AND consensus_ns <= $2`
	whereTimestampsInConsensusTimestamp string = `SELECT * FROM crypto_transfer
                                                  WHERE consensus_timestamp IN ($1)`
	whereTransactionsByHashAndConsensusTimestamps string = `SELECT * FROM transaction
                                                            WHERE transaction_hash = $1
                                                            AND consensus_ns BETWEEN $2 AND $3`
	selectTransactionResults string = "SELECT * FROM t_transaction_results"
	selectTransactionTypes   string = "SELECT * FROM t_transaction_types"
)

type transaction struct {
	ConsensusNS          int64  `gorm:"type:bigint;primary_key"`
	Type                 int    `gorm:"type:smallint"`
	Result               int    `gorm:"type:smallint"`
	PayerAccountID       int64  `gorm:"type:bigint"`
	ValidStartNS         int64  `gorm:"type:bigint"`
	ValidDurationSeconds int64  `gorm:"type:bigint"`
	NodeAccountID        int64  `gorm:"type:bigint"`
	EntityID             int64  `gorm:"type:bigint"`
	InitialBalance       int64  `gorm:"type:bigint"`
	MaxFee               int64  `gorm:"type:bigint"`
	ChargedTxFee         int64  `gorm:"type:bigint"`
	Memo                 []byte `gorm:"type:bytea"`
	TransactionHash      []byte `gorm:"type:bytea"`
	TransactionBytes     []byte `gorm:"type:bytea"`
}

type transactionType struct {
	ProtoID int    `gorm:"type:integer;primary_key"`
	Name    string `gorm:"size:30"`
}

type transactionResult struct {
	ProtoID int    `gorm:"type:integer;primary_key"`
	Result  string `gorm:"size:100"`
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
	statuses map[int]string
	types    map[int]string
}

// NewTransactionRepository creates an instance of a TransactionRepository struct
func NewTransactionRepository(dbClient *gorm.DB) *TransactionRepository {
	return &TransactionRepository{dbClient: dbClient}
}

// Types returns map of all Transaction Types
func (tr *TransactionRepository) Types() (map[int]string, *rTypes.Error) {
	if tr.types == nil {
		err := tr.retrieveTransactionTypesAndStatuses()
		if err != nil {
			return nil, err
		}
	}
	return tr.types, nil
}

// Statuses returns map of all Transaction Results
func (tr *TransactionRepository) Statuses() (map[int]string, *rTypes.Error) {
	if tr.statuses == nil {
		err := tr.retrieveTransactionTypesAndStatuses()
		if err != nil {
			return nil, err
		}
	}
	return tr.statuses, nil
}

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
		return nil, errors.Errors[errors.StartMustNotBeAfterEnd]
	}
	var transactions []transaction
	tr.dbClient.Raw(whereClauseBetweenConsensus, start, end).Find(&transactions)

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
func (tr *TransactionRepository) FindByHashInBlock(hashStr string, consensusStart int64, consensusEnd int64) (*types.Transaction, *rTypes.Error) {
	var transactions []transaction
	transactionHash, err := hex.DecodeString(hexUtils.SafeRemoveHexPrefix(hashStr))
	if err != nil {
		return nil, errors.Errors[errors.InvalidTransactionIdentifier]
	}
	tr.dbClient.Raw(whereTransactionsByHashAndConsensusTimestamps, transactionHash, consensusStart, consensusEnd).Find(&transactions)

	if len(transactions) == 0 {
		return nil, errors.Errors[errors.TransactionNotFound]
	}

	transaction, err1 := tr.constructTransaction(transactions)
	if err1 != nil {
		return nil, err1
	}
	return transaction, nil
}

func (tr *TransactionRepository) findCryptoTransfers(timestamps []int64) []dbTypes.CryptoTransfer {
	var cryptoTransfers []dbTypes.CryptoTransfer
	timestampsStr := intsToString(timestamps)
	tr.dbClient.Raw(whereTimestampsInConsensusTimestamp, timestampsStr).Find(&cryptoTransfers)
	return cryptoTransfers
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

func (tr *TransactionRepository) constructTransaction(sameHashTransactions []transaction) (*types.Transaction, *rTypes.Error) {
	tResult := &types.Transaction{Hash: sameHashTransactions[0].getHashString()}

	transactionsMap := make(map[int64]transaction)
	timestamps := make([]int64, len(sameHashTransactions))
	for i, t := range sameHashTransactions {
		transactionsMap[t.ConsensusNS] = t
		timestamps[i] = t.ConsensusNS
	}
	cryptoTransfers := tr.findCryptoTransfers(timestamps)
	operations, err := tr.constructOperations(cryptoTransfers, transactionsMap)
	if err != nil {
		return nil, err
	}
	tResult.Operations = operations

	return tResult, nil
}

func (tr *TransactionRepository) constructOperations(cryptoTransfers []dbTypes.CryptoTransfer, transactionsMap map[int64]transaction) ([]*types.Operation, *rTypes.Error) {
	transactionTypes, err := tr.Types()
	if err != nil {
		return nil, err
	}

	transactionStatuses, _ := tr.Statuses()

	operations := make([]*types.Operation, len(cryptoTransfers))
	for i, ct := range cryptoTransfers {
		a, err := constructAccount(ct.EntityID)
		if err != nil {
			return nil, err
		}
		operationType := transactionTypes[transactionsMap[ct.ConsensusTimestamp].Type]
		operationStatus := transactionStatuses[transactionsMap[ct.ConsensusTimestamp].Result]
		operations[i] = &types.Operation{Index: int64(i), Type: operationType, Status: operationStatus, Account: a, Amount: &types.Amount{Value: ct.Amount}}
	}
	return operations, nil
}

func (tr *TransactionRepository) retrieveTransactionTypesAndStatuses() *rTypes.Error {
	typesArray := tr.retrieveTransactionTypes()
	rArray := tr.retrieveTransactionResults()

	if len(typesArray) == 0 {
		log.Println("No Transaction Types were found in the database.")
		return errors.Errors[errors.OperationTypesNotFound]
	}

	if len(rArray) == 0 {
		log.Println("No Transaction Results were found in the database.")
		return errors.Errors[errors.OperationStatusesNotFound]
	}

	tr.once.Do(func() {
		tr.types = make(map[int]string)
		for _, t := range typesArray {
			tr.types[t.ProtoID] = t.Name
		}

		tr.statuses = make(map[int]string)
		for _, s := range rArray {
			tr.statuses[s.ProtoID] = s.Result
		}
	})

	return nil
}

func constructAccount(encodedID int64) (*types.Account, *rTypes.Error) {
	acc, err := types.NewAccountFromEncodedID(encodedID)
	if err != nil {
		log.Printf(errors.CreateAccountDbIdFailed, encodedID)
		return nil, errors.Errors[errors.InternalServerError]
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
