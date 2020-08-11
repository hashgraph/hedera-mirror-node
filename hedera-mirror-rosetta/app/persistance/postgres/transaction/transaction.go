package transaction

import (
	"fmt"
	"math/big"
	"strings"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/maphelper"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/jinzhu/gorm"
)

const (
	whereClauseBetweenConsensus string = "consensus_ns >= ? AND consensus_ns <= ?"
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

type cryptoTransfer struct {
	EntityID           int64 `gorm:"type:bigint"`
	ConsensusTimestamp int64 `gorm:"type:bigint"`
	Amount             int64 `gorm:"type:bigint"`
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

// TableName - Set table name of the CryptoTransfers to be `crypto_transfer`
func (cryptoTransfer) TableName() string {
	return "crypto_transfer"
}

// TableName - Set table name of the Transaction Types to be `t_transaction_types`
func (transactionType) TableName() string {
	return "t_transaction_types"
}

// TableName - Set table name of the Transaction Statuses to be `t_transaction_results`
func (transactionStatus) TableName() string {
	return "t_transaction_results"
}

// The current format used is {payer-ID}-{valid_start_seconds}-{valid_start_nanos}-{node-ID}
func (t *transaction) constructIdentifier() string {
	payerID := constructAccount(t.PayerAccountID)
	nodeID := constructAccount(t.NodeAccountID)

	validStartBn, tenToNinthBn := new(big.Float).SetInt(big.NewInt(t.ValidStartNS)), big.NewFloat(1e9)
	formattedTime := validStartBn.Quo(validStartBn, tenToNinthBn)
	return fmt.Sprintf("%s-%.9f-%s", payerID.FormatToString(), formattedTime, nodeID.FormatToString())
}

// Populates transaction struct from a given Identifier
func newTransactionFromIdentifier(identifier string) (*transaction, *rTypes.Error) {
	inputs := strings.Split(identifier, "-")
	if len(inputs) != 3 {
		return nil, errors.Errors[errors.InvalidTransactionIdentifier]
	}

	payerID, err := types.AccountFromString(inputs[0])
	if err != nil {
		return nil, errors.Errors[errors.InvalidTransactionIdentifier]
	}
	nodeID, err := types.AccountFromString(inputs[2])
	if err != nil {
		return nil, errors.Errors[errors.InvalidTransactionIdentifier]
	}

	var err1 error
	t := transaction{}
	t.PayerAccountID, err1 = payerID.ComputeEncodedID()
	if err1 != nil {
		return nil, errors.Errors[errors.InvalidTransactionIdentifier]
	}
	t.NodeAccountID, err1 = nodeID.ComputeEncodedID()
	if err1 != nil {
		return nil, errors.Errors[errors.InvalidTransactionIdentifier]
	}

	validStart, _, err1 := new(big.Float).Parse(inputs[1], 10)
	if err1 != nil {
		return nil, errors.Errors[errors.InvalidTransactionIdentifier]
	}

	t.ValidStartNS, _ = validStart.Mul(validStart, big.NewFloat(1e9)).Int64()
	return &t, nil
}

// TransactionRepository struct that has connection to the Database
type TransactionRepository struct {
	dbClient *gorm.DB
	types    map[int]string
	statuses map[int]string
}

// NewTransactionRepository creates an instance of a TransactionRepository struct. Populates the transaction types and statuses on init
func NewTransactionRepository(dbClient *gorm.DB) *TransactionRepository {
	tr := &TransactionRepository{dbClient: dbClient}

	typesArray := tr.retrieveTransactionTypes()
	tMap := make(map[int]string)
	for _, t := range typesArray {
		tMap[t.ProtoID] = t.Name
	}

	statusesArray := tr.retrieveTransactionStatuses()
	sMap := make(map[int]string)
	for _, s := range statusesArray {
		sMap[s.ProtoID] = s.Result
	}

	tr.types = tMap
	tr.statuses = sMap
	return tr
}

// Types returns map of all Transaction Types
func (tr *TransactionRepository) Types() map[int]string {
	return tr.types
}

// Statuses returns map of all Transaction Statuses
func (tr *TransactionRepository) Statuses() map[int]string {
	return tr.statuses
}

func (tr *TransactionRepository) TypesAsArray() []string {
	return maphelper.GetStringValuesFromIntStringMap(tr.types)
}

// FindByTimestamp retrieves Transaction by given timestamp
func (tr *TransactionRepository) FindByTimestamp(timestamp int64) *types.Transaction {
	t := transaction{}
	tr.dbClient.Find(&t, timestamp)
	return tr.constructTransaction(t)
}

// FindBetween retrieves all Transactions between the provided start and end timestamp
func (tr *TransactionRepository) FindBetween(start int64, end int64) ([]*types.Transaction, *rTypes.Error) {
	if start > end {
		return nil, errors.Errors[errors.StartMustBeBeforeEnd]
	}
	tArray := []transaction{}
	tr.dbClient.Where(whereClauseBetweenConsensus, start, end).Find(&tArray)

	res := make([]*types.Transaction, len(tArray))
	for i, t := range tArray {
		res[i] = tr.constructTransaction(t)
	}
	return res, nil
}

// FindByIdentifier retrieves a transaction by Identifier
func (tr *TransactionRepository) FindByIdentifierInBlock(identifier string, consensusStart int64, consensusEnd int64) (*types.Transaction, *rTypes.Error) {
	t := transaction{}
	queryT, err := newTransactionFromIdentifier(identifier)
	if err != nil {
		return nil, err
	}
	if tr.dbClient.Where(whereClauseBetweenConsensus, consensusStart, consensusEnd).Where(queryT).Find(&t).RecordNotFound() {
		return nil, errors.Errors[errors.TransactionNotFound]
	}
	return tr.constructTransaction(t), nil
}

func (tr *TransactionRepository) findCryptoTransfers(timestamp int64) []cryptoTransfer {
	ctArray := []cryptoTransfer{}
	tr.dbClient.Where(&cryptoTransfer{ConsensusTimestamp: timestamp}).Find(&ctArray)
	return ctArray
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

func (tr *TransactionRepository) constructTransaction(t transaction) *types.Transaction {
	tResult := &types.Transaction{ID: t.constructIdentifier()}

	ctArray := tr.findCryptoTransfers(t.ConsensusNS)
	oArray := tr.constructOperations(ctArray, tr.types[t.Type], tr.statuses[t.Result])
	tResult.Operations = oArray

	return tResult
}

func (tr *TransactionRepository) constructOperations(ctArray []cryptoTransfer, transactionType string, transactionStatus string) []*types.Operation {
	oArray := make([]*types.Operation, len(ctArray))
	for i, ct := range ctArray {
		a := constructAccount(ct.EntityID)
		oArray[i] = &types.Operation{Index: int64(i), Type: transactionType, Status: transactionStatus, Account: a, Amount: ct.Amount}
	}
	return oArray
}

func constructAccount(encodedID int64) *types.Account {
	acc, err := types.NewAccountFromEncodedID(encodedID)
	if err != nil {
		panic(fmt.Sprintf("Cannot create Account ID from encoded DB ID: %d", encodedID))
	}
	return acc
}
