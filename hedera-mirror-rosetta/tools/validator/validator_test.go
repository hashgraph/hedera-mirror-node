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

package validator

import (
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestValidateOperationsSum(t *testing.T) {
	// given:
	operationDummy := newOperationDummy("100")
	operationDummyNegative := newOperationDummy("-100")
	invalidOperationDummy := newOperationDummy("-100H")

	var testData = []struct {
		operations []*types.Operation
		expected   *types.Error
	}{
		{[]*types.Operation{
			operationDummy,
			operationDummyNegative,
		}, nil},
		{[]*types.Operation{
			operationDummyNegative,
		}, errors.ErrInvalidOperationsTotalAmount},
		{[]*types.Operation{
			invalidOperationDummy,
		}, errors.ErrInvalidAmount},
		{[]*types.Operation{}, errors.ErrEmptyOperations},
		{[]*types.Operation{
			newOperationDummy("0"),
		}, errors.ErrInvalidAmount},
	}

	for _, tt := range testData {
		result := ValidateOperationsSum(tt.operations)
		assert.Equal(t, tt.expected, result)
	}
}

func newOperationDummy(amount string) *types.Operation {
	return &types.Operation{
		Amount: &types.Amount{
			Value: amount,
		},
	}
}
