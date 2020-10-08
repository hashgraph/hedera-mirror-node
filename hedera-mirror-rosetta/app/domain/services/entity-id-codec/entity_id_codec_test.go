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
	"math"
	"testing"
)

func TestEntityIdEncoding(t *testing.T) {
	var testData = []struct {
		shard, realm, number, expected int64
	}{
		{0, 0, 0, 0},
		{0, 0, 10, 10},
		{0, 0, 4294967295, 4294967295},
		{32767, 65535, 4294967295, 9223372036854775807},
		{32767, 0, 0, 9223090561878065152},
	}

	for _, tt := range testData {
		res, _ := Encode(tt.shard, tt.realm, tt.number)
		if res != tt.expected {
			t.Errorf("Got %d, expected %d", res, tt.expected)
		}
	}
}

func TestEntityIdEncodeThrows(t *testing.T) {
	var testData = []struct {
		shard, realm, number int64
	}{
		{int64(math.MaxInt16 + 1), 0, 0},  // 1 << shardBits
		{0, int64(math.MaxUint16 + 1), 0}, // 1 << realmBits
		{0, 0, int64(math.MaxUint32 + 1)}, // 1 << numberBits
		{-1, 0, 0},
		{0, -1, 0},
		{0, 0, -1},
	}

	for _, tt := range testData {
		_, err := Encode(tt.shard, tt.realm, tt.number)
		if err == nil {
			t.Errorf("Expected error when providing invalid encoding parameters")
		}
	}
}

func TestEntityIdDecoding(t *testing.T) {
	var testData = []struct {
		input, shard, realm, number int64
	}{
		{0, 0, 0, 0},
		{10, 0, 0, 10},
		{4294967295, 0, 0, 4294967295},
		{2814792716779530, 10, 10, 10},
		{9223372036854775807, 32767, 65535, 4294967295},
		{9223090561878065152, 32767, 0, 0},
	}

	for _, tt := range testData {
		res, _ := Decode(tt.input)
		if res.ShardNum != tt.shard ||
			res.RealmNum != tt.realm ||
			res.EntityNum != tt.number {
			t.Errorf("Got %d.%d.%d, expected %d.%d.%d", res.ShardNum, res.RealmNum, res.EntityNum, tt.shard, tt.realm, tt.number)
		}
	}
}

func TestEntityIdDecodeThrows(t *testing.T) {
	_, err := Decode(-1)
	if err == nil {
		t.Errorf("Expected error when providing invalid encoding parameters")
	}
}
