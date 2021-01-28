/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
 *
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

type MockBlockRepository struct {
	mock.Mock
}

func (m *MockBlockRepository) FindByIndex(index int64) (*types.Block, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).(*types.Block), args.Get(1).(*rTypes.Error)
}

func (m *MockBlockRepository) FindByHash(hash string) (*types.Block, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).(*types.Block), args.Get(1).(*rTypes.Error)
}

func (m *MockBlockRepository) FindByIdentifier(index int64, hash string) (*types.Block, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).(*types.Block), args.Get(1).(*rTypes.Error)
}

func (m *MockBlockRepository) RetrieveGenesis() (*types.Block, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).(*types.Block), args.Get(1).(*rTypes.Error)
}

func (m *MockBlockRepository) RetrieveLatest() (*types.Block, *rTypes.Error) {
	args := m.Called()
	return args.Get(0).(*types.Block), args.Get(1).(*rTypes.Error)
}
