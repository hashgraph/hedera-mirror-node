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

package repository

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/stretchr/testify/mock"
)

type MockTransactionRepository struct {
	mock.Mock
}

func (m *MockTransactionRepository) FindByHashInBlock(
	identifier string,
	consensusStart int64,
	consensusEnd int64,
) (*types.Transaction, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).(*types.Transaction), args.Get(1).(*rTypes.Error)
}

func (m *MockTransactionRepository) FindBetween(start int64, end int64) ([]*types.Transaction, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).([]*types.Transaction), args.Get(1).(*rTypes.Error)
}

func (m *MockTransactionRepository) Types() (map[int]string, *rTypes.Error) {
	panic("implement me")
}

func (m *MockTransactionRepository) TypesAsArray() ([]string, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).([]string), args.Get(1).(*rTypes.Error)
}

func (m *MockTransactionRepository) Results() (map[int]string, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).(map[int]string), args.Get(1).(*rTypes.Error)
}
