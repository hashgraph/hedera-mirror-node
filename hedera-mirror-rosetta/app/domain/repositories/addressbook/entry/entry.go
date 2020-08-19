package repositories

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
)

// AddressBookEntryRepository Interface that all AddressBookEntryRepository structs must implement
type AddressBookEntryRepository interface {
	Entries() (*types.AddressBookEntries, *rTypes.Error)
}
