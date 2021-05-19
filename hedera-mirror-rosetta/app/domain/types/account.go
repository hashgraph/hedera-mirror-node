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

package types

import (
	"fmt"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
)

// Account is domain level struct used to represent Rosetta Account
type Account struct {
	entityid.EntityId
}

// NewAccountFromEncodedID - creates new instance of Account struct
func NewAccountFromEncodedID(encodedID int64) (*Account, error) {
	entityId, err := entityid.Decode(encodedID)
	if err != nil {
		return nil, err
	}

	return &Account{*entityId}, err
}

// ComputeEncodedID - returns the encoded ID from the Shard, Realm and Number
func (a *Account) ComputeEncodedID() (int64, error) {
	return entityid.Encode(a.ShardNum, a.RealmNum, a.EntityNum)
}

// String - returns the string representation of the account
func (a *Account) String() string {
	return fmt.Sprintf("%d.%d.%d", a.ShardNum, a.RealmNum, a.EntityNum)
}

// ToRosetta returns Rosetta type Account from the current domain type Account
func (a *Account) ToRosetta() *rTypes.AccountIdentifier {
	return &rTypes.AccountIdentifier{
		Address: a.String(),
	}
}

// AccountFromString populates domain type Account from String Account
func AccountFromString(account string) (*Account, *rTypes.Error) {
	entityId, err := entityid.FromString(account)
	if err != nil {
		return nil, errors.ErrInvalidAccount
	}
	return &Account{*entityId}, nil
}
