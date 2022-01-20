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
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/stretchr/testify/assert"
)

var (
	entityId = domain.MustDecodeEntityId(100)
	status   = "pending"
)

func exampleTransaction() *Transaction {
	return &Transaction{
		EntityId: &entityId,
		Hash:     "somehash",
		Operations: []*Operation{
			{
				Index:   1,
				Type:    "transfer",
				Status:  status,
				Account: Account{domain.EntityId{}},
				Amount:  &HbarAmount{Value: int64(400)},
			},
		},
	}
}

func expectedTransaction() *types.Transaction {
	return &types.Transaction{
		TransactionIdentifier: &types.TransactionIdentifier{Hash: "somehash"},
		Operations: []*types.Operation{
			{
				OperationIdentifier: &types.OperationIdentifier{Index: 1},
				Type:                "transfer",
				Status:              &status,
				Account:             &types.AccountIdentifier{Address: "0.0.0"},
				Amount:              &types.Amount{Value: "400", Currency: CurrencyHbar},
			},
		},
		Metadata: map[string]interface{}{
			"entity_id": entityId.String(),
		},
	}
}

func TestToRosettaTransaction(t *testing.T) {
	// given
	expected := expectedTransaction()

	// when
	actual := exampleTransaction().ToRosetta()

	// then
	assert.Equal(t, expected, actual)
}

func TestToRosettaTransactionNoEntityId(t *testing.T) {
	// given
	expected := expectedTransaction()
	expected.Metadata = nil

	// when
	transaction := exampleTransaction()
	transaction.EntityId = nil
	actual := transaction.ToRosetta()

	// then
	assert.Equal(t, expected, actual)
}
