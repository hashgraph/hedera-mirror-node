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
	entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/stretchr/testify/assert"
	"testing"
)

func exampleAccount() *Account {
	return &Account{
		entityid.EntityId{
			ShardNum:  0,
			RealmNum:  0,
			EntityNum: 0,
		},
	}
}

func exampleAccountWith(shard, realm, entity int64) *Account {
	return &Account{
		entityid.EntityId{
			ShardNum:  shard,
			RealmNum:  realm,
			EntityNum: entity,
		},
	}
}

func expectedAccount() *types.AccountIdentifier {
	return &types.AccountIdentifier{
		Address:    "0.0.0",
		SubAccount: nil,
		Metadata:   nil,
	}
}

func expectedAccountWith(shard int64, realm int64, number int64) *Account {
	return &Account{
		entityid.EntityId{
			ShardNum:  shard,
			RealmNum:  realm,
			EntityNum: number,
		},
	}
}

func TestToRosettaAccount(t *testing.T) {
	// when:
	rosettaAccount := exampleAccount().ToRosetta()

	// then:
	assert.Equal(t, expectedAccount(), rosettaAccount)
}

func TestNewAccountFromEncodedID(t *testing.T) {
	// given:
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
		// when:
		res, e := NewAccountFromEncodedID(tt.input)

		// then:
		assert.Equal(t, expectedAccountWith(tt.shard, tt.realm, tt.number), res)
		assert.Nil(t, e)
	}
}

func TestNewAccountFromEncodedIDThrows(t *testing.T) {
	// given:
	testData := int64(-1)

	// when:
	res, err := NewAccountFromEncodedID(testData)

	// then:
	assert.Nil(t, res)
	assert.NotNil(t, err)
}

func TestComputeEncodedID(t *testing.T) {
	var testData = []struct {
		shard, realm, number, result int64
	}{
		{0, 0, 1, 1},
		{0, 1, 1, 4294967297},
		{123, 123, 123, 34621950416388219},
	}

	for _, tt := range testData {
		res, e := exampleAccountWith(tt.shard, tt.realm, tt.number).ComputeEncodedID()
		assert.Equal(t, tt.result, res)
		assert.Nil(t, e)
	}
}

func TestComputeEncodedIDThrows(t *testing.T) {
	var testData = []struct {
		shard, realm, number int64
	}{
		{-1, 123, 246},
		{123, -123, 246},
		{123, 23, -246},
	}

	for _, tt := range testData {
		res, e := exampleAccountWith(tt.shard, tt.realm, tt.number).ComputeEncodedID()
		assert.Zero(t, res)
		assert.NotNil(t, e)
	}
}

func TestAccountString(t *testing.T) {
	var testData = []struct {
		shard  int64
		realm  int64
		number int64
		result string
	}{
		{0, 0, 1, "0.0.1"},
		{0, 1, 1, "0.1.1"},
		{123, 123, 123, "123.123.123"},
	}

	for _, tt := range testData {
		res := exampleAccountWith(tt.shard, tt.realm, tt.number).String()
		assert.Equal(t, tt.result, res)
	}
}

func TestAccountFromString(t *testing.T) {
	// given:
	var testData = []struct {
		input                string
		shard, realm, number int64
	}{
		{"0.0.0", 0, 0, 0},
		{"0.0.10", 0, 0, 10},
		{"0.0.4294967295", 0, 0, 4294967295},
		{"10.10.10", 10, 10, 10},
		{"32767.65535.4294967295", 32767, 65535, 4294967295},
		{"32767.0.0", 32767, 0, 0},
	}

	for _, tt := range testData {
		// when:
		res, e := AccountFromString(tt.input)

		// then:
		assert.Equal(t, expectedAccountWith(tt.shard, tt.realm, tt.number), res)
		assert.Nil(t, e)
	}
}

func TestAccountFromStringThrows(t *testing.T) {
	// given:
	var testData = []struct {
		input string
	}{
		{"a.0.0"},
		{"0.b.0"},
		{"0.0c"},
		{"0.0.c"},
	}

	var expectedNil *Account = nil

	for _, tt := range testData {
		// when:
		res, err := AccountFromString(tt.input)

		// then:
		assert.Equal(t, expectedNil, res)
		assert.Equal(t, errors.ErrInvalidAccount, err)
	}
}
