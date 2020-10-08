/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
 *
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

package entity_id_codec

import (
	"fmt"
)

const (
	shardBits  int   = 15
	realmBits  int   = 16
	numberBits int   = 32
	shardMask  int64 = (int64(1) << shardBits) - 1
	realmMask  int64 = (int64(1) << realmBits) - 1
	numberMask int64 = (int64(1) << numberBits) - 1
)

// DecodedData returns the decoded data from the DB ID
type DecodedData struct {
	ShardNum  int64
	RealmNum  int64
	EntityNum int64
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
func Decode(encodedID int64) (*DecodedData, error) {
	if encodedID < 0 {
		return nil, fmt.Errorf("encodedID cannot be negative: %d", encodedID)
	}

	return &DecodedData{
		ShardNum:  encodedID >> (realmBits + numberBits),
		RealmNum:  (encodedID >> numberBits) & realmMask,
		EntityNum: encodedID & numberMask,
	}, nil
}
