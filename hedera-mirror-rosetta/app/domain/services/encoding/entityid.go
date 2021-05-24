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

package entityid

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/parse"
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

func (e *EntityId) String() string {
	return fmt.Sprintf("%d.%d.%d", e.ShardNum, e.RealmNum, e.EntityNum)
}

func (e *EntityId) UnmarshalJSON(data []byte) error {
	str := parse.SafeUnquote(string(data))

	var entityId *EntityId
	var err error
	if strings.Contains(str, ".") {
		entityId, err = FromString(str)
	} else {
		var encodedId int64
		encodedId, err = strconv.ParseInt(str, 10, 64)
		if err != nil {
			return err
		}

		entityId, err = Decode(encodedId)
	}

	if err != nil {
		return err
	}

	*e = *entityId
	return nil
}

// Encode - encodes the shard, realm and entity id into a Entity DB Id
func Encode(shardNum int64, realmNum int64, entityNum int64) (int64, error) {
	if shardNum > shardMask || shardNum < 0 ||
		realmNum > realmMask || realmNum < 0 ||
		entityNum > numberMask || entityNum < 0 {
		return 0, fmt.Errorf("Invalid parameters provided for encoding: %d.%d.%d", shardNum, realmNum, entityNum)
	}
	return (entityNum & numberMask) |
		(realmNum&realmMask)<<numberBits |
		(shardNum&shardMask)<<(realmBits+numberBits), nil
}

// Decode - decodes the Entity DB id into Account struct
func Decode(encodedID int64) (*EntityId, error) {
	if encodedID < 0 {
		return nil, fmt.Errorf("encodedID cannot be negative: %d", encodedID)
	}

	return &EntityId{
		ShardNum:  encodedID >> (realmBits + numberBits),
		RealmNum:  (encodedID >> numberBits) & realmMask,
		EntityNum: encodedID & numberMask,
		EncodedId: encodedID,
	}, nil
}

func FromString(entityId string) (*EntityId, error) {
	inputs := strings.Split(entityId, ".")
	if len(inputs) != 3 {
		return nil, errorEntity
	}

	shardNum, err := parse.ToInt64(inputs[0])
	if err != nil {
		return nil, errorShardId
	}

	realmNum, err := parse.ToInt64(inputs[1])
	if err != nil {
		return nil, errorRealmId
	}

	entityNum, err := parse.ToInt64(inputs[2])
	if err != nil {
		return nil, errorEntityId
	}

	encodedId, err := Encode(shardNum, realmNum, entityNum)
	if err != nil {
		return nil, err
	}

	return &EntityId{
		ShardNum:  shardNum,
		RealmNum:  realmNum,
		EntityNum: entityNum,
		EncodedId: encodedId,
	}, nil
}
