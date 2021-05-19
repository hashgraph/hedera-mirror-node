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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/stretchr/testify/assert"
	"testing"
)

func exampleOperation() *Operation {
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
		Amount: &Amount{Value: int64(400)},
	}
}

func expectedOperation() *types.Operation {
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
		Amount: &types.Amount{
			Value:    "400",
			Currency: config.CurrencyHbar,
			Metadata: nil,
		},
	}
}

func TestToRosettaOperation(t *testing.T) {
	// when:
	rosettaOperation := exampleOperation().ToRosetta()

	// then:
	assert.Equal(t, expectedOperation(), rosettaOperation)
}
