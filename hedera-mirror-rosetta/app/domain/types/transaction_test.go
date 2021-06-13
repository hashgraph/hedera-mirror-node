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
	entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/stretchr/testify/assert"
)

func exampleTransaction() *Transaction {
	return &Transaction{
		Hash: "somehash",
		Operations: []*Operation{
			{
				Index:   1,
				Type:    "transfer",
				Status:  "pending",
				Account: Account{entityid.EntityId{}},
				Amount:  &HbarAmount{Value: int64(400)},
			},
		},
	}
}

func expectedTransaction() *types.Transaction {
	status := "pending"
	return &types.Transaction{
		TransactionIdentifier: &types.TransactionIdentifier{Hash: "somehash"},
		Operations: []*types.Operation{
			{
				OperationIdentifier: &types.OperationIdentifier{Index: 1},
				RelatedOperations:   []*types.OperationIdentifier{},
				Type:                "transfer",
				Status:              &status,
				Account:             &types.AccountIdentifier{Address: "0.0.0"},
				Amount:              &types.Amount{Value: "400", Currency: config.CurrencyHbar},
			},
		},
	}
}

func TestToRosettaTransaction(t *testing.T) {
	// given:
	expectedTransaction := expectedTransaction()

	// when:
	rosettaTransaction := exampleTransaction().ToRosetta()

	// then:
	assert.Equal(t, expectedTransaction, rosettaTransaction)
}
