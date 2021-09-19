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
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/stretchr/testify/assert"
)

var zeroAccount Account

func exampleAccount() *Account {
	return &Account{domain.EntityId{}}
}

func exampleAccountWith(shard, realm, entity int64) Account {
	encoded, _ := domain.EncodeEntityId(shard, realm, entity)
	return Account{
		domain.EntityId{
			ShardNum:  shard,
			RealmNum:  realm,
			EntityNum: entity,
			EncodedId: encoded,
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
		assert.Equal(t, exampleAccountWith(tt.shard, tt.realm, tt.number), res)
		assert.Nil(t, e)
	}
}

func TestNewAccountFromEncodedIDThrows(t *testing.T) {
	// given:
	testData := int64(-1)

	// when:
	res, err := NewAccountFromEncodedID(testData)

	// then:
	assert.Equal(t, zeroAccount, res)
	assert.NotNil(t, err)
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
		actual := exampleAccountWith(tt.shard, tt.realm, tt.number)
		assert.Equal(t, tt.result, actual.String())
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
		assert.Equal(t, exampleAccountWith(tt.shard, tt.realm, tt.number), res)
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

	var zeroAccount Account

	for _, tt := range testData {
		// when:
		res, err := AccountFromString(tt.input)

		// then:
		assert.Equal(t, zeroAccount, res)
		assert.Equal(t, errors.ErrInvalidAccount, err)
	}
}
