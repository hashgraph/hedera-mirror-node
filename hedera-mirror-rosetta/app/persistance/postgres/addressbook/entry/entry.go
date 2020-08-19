package entry

import (
	"fmt"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/jinzhu/gorm"
)

const (
	latestAddressBookEntries = "SELECT abe.* FROM %s AS abe JOIN %s AS ab ON ab.start_consensus_timestamp = abe.consensus_timestamp WHERE ab.end_consensus_timestamp IS NULL"
)

const (
	tableNameAddressBook      = "address_book"
	tableNameAddressBookEntry = "address_book_entry"
)

type addressBookEntry struct {
	Id                 int32  `gorm:"type:integer;primary_key"`
	ConsensusTimestamp int64  `gorm:"type:bigint"`
	Ip                 string `gorm:"size:128"`
	Port               int32  `gorm:"type:integer"`
	Memo               string `gorm:"size:128"`
	PublicKey          string `gorm:"size:1024"`
	NodeId             *int64 `gorm:"type:bigint"`
	NodeAccountId      int64  `gorm:"type:bigint"`
	NodeCertHash       []byte `gorm:"type:bytea"`
}

func (addressBookEntry) TableName() string {
	return tableNameAddressBookEntry
}

// AddressBookEntryRepository struct that has connection to the Database
type AddressBookEntryRepository struct {
	dbClient *gorm.DB
}

// Entries return all found Address Book Entries
func (aber *AddressBookEntryRepository) Entries() (*types.AddressBookEntries, *rTypes.Error) {
	dbEntries := aber.retrieveEntries()

	entries := make([]*types.AddressBookEntry, len(dbEntries))
	for i, e := range dbEntries {
		entries[i] = &types.AddressBookEntry{
			PeerId: e.getPeerId(),
			Metadata: map[string]interface{}{
				"ip":   e.Ip,
				"port": e.Port,
			},
		}
	}

	return &types.AddressBookEntries{
		Entries: entries}, nil
}

func (abe *addressBookEntry) getPeerId() *types.Account {
	if abe.NodeId == nil {
		acc, err := types.AccountFromString(abe.Memo)
		if err != nil {
			panic(fmt.Sprintf(errors.CreateAccountDbIdFailed, abe.Memo))
		}
		return acc
	}

	decoded, err := types.NewAccountFromEncodedID(*abe.NodeId)
	if err != nil {
		panic(fmt.Sprintf(errors.CreateAccountDbIdFailed, abe.NodeId))
	}

	return decoded
}

func (aber *AddressBookEntryRepository) retrieveEntries() []addressBookEntry {
	var entries []addressBookEntry
	aber.dbClient.Raw(fmt.Sprintf(latestAddressBookEntries, tableNameAddressBookEntry, tableNameAddressBook)).Scan(&entries)
	return entries
}

// NewAddressBookEntryRepository creates an instance of a AddressBookEntryRepository struct.
func NewAddressBookEntryRepository(dbClient *gorm.DB) *AddressBookEntryRepository {
	return &AddressBookEntryRepository{
		dbClient: dbClient,
	}
}
