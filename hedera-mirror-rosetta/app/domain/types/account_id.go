/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
	"encoding/hex"
	"strings"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

const (
	ed25519PublicKeySize   = 32
	secp256k1PublicKeySize = 33
)

type AccountId struct {
	accountId domain.EntityId
	aliasKey  *hedera.PublicKey
	alias     []byte
	curveType types.CurveType
}

func (a AccountId) GetAlias() []byte {
	if !a.HasAlias() {
		return nil
	}

	alias := make([]byte, len(a.alias))
	copy(alias, a.alias)
	return alias
}

func (a AccountId) GetCurveType() types.CurveType {
	return a.curveType
}

func (a AccountId) GetId() int64 {
	return a.accountId.EncodedId
}

func (a AccountId) HasAlias() bool {
	return len(a.alias) != 0
}

func (a AccountId) IsZero() bool {
	return !a.HasAlias() && a.accountId.EncodedId == 0
}

func (a AccountId) String() string {
	if a.HasAlias() {
		return hex.EncodeToString(a.alias)
	}
	return a.accountId.String()
}

func (a AccountId) ToRosetta() *types.AccountIdentifier {
	return &types.AccountIdentifier{Address: a.String()}
}

func (a AccountId) ToSdkAccountId() hedera.AccountID {
	return hedera.AccountID{
		Shard:    uint64(a.accountId.ShardNum),
		Realm:    uint64(a.accountId.RealmNum),
		Account:  uint64(a.accountId.EntityNum),
		AliasKey: a.aliasKey,
	}
}

func NewAccountIdFromString(address string, shard, realm int64) (AccountId, error) {
	if strings.Contains(address, ".") {
		entityId, err := domain.EntityIdFromString(address)
		if err != nil {
			return AccountId{}, err
		}
		return AccountId{accountId: entityId}, nil
	}
	alias, err := hex.DecodeString(address)
	if err != nil {
		return AccountId{}, err
	}
	return NewAccountIdFromAlias(alias, shard, realm)
}

func NewAccountIdFromAlias(alias []byte, shard, realm int64) (AccountId, error) {
	aliasKey, err := hedera.PublicKeyFromBytes(alias)
	if err != nil {
		return AccountId{}, err
	}
	var curveType types.CurveType
	switch len(alias) {
	case ed25519PublicKeySize:
		curveType = types.Edwards25519
	case secp256k1PublicKeySize:
		curveType = types.Secp256k1
	}
	return AccountId{
		accountId: domain.EntityId{ShardNum: shard, RealmNum: realm},
		alias:     alias,
		aliasKey:  &aliasKey,
		curveType: curveType,
	}, nil
}

func NewAccountIdFromEntityId(accountId domain.EntityId) AccountId {
	return AccountId{accountId: accountId}
}

func NewAccountIdFromSdkAccountId(accountId hedera.AccountID) (AccountId, error) {
	var alias []byte
	var entityId domain.EntityId
	var curveType types.CurveType
	if accountId.AliasKey != nil {
		alias = accountId.AliasKey.BytesRaw()
		entityId = domain.EntityId{ShardNum: int64(accountId.Shard), RealmNum: int64(accountId.Realm)}
		switch len(alias) {
		case ed25519PublicKeySize:
			curveType = types.Edwards25519
		case secp256k1PublicKeySize:
			curveType = types.Secp256k1
		}
	} else {
		var err error
		entityId, err = domain.EntityIdOf(int64(accountId.Shard), int64(accountId.Realm), int64(accountId.Account))
		if err != nil {
			return AccountId{}, err
		}
	}

	return AccountId{
		accountId: entityId,
		alias:     alias,
		aliasKey:  accountId.AliasKey,
		curveType: curveType,
	}, nil
}
