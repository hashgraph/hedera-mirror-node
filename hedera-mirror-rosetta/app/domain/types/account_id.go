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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/tools"
	"github.com/hashgraph/hedera-protobufs-go/services"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/pkg/errors"
	"google.golang.org/protobuf/proto"
)

type AccountId struct {
	accountId    domain.EntityId
	alias        []byte
	aliasKey     *hedera.PublicKey
	curveType    types.CurveType
	networkAlias []byte
}

// GetAlias returns the public key raw bytes as the account alias if the alias exists
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

// GetNetworkAlias returns the serialized Key message bytes of the account if there is an alias
func (a AccountId) GetNetworkAlias() []byte {
	return a.networkAlias
}

func (a AccountId) HasAlias() bool {
	return len(a.alias) != 0
}

func (a AccountId) IsZero() bool {
	return !a.HasAlias() && a.accountId.EncodedId == 0
}

func (a AccountId) String() string {
	if a.HasAlias() {
		return tools.SafeAddHexPrefix(hex.EncodeToString(a.alias))
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

// NewAccountIdFromString creates AccountId from the address string. If the address is in the shard.realm.num form,
// shard and realm are ignored. The only valid form of the alias address is the hex string of the raw public key bytes.
func NewAccountIdFromString(address string, shard, realm int64) (zero AccountId, _ error) {
	if strings.Contains(address, ".") {
		entityId, err := domain.EntityIdFromString(address)
		if err != nil {
			return zero, err
		}
		return AccountId{accountId: entityId}, nil
	}

	if !strings.HasPrefix(address, tools.HexPrefix) {
		return zero, errors.Errorf("Invalid Account Alias")
	}

	alias, err := hex.DecodeString(tools.SafeRemoveHexPrefix(address))
	if err != nil {
		return zero, err
	}
	return NewAccountIdFromAlias(alias, shard, realm)
}

func NewAccountIdFromAlias(alias []byte, shard, realm int64) (zero AccountId, _ error) {
	if shard < 0 || realm < 0 {
		return zero, errors.Errorf("shard and realm must be positive integers")
	}
	aliasKey, curveType, networkAlias, err := convertAlias(alias, hedera.PublicKey{})
	if err != nil {
		return zero, err
	}
	return AccountId{
		accountId:    domain.EntityId{ShardNum: shard, RealmNum: realm},
		alias:        alias,
		aliasKey:     &aliasKey,
		curveType:    curveType,
		networkAlias: networkAlias,
	}, nil
}

// NewAccountIdFromEntity creates AccountId from the entity. If the entity has a network alias, the function will parse
// it to the rosetta format
func NewAccountIdFromEntity(entity domain.Entity) (zero AccountId, _ error) {
	if len(entity.Alias) == 0 {
		return NewAccountIdFromEntityId(entity.Id), nil
	}

	var key services.Key
	if err := proto.Unmarshal(entity.Alias, &key); err != nil {
		return zero, err
	}

	var alias []byte
	var curveType types.CurveType
	switch aliasKey := key.GetKey().(type) {
	case *services.Key_Ed25519:
		alias = aliasKey.Ed25519
		curveType = types.Edwards25519
	case *services.Key_ECDSASecp256K1:
		alias = aliasKey.ECDSASecp256K1
		curveType = types.Secp256k1
	default:
		return zero, errors.Errorf("Unsupported key type in alias")
	}

	return AccountId{
		accountId:    entity.Id,
		alias:        alias,
		curveType:    curveType,
		networkAlias: entity.Alias,
	}, nil
}

func NewAccountIdFromEntityId(accountId domain.EntityId) AccountId {
	return AccountId{accountId: accountId}
}

func NewAccountIdFromSdkAccountId(accountId hedera.AccountID) (zero AccountId, _ error) {
	var alias []byte
	var entityId domain.EntityId
	var err error
	var curveType types.CurveType
	var networkAlias []byte
	if accountId.AliasKey != nil {
		alias = accountId.AliasKey.BytesRaw()
		entityId = domain.EntityId{ShardNum: int64(accountId.Shard), RealmNum: int64(accountId.Realm)}
		if _, curveType, networkAlias, err = convertAlias(nil, *accountId.AliasKey); err != nil {
			return zero, err
		}
	} else {
		entityId, err = domain.EntityIdOf(int64(accountId.Shard), int64(accountId.Realm), int64(accountId.Account))
		if err != nil {
			return zero, err
		}
	}

	return AccountId{
		accountId:    entityId,
		alias:        alias,
		aliasKey:     accountId.AliasKey,
		curveType:    curveType,
		networkAlias: networkAlias,
	}, nil
}

// convertAlias takes either an alias byte slice or an aliasKey as input, returns the aliasKey of type hedera.PublicKey,
// the curve type of the public key, and the network alias which is a serialized protobuf Key message
func convertAlias(alias []byte, aliasKey hedera.PublicKey) (hedera.PublicKey, types.CurveType, []byte, error) {
	var err error
	if len(alias) == 0 {
		alias = aliasKey.BytesRaw()
	} else {
		aliasKey, err = hedera.PublicKeyFromBytes(alias)
		if err != nil {
			return hedera.PublicKey{}, "", nil, err
		}
	}

	var curveType types.CurveType
	switch aliasSize := len(alias); aliasSize {
	case ed25519PublicKeySize:
		curveType = types.Edwards25519
	case ecdsaSecp256k1PublicKeySize:
		curveType = types.Secp256k1
	default:
		return hedera.PublicKey{}, "", nil, errors.Errorf("Invalid alias size - %d", aliasSize)
	}

	networkAlias, err := PublicKey{PublicKey: aliasKey}.ToAlias()
	if err != nil {
		return hedera.PublicKey{}, "", nil, err
	}

	return aliasKey, curveType, networkAlias, nil
}
