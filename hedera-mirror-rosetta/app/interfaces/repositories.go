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
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
)

// AccountRepository Interface that all AccountRepository structs must implement
type AccountRepository interface {
	RetrieveBalanceAtBlock(accountId int64, consensusEnd int64) ([]types.Amount, *rTypes.Error)
	RetrieveDissociatedTokens(accountId int64, consensusEnd int64) ([]domain.Token, *rTypes.Error)
	RetrieveTransferredTokensInBlockAfter(accountId int64, consensusTimestamp int64) (
		[]domain.Token,
		*rTypes.Error,
	)
}

// AddressBookEntryRepository Interface that all AddressBookEntryRepository structs must implement
type AddressBookEntryRepository interface {
	Entries() (*types.AddressBookEntries, *rTypes.Error)
}

// BlockRepository Interface that all BlockRepository structs must implement
type BlockRepository interface {
	FindByHash(hash string) (*types.Block, *rTypes.Error)
	FindByIdentifier(index int64, hash string) (*types.Block, *rTypes.Error)
	FindByIndex(index int64) (*types.Block, *rTypes.Error)
	RetrieveGenesis() (*types.Block, *rTypes.Error)
	RetrieveLatest() (*types.Block, *rTypes.Error)
}

// TokenRepository Interface that all TokenRepository structs must implement
type TokenRepository interface {
	Find(tokenIdStr string) (domain.Token, *rTypes.Error)
}

// TransactionRepository Interface that all TransactionRepository structs must implement
type TransactionRepository interface {
	FindByHashInBlock(identifier string, consensusStart int64, consensusEnd int64) (*types.Transaction, *rTypes.Error)
	FindBetween(start int64, end int64) ([]*types.Transaction, *rTypes.Error)
	Results() (map[int]string, *rTypes.Error)
	Types() (map[int]string, *rTypes.Error)
	TypesAsArray() ([]string, *rTypes.Error)
}
