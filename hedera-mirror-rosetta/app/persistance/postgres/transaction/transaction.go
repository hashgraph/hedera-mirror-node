package transaction

import (
	"encoding/hex"
	"errors"
	"fmt"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/jinzhu/gorm"
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

func (t *transaction) constructID() string {
	return fmt.Sprintf("%s@%d", hex.EncodeToString(t.TransactionHash), t.NodeAccountID) // TODO we will have to settle on the TX ID. For now using the TX (Hash@NodeID)
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

// GetTypes returns map of all Transaction Types
func (tr *TransactionRepository) GetTypes() map[int]string {
	return tr.types
}

// GetStatuses returns map of all Transaction Statuses
func (tr *TransactionRepository) GetStatuses() map[int]string {
	return tr.statuses
}

// FindByTimestamp retrieves Transaction by given timestmap
func (tr *TransactionRepository) FindByTimestamp(timestamp int64) *types.Transaction {
	t := &transaction{}
	tr.dbClient.Find(t, timestamp)
	tResult := tr.constructTransaction(t)
	return &tResult
}

// FindBetween retrieves all Transactions between the provided start and end timestamp
func (tr *TransactionRepository) FindBetween(start int64, end int64) ([]types.Transaction, error) {
	if start > end {
		return nil, errors.New("start must be before end")
	}
	tArray := []transaction{}
	tr.dbClient.Where("consensus_ns >= ? AND consensus_ns <= ?", start, end).Find(&tArray)

	res := make([]types.Transaction, len(tArray))
	for i, t := range tArray {
		res[i] = tr.constructTransaction(&t)
	}
	return res, nil
}

func (tr *TransactionRepository) findCryptoTransfers(timestamp int64) []cryptoTransfer {
	ctArray := []cryptoTransfer{}
	tr.dbClient.Where(&cryptoTransfer{ConsensusTimestamp: timestamp}).Find(&ctArray)
	return ctArray
}

func (tr *TransactionRepository) retrieveTransactionTypes() []transactionType {
	types := []transactionType{}
	tr.dbClient.Find(&types)
	return types
}

func (tr *TransactionRepository) retrieveTransactionStatuses() []transactionStatus {
	statuses := []transactionStatus{}
	tr.dbClient.Find(&statuses)
	return statuses
}

func (tr *TransactionRepository) constructTransaction(t *transaction) types.Transaction {
	tResult := types.Transaction{ID: t.constructID()}

	ctArray := tr.findCryptoTransfers(t.ConsensusNS)
	oArray := tr.constructOperations(ctArray, tr.types[t.Type], tr.statuses[t.Result])
	tResult.Operations = oArray

	return tResult
}

func (tr *TransactionRepository) constructOperations(ctArray []cryptoTransfer, transactionType string, transactionStatus string) []types.Operation {
	oArray := make([]types.Operation, len(ctArray))
	for i, ct := range ctArray {
		oArray[i] = types.Operation{Index: int64(i), Type: transactionType, Status: transactionStatus, EntityID: ct.EntityID, Amount: ct.Amount}
	}
	return oArray
}
