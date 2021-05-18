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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/parse"
)

func ValidateOperationsSum(operations []*types.Operation) *types.Error {
	if len(operations) == 0 {
		return errors.ErrEmptyOperations
	}
	var sum int64 = 0

	for _, operation := range operations {
		amount, err := parse.ToInt64(operation.Amount.Value)
		if err != nil || amount == 0 {
			return errors.ErrInvalidAmount
		}
		sum += amount
	}

	if sum != 0 {
		return errors.ErrInvalidOperationsTotalAmount
	}

	return nil
}
