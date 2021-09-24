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

package services

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools"
)

// BaseService - Struct implementing common functionalities used by more than 1 service
type BaseService struct {
	blockRepo       interfaces.BlockRepository
	transactionRepo interfaces.TransactionRepository
}

// NewBaseService - Service containing common functions that are shared between other services
func NewBaseService(
	blockRepo interfaces.BlockRepository,
	transactionRepo interfaces.TransactionRepository,
) BaseService {
	return BaseService{
		blockRepo:       blockRepo,
		transactionRepo: transactionRepo,
	}
}

// RetrieveBlock - Retrieves Block by a given PartialBlockIdentifier
func (c *BaseService) RetrieveBlock(ctx context.Context, bIdentifier *rTypes.PartialBlockIdentifier) (
	*types.Block,
	*rTypes.Error,
) {
	if bIdentifier.Hash != nil && bIdentifier.Index != nil {
		h := tools.SafeRemoveHexPrefix(*bIdentifier.Hash)
		return c.blockRepo.FindByIdentifier(ctx, *bIdentifier.Index, h)
	} else if bIdentifier.Hash == nil && bIdentifier.Index != nil {
		return c.blockRepo.FindByIndex(ctx, *bIdentifier.Index)
	} else if bIdentifier.Index == nil && bIdentifier.Hash != nil {
		h := tools.SafeRemoveHexPrefix(*bIdentifier.Hash)
		return c.blockRepo.FindByHash(ctx, h)
	} else {
		return c.blockRepo.RetrieveLatest(ctx)
	}
}

func (c *BaseService) RetrieveGenesis(ctx context.Context) (*types.Block, *rTypes.Error) {
	return c.blockRepo.RetrieveGenesis(ctx)
}

func (c *BaseService) RetrieveLatest(ctx context.Context) (*types.Block, *rTypes.Error) {
	return c.blockRepo.RetrieveLatest(ctx)
}

func (c *BaseService) FindByIdentifier(ctx context.Context, index int64, hash string) (*types.Block, *rTypes.Error) {
	return c.blockRepo.FindByIdentifier(ctx, index, hash)
}

func (c *BaseService) FindByHashInBlock(
	ctx context.Context,
	identifier string,
	consensusStart int64,
	consensusEnd int64,
) (*types.Transaction, *rTypes.Error) {
	return c.transactionRepo.FindByHashInBlock(ctx, identifier, consensusStart, consensusEnd)
}

func (c *BaseService) FindBetween(ctx context.Context, start int64, end int64) ([]*types.Transaction, *rTypes.Error) {
	return c.transactionRepo.FindBetween(ctx, start, end)
}

func (c *BaseService) Results(ctx context.Context) (map[int]string, *rTypes.Error) {
	return c.transactionRepo.Results(ctx)
}

func (c *BaseService) TypesAsArray(ctx context.Context) ([]string, *rTypes.Error) {
	return c.transactionRepo.TypesAsArray(ctx)
}
