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

package domain

import (
	"database/sql/driver"
	"fmt"
	"strconv"
	"strings"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools"
)

const (
	shardBits  int   = 15
	realmBits  int   = 16
	numberBits int   = 32
	shardMask  int64 = (int64(1) << shardBits) - 1
	realmMask  int64 = (int64(1) << realmBits) - 1
	numberMask int64 = (int64(1) << numberBits) - 1
)

var (
	errorEntity   = fmt.Errorf("invalid entity")
	errorEntityId = fmt.Errorf("invalid entityId")
	errorShardId  = fmt.Errorf("invalid shardId")
	errorRealmId  = fmt.Errorf("invalid realmId")
)

// EntityId returns the decoded data from the DB ID
type EntityId struct {
	ShardNum  int64
	RealmNum  int64
	EntityNum int64
	EncodedId int64
}

func (e *EntityId) IsZero() bool {
	return e.EncodedId == 0
}

func (e *EntityId) Scan(value interface{}) error {
	encodedId, ok := value.(int64)
	if !ok {
		return fmt.Errorf("failed to unmarshal EntityId value %v", value)
	}

	var err error
	if *e, err = DecodeEntityId(encodedId); err != nil {
		return err
	}

	return nil
}

func (e *EntityId) String() string {
	return fmt.Sprintf("%d.%d.%d", e.ShardNum, e.RealmNum, e.EntityNum)
}

func (e *EntityId) UnmarshalJSON(data []byte) error {
	str := tools.SafeUnquote(string(data))

	var entityId EntityId
	var err error
	if strings.Contains(str, ".") {
		entityId, err = EntityIdFromString(str)
	} else {
		var encodedId int64
		encodedId, err = strconv.ParseInt(str, 10, 64)
		if err != nil {
			return err
		}

		entityId, err = DecodeEntityId(encodedId)
	}

	if err != nil {
		return err
	}

	*e = entityId
	return nil
}

func (e EntityId) Value() (driver.Value, error) {
	if e.IsZero() {
		return nil, nil
	}

	return e.EncodedId, nil
}

// EncodeEntityId - encodes the shard, realm and entity id into a Entity DB Id
func EncodeEntityId(shardNum int64, realmNum int64, entityNum int64) (int64, error) {
	if shardNum > shardMask || shardNum < 0 ||
		realmNum > realmMask || realmNum < 0 ||
		entityNum > numberMask || entityNum < 0 {
		return 0, fmt.Errorf("invalid parameters provided for encoding: %d.%d.%d", shardNum, realmNum, entityNum)
	}
	return (entityNum & numberMask) |
		(realmNum&realmMask)<<numberBits |
		(shardNum&shardMask)<<(realmBits+numberBits), nil
}

// DecodeEntityId - decodes the Entity DB id into Account struct
func DecodeEntityId(encodedID int64) (EntityId, error) {
	if encodedID < 0 {
		return EntityId{}, fmt.Errorf("encodedID cannot be negative: %d", encodedID)
	}

	return EntityId{
		ShardNum:  encodedID >> (realmBits + numberBits),
		RealmNum:  (encodedID >> numberBits) & realmMask,
		EntityNum: encodedID & numberMask,
		EncodedId: encodedID,
	}, nil
}

func EntityIdFromString(entityId string) (EntityId, error) {
	inputs := strings.Split(entityId, ".")
	if len(inputs) != 3 {
		return EntityId{}, errorEntity
	}

	shardNum, err := tools.ToInt64(inputs[0])
	if err != nil {
		return EntityId{}, errorShardId
	}

	realmNum, err := tools.ToInt64(inputs[1])
	if err != nil {
		return EntityId{}, errorRealmId
	}

	entityNum, err := tools.ToInt64(inputs[2])
	if err != nil {
		return EntityId{}, errorEntityId
	}

	encodedId, err := EncodeEntityId(shardNum, realmNum, entityNum)
	if err != nil {
		return EntityId{}, err
	}

	return EntityId{
		ShardNum:  shardNum,
		RealmNum:  realmNum,
		EntityNum: entityNum,
		EncodedId: encodedId,
	}, nil
}

func MustDecodeEntityId(encodedId int64) EntityId {
	entityId, err := DecodeEntityId(encodedId)
	if err != nil {
		panic(err)
	}

	return entityId
}
