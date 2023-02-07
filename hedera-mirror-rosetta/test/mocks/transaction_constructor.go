/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

package mocks

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/mock"
)

var (
	NilHederaTransaction *hedera.TransferTransaction
	NilOperations        types.OperationSlice
	NilSigners           []types.AccountId
)

type MockTransactionConstructor struct {
	mock.Mock
}

func (m *MockTransactionConstructor) Construct(
	ctx context.Context,
	operations types.OperationSlice,
) (interfaces.Transaction, []types.AccountId, *rTypes.Error) {
	args := m.Called(ctx, operations)
	return args.Get(0).(interfaces.Transaction), args.Get(1).([]types.AccountId),
		args.Get(2).(*rTypes.Error)
}

func (m *MockTransactionConstructor) Parse(ctx context.Context, transaction interfaces.Transaction) (
	types.OperationSlice,
	[]types.AccountId,
	*rTypes.Error,
) {
	args := m.Called(ctx, transaction)
	return args.Get(0).(types.OperationSlice), args.Get(1).([]types.AccountId), args.Get(2).(*rTypes.Error)
}

func (m *MockTransactionConstructor) Preprocess(ctx context.Context, operations types.OperationSlice) (
	[]types.AccountId,
	*rTypes.Error,
) {
	args := m.Called(ctx, operations)
	return args.Get(0).([]types.AccountId), args.Get(1).(*rTypes.Error)
}

func (m *MockTransactionConstructor) GetDefaultMaxTransactionFee(operationType string) (
	types.HbarAmount,
	*rTypes.Error,
) {
	args := m.Called(operationType)
	return args.Get(0).(types.HbarAmount), args.Get(1).(*rTypes.Error)
}
