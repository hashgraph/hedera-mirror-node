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
	"github.com/stretchr/testify/assert"
)

func exampleOperation(amount Amount) *Operation {
	return &Operation{
		Index:  1,
		Type:   "transfer",
		Status: "pending",
		Account: &Account{
			entityid.EntityId{
				ShardNum:  0,
				RealmNum:  0,
				EntityNum: 0,
			},
		},
		Amount: amount,
	}
}

func expectedOperation(amount *types.Amount) *types.Operation {
	status := "pending"
	return &types.Operation{
		OperationIdentifier: &types.OperationIdentifier{
			Index:        1,
			NetworkIndex: nil,
		},
		RelatedOperations: []*types.OperationIdentifier{},
		Type:              "transfer",
		Status:            &status,
		Account: &types.AccountIdentifier{
			Address:    "0.0.0",
			SubAccount: nil,
			Metadata:   nil,
		},
		Amount: amount,
	}
}

func TestToRosettaOperation(t *testing.T) {
	var tests = []struct {
		name     string
		input    *Operation
		expected *types.Operation
	}{
		{
			name:     "HbarAmount",
			input:    exampleOperation(hbarAmount),
			expected: expectedOperation(hbarRosettaAmount),
		},
		{
			name:     "TokenAmount",
			input:    exampleOperation(tokenAmount),
			expected: expectedOperation(tokenRosettaAmount),
		},
		{
			name:     "NilAmount",
			input:    exampleOperation(nil),
			expected: expectedOperation(nil),
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// when:
			rosettaOperation := tt.input.ToRosetta()

			// then:
			assert.Equal(t, tt.expected, rosettaOperation)
		})
	}

}
