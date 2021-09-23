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

package construction

import (
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

// TransactionConstructor defines the methods to construct a transaction
type TransactionConstructor interface {
	// Construct constructs a transaction from its operations
	Construct(nodeAccountId hedera.AccountID, operations []*types.Operation, validStartNanos int64) (
		interfaces.Transaction,
		[]hedera.AccountID,
		*types.Error,
	)

	// Parse parses a signed or unsigned transaction to get its operations and required signers
	Parse(transaction interfaces.Transaction) ([]*types.Operation, []hedera.AccountID, *types.Error)

	// Preprocess preprocesses the operations to get required signers
	Preprocess(operations []*types.Operation) ([]hedera.AccountID, *types.Error)
}

// embed SDK PublicKey and implement the Unmarshaler interface
type publicKey struct {
	hedera.PublicKey
}

func (pk *publicKey) UnmarshalJSON(data []byte) error {
	var err error
	pk.PublicKey, err = hedera.PublicKeyFromString(tools.SafeUnquote(string(data)))
	return err
}

func (pk *publicKey) isEmpty() bool {
	return len(pk.PublicKey.Bytes()) == 0
}
