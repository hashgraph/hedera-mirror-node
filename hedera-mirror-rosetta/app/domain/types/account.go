package types

import (
	"fmt"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/entity-id-codec"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/parse"
	"strings"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
)

// Account is domain level struct used to represent Rosetta Account
type Account struct {
	Shard  int64
	Realm  int64
	Number int64
}

// NewAccountFromEncodedID - creates new instance of Account struct
func NewAccountFromEncodedID(encodedID int64) (*Account, error) {
	d, err := entity_id_codec.Decode(encodedID)
	if err != nil {
		return nil, err
	}

	return &Account{
		Shard:  d.ShardNum,
		Realm:  d.RealmNum,
		Number: d.EntityNum,
	}, err
}

// ComputeEncodedID - returns the encoded ID from the Shard, Realm and Number
func (a *Account) ComputeEncodedID() (int64, error) {
	return entity_id_codec.Encode(a.Shard, a.Realm, a.Number)
}

// String - returns the string representation of the account
func (a *Account) String() string {
	return fmt.Sprintf("%d.%d.%d", a.Shard, a.Realm, a.Number)
}

// FromRosettaAccount populates domain type Account from Rosetta type Account
func FromRosettaAccount(rAccount *rTypes.AccountIdentifier) (*Account, *rTypes.Error) {
	return AccountFromString(rAccount.Address)
}

// ToRosettaAccount returns Rosetta type Account from the current domain type Account
func (a *Account) ToRosettaAccount() *rTypes.AccountIdentifier {
	return &rTypes.AccountIdentifier{
		Address: a.String(),
	}
}

// AccountFromString populates domain type Account from String Account
func AccountFromString(account string) (*Account, *rTypes.Error) {
	inputs := strings.Split(account, ".")
	if len(inputs) != 3 {
		return nil, errors.Errors[errors.InvalidAccount]
	}

	shard, err := parse.ToInt64(inputs[0])
	if err != nil {
		return nil, errors.Errors[errors.InvalidAccount]
	}
	realm, err := parse.ToInt64(inputs[1])
	if err != nil {
		return nil, errors.Errors[errors.InvalidAccount]
	}
	number, err := parse.ToInt64(inputs[2])
	if err != nil {
		return nil, errors.Errors[errors.InvalidAccount]
	}

	return &Account{
		Shard:  shard,
		Realm:  realm,
		Number: number,
	}, nil
}
