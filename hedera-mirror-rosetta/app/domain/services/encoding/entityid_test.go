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
	"encoding/json"
	"fmt"
	"math"
	"reflect"
	"testing"

	log "github.com/sirupsen/logrus"
	"github.com/stretchr/testify/assert"
)

var invalidEntityIdStrs = []string{
	"abc",
	"a.0.0",
	"0.b.0",
	"0.0.c",
	"-1.0.0",
	"0.-1.0",
	"0.0.-1",
}

func TestEntityIdString(t *testing.T) {
	entityId := EntityId{
		ShardNum:  0,
		RealmNum:  0,
		EntityNum: 7,
		EncodedId: 7,
	}

	assert.Equal(t, "0.0.7", entityId.String())
}

type E struct {
	EntityId EntityId `json:"entity_id"`
}

func TestEntityIdUnmarshalJSON(t *testing.T) {
	var tests = []struct {
		entity   string
		expected EntityId
	}{
		{
			entity: "0",
			expected: EntityId{
				ShardNum:  0,
				RealmNum:  0,
				EntityNum: 0,
				EncodedId: 0,
			},
		},
		{
			entity: "\"0.0.0\"",
			expected: EntityId{
				ShardNum:  0,
				RealmNum:  0,
				EntityNum: 0,
				EncodedId: 0,
			},
		},
		{
			entity: "10",
			expected: EntityId{
				ShardNum:  0,
				RealmNum:  0,
				EntityNum: 10,
				EncodedId: 10,
			},
		},
		{
			entity: "\"0.0.10\"",
			expected: EntityId{
				ShardNum:  0,
				RealmNum:  0,
				EntityNum: 10,
				EncodedId: 10,
			},
		},
		{
			entity: "4294967295",
			expected: EntityId{
				ShardNum:  0,
				RealmNum:  0,
				EntityNum: 4294967295,
				EncodedId: 4294967295,
			},
		},
		{
			entity: "\"0.0.4294967295\"",
			expected: EntityId{
				ShardNum:  0,
				RealmNum:  0,
				EntityNum: 4294967295,
				EncodedId: 4294967295,
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.entity, func(t *testing.T) {
			input := fmt.Sprintf("{\"entity_id\": %s}", tt.entity)

			e := &E{}
			err := json.Unmarshal([]byte(input), e)

			assert.NoError(t, err)
			assert.Equal(t, tt.expected, e.EntityId)
		})
	}
}

func TestEntityIdUnmarshalJSONThrows(t *testing.T) {
	strs := make([]string, 0)
	for _, invalid := range invalidEntityIdStrs {
		strs = append(strs, fmt.Sprintf("\"%s\"", invalid))
	}

	strs = append(strs, "-1")

	for _, str := range strs {
		t.Run(str, func(t *testing.T) {
			input := fmt.Sprintf("{\"entity_id\": %s}", str)

			e := &E{}
			err := json.Unmarshal([]byte(input), e)

			assert.Error(t, err)
		})
	}
}

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
		res, err := Encode(tt.shard, tt.realm, tt.number)

		assert.NoError(t, err)
		assert.Equal(t, tt.expected, res)
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
		res, err := Encode(tt.shard, tt.realm, tt.number)
		assert.Error(t, err)
		assert.Equal(t, int64(0), res)
	}
}

func TestEntityIdFromString(t *testing.T) {
	var testData = []struct {
		expected *EntityId
		entity   string
	}{
		{&EntityId{
			ShardNum:  0,
			RealmNum:  0,
			EntityNum: 0,
			EncodedId: 0,
		}, "0.0.0"},
		{&EntityId{
			ShardNum:  0,
			RealmNum:  0,
			EntityNum: 10,
			EncodedId: 10,
		}, "0.0.10"},
		{&EntityId{
			ShardNum:  0,
			RealmNum:  0,
			EntityNum: 4294967295,
			EncodedId: 4294967295,
		}, "0.0.4294967295"},
	}

	for _, tt := range testData {
		res, err := FromString(tt.entity)
		assert.Nil(t, err)
		assert.Equal(t, tt.expected, res)
	}
}

func TestEntityIdFromStringThrows(t *testing.T) {
	for _, tt := range invalidEntityIdStrs {
		res, err := FromString(tt)
		assert.Nil(t, res)
		assert.Error(t, err)
	}
}

func TestEntityIdDecoding(t *testing.T) {
	var testData = []struct {
		input    int64
		expected *EntityId
	}{
		{0, &EntityId{
			ShardNum:  0,
			RealmNum:  0,
			EntityNum: 0,
			EncodedId: 0,
		}},
		{10, &EntityId{
			ShardNum:  0,
			RealmNum:  0,
			EntityNum: 10,
			EncodedId: 10,
		}},
		{4294967295, &EntityId{
			ShardNum:  0,
			RealmNum:  0,
			EntityNum: 4294967295,
			EncodedId: 4294967295,
		}},
		{2814792716779530, &EntityId{
			ShardNum:  10,
			RealmNum:  10,
			EntityNum: 10,
			EncodedId: 2814792716779530,
		}},
		{9223372036854775807, &EntityId{
			ShardNum:  32767,
			RealmNum:  65535,
			EntityNum: 4294967295,
			EncodedId: 9223372036854775807,
		}},
		{9223090561878065152, &EntityId{
			ShardNum:  32767,
			RealmNum:  0,
			EntityNum: 0,
			EncodedId: 9223090561878065152,
		}},
	}

	for _, tt := range testData {
		res, err := Decode(tt.input)

		assert.Nil(t, err)
		assert.Equal(t, tt.expected, res)
	}
}

func TestEntityIdDecodeThrows(t *testing.T) {
	res, err := Decode(-1)
	assert.Nil(t, res)
	assert.Error(t, err)
}

type foobar struct {
	Value int
}

func demo(records ...interface{}) {
	for _, r := range records {
		rt := reflect.TypeOf(r)
		switch rt.Kind() {
		case reflect.Slice:
			v := reflect.ValueOf(r)

			for i := 0; i < v.Len(); i++ {
				log.Infof("value at %d - %+v", i, v.Index(i))
			}
			log.Info("slice")
		case reflect.Array:
			log.Info("Array")
		default:
			log.Infof("value - %+v", r)
		}
	}
}

func TestReflection(t *testing.T) {
	f1 := &foobar{10}
	f2 := []*foobar{
		{Value: 1},
		{Value: 2},
	}

	demo(f1, f2)
}
