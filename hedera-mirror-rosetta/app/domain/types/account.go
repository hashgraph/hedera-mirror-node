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
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
)

// Account is domain level struct used to represent Rosetta Account
type Account struct {
	domain.EntityId
}

// NewAccountFromEncodedID - creates new instance of Account struct
func NewAccountFromEncodedID(encodedID int64) (Account, error) {
	entityId, err := domain.DecodeEntityId(encodedID)
	return Account{entityId}, err
}

// ToRosetta returns Rosetta type Account from the current domain type Account
func (a Account) ToRosetta() *types.AccountIdentifier {
	return &types.AccountIdentifier{Address: a.String()}
}

// AccountFromString populates domain type Account from String Account
func AccountFromString(account string) (Account, *types.Error) {
	entityId, err := domain.EntityIdFromString(account)
	if err != nil {
		return Account{}, errors.ErrInvalidAccount
	}
	return Account{entityId}, nil
}
