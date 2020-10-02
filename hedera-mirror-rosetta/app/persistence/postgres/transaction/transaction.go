package transaction

import (
	"encoding/hex"
	"fmt"
	dbTypes "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/postgres/types"
	hexUtils "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	"strconv"
	"strings"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/maphelper"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/jinzhu/gorm"
)

const (
	whereClauseBetweenConsensus         string = "consensus_ns >= ? AND consensus_ns <= ?"
	whereTimestampsInConsensusTimestamp string = "consensus_timestamp IN (%s)"
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

type transactionStatus struct {
	ProtoID int    `gorm:"type:integer;primary_key"`
	Result  string `gorm:"size:100"`
}

// TableName - Set table name of the Transactions to be `record_file`
func (transaction) TableName() string {
	return "transaction"
}

// TableName - Set table name of the Transaction Types to be `t_transaction_types`
func (transactionType) TableName() string {
	return "t_transaction_types"
}

// TableName - Set table name of the Transaction Statuses to be `t_transaction_results`
func (transactionStatus) TableName() string {
	return "t_transaction_results"
}

func (t *transaction) getHashString() string {
	return hexUtils.SafeAddHexPrefix(hex.EncodeToString(t.TransactionHash))
}

// TransactionRepository struct that has connection to the Database
type TransactionRepository struct {
	dbClient *gorm.DB
}

// NewTransactionRepository creates an instance of a TransactionRepository struct. Populates the transaction types and statuses on init
func NewTransactionRepository(dbClient *gorm.DB) *TransactionRepository {
	return &TransactionRepository{dbClient: dbClient}
}

// Types returns map of all Transaction Types
// TODO implement cache instead of retrieving this everytime form DB
func (tr *TransactionRepository) Types() map[int]string {
	typesArray := tr.retrieveTransactionTypes()
	tMap := make(map[int]string)
	for _, t := range typesArray {
		tMap[t.ProtoID] = t.Name
	}
	return tMap
}

// Statuses returns map of all Transaction Statuses
// TODO implement cache instead of retrieving this everytime form DB
func (tr *TransactionRepository) Statuses() map[int]string {
	statusesArray := tr.retrieveTransactionStatuses()
	sMap := make(map[int]string)
	for _, s := range statusesArray {
		sMap[s.ProtoID] = s.Result
	}
	return sMap
}

func (tr *TransactionRepository) TypesAsArray() []string {
	return maphelper.GetStringValuesFromIntStringMap(tr.Types())
}

// FindByTimestamp retrieves Transaction by given timestamp
func (tr *TransactionRepository) FindByTimestamp(timestamp int64) *types.Transaction {
	t := transaction{}
	tr.dbClient.Find(&t, timestamp)
	return tr.constructTransaction([]transaction{t})
}

// FindBetween retrieves all Transactions between the provided start and end timestamp
func (tr *TransactionRepository) FindBetween(start int64, end int64) ([]*types.Transaction, *rTypes.Error) {
	if start > end {
		return nil, errors.Errors[errors.StartMustBeBeforeEnd]
	}
	var transactions []transaction
	tr.dbClient.Where(whereClauseBetweenConsensus, start, end).Find(&transactions)

	sameHashMap := make(map[string][]transaction)
	for _, t := range transactions {
		h := t.getHashString()
		sameHashMap[h] = append(sameHashMap[h], t)
	}
	res := make([]*types.Transaction, 0, len(sameHashMap))
	for _, sameHashTransactions := range sameHashMap {
		res = append(res, tr.constructTransaction(sameHashTransactions))
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
	tr.dbClient.Where(whereClauseBetweenConsensus, consensusStart, consensusEnd).Where(&transaction{TransactionHash: transactionHash}).Find(&transactions)
	if len(transactions) == 0 {
		return nil, errors.Errors[errors.TransactionNotFound]
	}

	return tr.constructTransaction(transactions), nil
}

func (tr *TransactionRepository) findCryptoTransfers(timestamps []int64) []dbTypes.CryptoTransfer {
	var cryptoTransfers []dbTypes.CryptoTransfer
	timestampsStr := intsToString(timestamps)
	tr.dbClient.Where(fmt.Sprintf(whereTimestampsInConsensusTimestamp, timestampsStr)).Find(&cryptoTransfers)
	return cryptoTransfers
}

func (tr *TransactionRepository) retrieveTransactionTypes() []transactionType {
	var transactionTypes []transactionType
	tr.dbClient.Find(&transactionTypes)
	return transactionTypes
}

func (tr *TransactionRepository) retrieveTransactionStatuses() []transactionStatus {
	var statuses []transactionStatus
	tr.dbClient.Find(&statuses)
	return statuses
}

func (tr *TransactionRepository) constructTransaction(sameHashTransactions []transaction) *types.Transaction {
	tResult := &types.Transaction{Hash: sameHashTransactions[0].getHashString()}

	transactionsMap := make(map[int64]transaction)
	timestamps := make([]int64, len(sameHashTransactions))
	for i, t := range sameHashTransactions {
		transactionsMap[t.ConsensusNS] = t
		timestamps[i] = t.ConsensusNS
	}
	cryptoTransfers := tr.findCryptoTransfers(timestamps)
	operations := tr.constructOperations(cryptoTransfers, transactionsMap)
	tResult.Operations = operations

	return tResult
}

func (tr *TransactionRepository) constructOperations(cryptoTransfers []dbTypes.CryptoTransfer, transactionsMap map[int64]transaction) []*types.Operation {
	transactionTypes := tr.Types()
	transactionStatuses := tr.Statuses()

	operations := make([]*types.Operation, len(cryptoTransfers))
	for i, ct := range cryptoTransfers {
		a := constructAccount(ct.EntityID)
		operationType := transactionTypes[transactionsMap[ct.ConsensusTimestamp].Type]
		operationStatus := transactionStatuses[transactionsMap[ct.ConsensusTimestamp].Result]
		operations[i] = &types.Operation{Index: int64(i), Type: operationType, Status: operationStatus, Account: a, Amount: &types.Amount{Value: ct.Amount}}
	}
	return operations
}

func constructAccount(encodedID int64) *types.Account {
	acc, err := types.NewAccountFromEncodedID(encodedID)
	if err != nil {
		panic(fmt.Sprintf(errors.CreateAccountDbIdFailed, encodedID))
	}
	return acc
}

func intsToString(ints []int64) string {
	r := make([]string, len(ints))
	for i, v := range ints {
		r[i] = strconv.FormatInt(v, 10)
	}
	return strings.Join(r, ",")
}
