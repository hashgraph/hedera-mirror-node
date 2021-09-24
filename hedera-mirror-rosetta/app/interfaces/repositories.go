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

package interfaces

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
)

// AccountRepository Interface that all AccountRepository structs must implement
type AccountRepository interface {
	RetrieveBalanceAtBlock(ctx context.Context, accountId, consensusEnd int64) ([]types.Amount, *rTypes.Error)
	RetrieveEverOwnedTokensByBlockAfter(ctx context.Context, accountId, consensusEnd int64) (
		[]domain.Token,
		*rTypes.Error,
	)
}

// AddressBookEntryRepository Interface that all AddressBookEntryRepository structs must implement
type AddressBookEntryRepository interface {
	Entries(ctx context.Context) (*types.AddressBookEntries, *rTypes.Error)
}

// BlockRepository Interface that all BlockRepository structs must implement
type BlockRepository interface {
	FindByHash(ctx context.Context, hash string) (*types.Block, *rTypes.Error)
	FindByIdentifier(ctx context.Context, index int64, hash string) (*types.Block, *rTypes.Error)
	FindByIndex(ctx context.Context, index int64) (*types.Block, *rTypes.Error)
	RetrieveGenesis(ctx context.Context) (*types.Block, *rTypes.Error)
	RetrieveLatest(ctx context.Context) (*types.Block, *rTypes.Error)
}

// TokenRepository Interface that all TokenRepository structs must implement
type TokenRepository interface {
	Find(ctx context.Context, tokenIdStr string) (domain.Token, *rTypes.Error)
}

// TransactionRepository Interface that all TransactionRepository structs must implement
type TransactionRepository interface {
	FindByHashInBlock(ctx context.Context, hash string, consensusStart, consensusEnd int64) (
		*types.Transaction,
		*rTypes.Error,
	)
	FindBetween(ctx context.Context, start, end int64) ([]*types.Transaction, *rTypes.Error)
	Results(ctx context.Context) (map[int]string, *rTypes.Error)
	Types(ctx context.Context) (map[int]string, *rTypes.Error)
	TypesAsArray(ctx context.Context) ([]string, *rTypes.Error)
}
