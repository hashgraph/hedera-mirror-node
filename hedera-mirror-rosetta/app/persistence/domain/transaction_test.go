/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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
 */

package domain

import (
	"fmt"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestTransactionTableName(t *testing.T) {
	assert.Equal(t, "transaction", Transaction{}.TableName())
}

func TestItemizedTransferSliceScan(t *testing.T) {
	// given
	bytes := []byte(`[
{"amount": -20, "entity_id": 101, "is_approval": true},
{"amount": 10, "entity_id": 102, "is_approval": false},
{"amount": 10, "entity_id": 103, "is_approval": false}
]`)
	expected := ItemizedTransferSlice{
		{Amount: -20, EntityId: EntityId{EntityNum: 101, EncodedId: 101}, IsApproval: true},
		{Amount: 10, EntityId: EntityId{EntityNum: 102, EncodedId: 102}},
		{Amount: 10, EntityId: EntityId{EntityNum: 103, EncodedId: 103}},
	}

	// when
	actual := ItemizedTransferSlice{}
	err := actual.Scan(bytes)

	// then
	assert.NoError(t, err)
	assert.Equal(t, expected, actual)
}

func TestItemizedTransferSliceScanThrows(t *testing.T) {
	inputs := []interface{}{
		"not byte array",
		[]byte(`[{"amount": -20, "entity_id": 101, "is_approval": true]`), // invalid json string
	}

	for _, input := range inputs {
		t.Run(fmt.Sprintf("%s", input), func(t *testing.T) {
			actual := ItemizedTransferSlice{}
			err := actual.Scan(input)
			assert.Error(t, err)
		})
	}
}

func TestItemizedTransferSliceValue(t *testing.T) {
	// given
	transfer := ItemizedTransferSlice{
		{Amount: -20, EntityId: EntityId{EntityNum: 101, EncodedId: 101}, IsApproval: true},
		{Amount: 10, EntityId: EntityId{EntityNum: 102, EncodedId: 102}},
		{Amount: 10, EntityId: EntityId{EntityNum: 103, EncodedId: 103}},
	}
	expected := `[
{"amount":-20, "entity_id": 101, "is_approval": true},
{"amount":10, "entity_id": 102, "is_approval": false},
{"amount":10, "entity_id": 103, "is_approval": false}
]`
	expected = strings.ReplaceAll(strings.ReplaceAll(expected, " ", ""), "\n", "")

	// when
	actual, err := transfer.Value()

	// then
	assert.NoError(t, err)
	assert.IsType(t, []byte{}, actual)
	bytes, _ := actual.([]byte)
	assert.Equal(t, expected, string(bytes))
}

func TestItemizedTransferSliceValueEmpty(t *testing.T) {
	actual, err := ItemizedTransferSlice{}.Value()
	assert.NoError(t, err)
	assert.Nil(t, actual)
}
