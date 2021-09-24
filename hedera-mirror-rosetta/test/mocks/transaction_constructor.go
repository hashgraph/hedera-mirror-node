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

package mocks

import (
	"context"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/mock"
)

var (
	NilHederaTransaction *hedera.TransferTransaction
	NilOperations        []*types.Operation
	NilSigners           []hedera.AccountID
)

type MockTransactionConstructor struct {
	mock.Mock
}

func (m *MockTransactionConstructor) Construct(
	ctx context.Context,
	nodeAccountId hedera.AccountID,
	operations []*types.Operation,
	validStartNanos int64,
) (interfaces.Transaction, []hedera.AccountID, *types.Error) {
	args := m.Called(ctx, nodeAccountId, operations, validStartNanos)
	return args.Get(0).(interfaces.Transaction), args.Get(1).([]hedera.AccountID),
		args.Get(2).(*types.Error)
}

func (m *MockTransactionConstructor) Parse(ctx context.Context, transaction interfaces.Transaction) (
	[]*types.Operation,
	[]hedera.AccountID,
	*types.Error,
) {
	args := m.Called(ctx, transaction)
	return args.Get(0).([]*types.Operation), args.Get(1).([]hedera.AccountID), args.Get(2).(*types.Error)
}

func (m *MockTransactionConstructor) Preprocess(ctx context.Context, operations []*types.Operation) (
	[]hedera.AccountID,
	*types.Error,
) {
	args := m.Called(ctx, operations)
	return args.Get(0).([]hedera.AccountID), args.Get(1).(*types.Error)
}

func (m *MockTransactionConstructor) GetOperationType() string {
	return config.OperationTypeCryptoTransfer
}

func (m *MockTransactionConstructor) GetSdkTransactionType() string {
	return "TransferTransaction"
}
