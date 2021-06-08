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
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
)

// Operation is domain level struct used to represent Operation within Transaction
type Operation struct {
	Index    int64
	Type     string
	Status   string
	Account  *Account
	Amount   Amount
	Metadata map[string]interface{}
}

// ToRosetta returns Rosetta type Operation from the current domain type Operation
func (o *Operation) ToRosetta() *rTypes.Operation {
	var amount *rTypes.Amount
	if o.Amount != nil {
		amount = o.Amount.ToRosetta()
	}

	rOperation := rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{
			Index: o.Index,
		},
		RelatedOperations: []*rTypes.OperationIdentifier{},
		Type:              o.Type,
		Status:            &o.Status,
		Account:           o.Account.ToRosetta(),
		Amount:            amount,
		Metadata:          o.Metadata,
	}
	return &rOperation
}
