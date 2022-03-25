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

package construction

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
)

// BaseTransactionConstructor defines the methods to construct a transaction
type BaseTransactionConstructor interface {
	// Construct constructs a transaction from its operations
	Construct(
		ctx context.Context,
		operations types.OperationSlice,
	) (interfaces.Transaction, []types.AccountId, *rTypes.Error)

	// Parse parses a signed or unsigned transaction to get its operations and required signers
	Parse(ctx context.Context, transaction interfaces.Transaction) (
		types.OperationSlice,
		[]types.AccountId,
		*rTypes.Error,
	)

	// Preprocess preprocesses the operations to get required signers
	Preprocess(ctx context.Context, operations types.OperationSlice) ([]types.AccountId, *rTypes.Error)
}

type TransactionConstructor interface {
	BaseTransactionConstructor

	// GetDefaultMaxTransactionFee gets the default max transaction fee in hbar
	GetDefaultMaxTransactionFee(operationType string) (types.HbarAmount, *rTypes.Error)
}
