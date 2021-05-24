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

// Transaction is domain level struct used to represent Transaction conceptual mapping in Hedera
type Transaction struct {
	Hash       string
	Operations []*Operation
	Status     string
	Type       string
}

// ToRosetta returns Rosetta type Transaction from the current domain type Transaction
func (t *Transaction) ToRosetta() *rTypes.Transaction {
	operations := make([]*rTypes.Operation, len(t.Operations))
	for i, o := range t.Operations {
		operations[i] = o.ToRosetta()
	}

	rTransaction := &rTypes.Transaction{
		TransactionIdentifier: &rTypes.TransactionIdentifier{Hash: t.Hash},
		Operations:            operations,
	}
	return rTransaction
}
