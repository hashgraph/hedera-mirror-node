package account

import (
	"fmt"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	dbTypes "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/postgres/types"
	"github.com/jinzhu/gorm"
)

const (
	balanceChangeBetween string = "select sum(amount::bigint) as value, count(consensus_timestamp) as number_of_transfers from %s where consensus_timestamp > %d and consensus_timestamp <= %d and entity_id = %d"
)

type accountBalance struct {
	ConsensusTimestamp int64 `gorm:"type:bigint;primary_key"`
	Balance            int64 `gorm:"type:bigint"`
	AccountRealmNum    int16 `gorm:"type:smallint;primary_key"`
	AccountNum         int32 `gorm:"type:integer;primary_key"`
}

type balanceChange struct {
	Value             int64 `gorm:"type:bigint"`
	NumberOfTransfers int64 `gorm:"type:bigint"`
}

// TableName - Set table name of the accountBalance to be `account_balance`
func (accountBalance) TableName() string {
	return "account_balance"
}

// AccountRepository struct that has connection to the Database
type AccountRepository struct {
	dbClient *gorm.DB
}

// NewAccountRepository creates an instance of a TransactionRepository struct. Populates the transaction types and statuses on init
func NewAccountRepository(dbClient *gorm.DB) *AccountRepository {
	return &AccountRepository{
		dbClient: dbClient,
	}
}

// RetrieveBalanceAtBlock returns the balance of the account at a given block (provided by consensusEnd timestamp).
// balance = balanceAtLatestBalanceSnapshot + balanceChangeBetweenSnapshotAndBlock
func (ar *AccountRepository) RetrieveBalanceAtBlock(addressStr string, consensusEnd int64) (*types.Amount, *rTypes.Error) {
	acc, err := types.AccountFromString(addressStr)
	if err != nil {
		return nil, err
	}
	entityID, err1 := acc.ComputeEncodedID()
	if err1 != nil {
		return nil, errors.Errors[errors.InvalidAccount]
	}

	// gets the most recent balance before block
	ab := &accountBalance{}
	if ar.dbClient.
		Where(fmt.Sprintf(`account_realm_num=%d AND account_num=%d AND consensus_timestamp <= %d`, int16(acc.Realm), int32(acc.Number), consensusEnd)).
		Order("consensus_timestamp desc").
		Limit(1).
		Find(&ab).RecordNotFound() {
		ab.Balance = 0
	}

	ct := &dbTypes.CryptoTransfer{}
	r := &balanceChange{}
	// gets the balance change from the Balance snapshot until the target block
	ar.dbClient.Raw(fmt.Sprintf(balanceChangeBetween, ct.TableName(), ab.ConsensusTimestamp, consensusEnd, entityID)).Scan(r)

	return &types.Amount{
		Value: ab.Balance + r.Value,
	}, nil
}
