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

package base

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	log "github.com/sirupsen/logrus"
)

// BaseService - Struct implementing common functionalities used by more than 1 service
type BaseService struct {
	blockRepo       repositories.BlockRepository
	transactionRepo repositories.TransactionRepository
}

// NewBaseService - Service containing common functions that are shared between other services
func NewBaseService(
	blockRepo repositories.BlockRepository,
	transactionRepo repositories.TransactionRepository,
) BaseService {
	return BaseService{
		blockRepo:       blockRepo,
		transactionRepo: transactionRepo,
	}
}

// RetrieveBlock - Retrieves Block by a given PartialBlockIdentifier
func (c *BaseService) RetrieveBlock(bIdentifier *rTypes.PartialBlockIdentifier) (*types.Block, *rTypes.Error) {
	if bIdentifier.Hash != nil && bIdentifier.Index != nil {
		h := hex.SafeRemoveHexPrefix(*bIdentifier.Hash)
		return c.blockRepo.FindByIdentifier(*bIdentifier.Index, h)
	} else if bIdentifier.Hash == nil && bIdentifier.Index != nil {
		return c.blockRepo.FindByIndex(*bIdentifier.Index)
	} else if bIdentifier.Index == nil && bIdentifier.Hash != nil {
		h := hex.SafeRemoveHexPrefix(*bIdentifier.Hash)
		return c.blockRepo.FindByHash(h)
	} else {
		log.Errorf(
			"An error occurred while retrieving Block with Index [%d] and Hash [%v]. Should not happen.",
			bIdentifier.Index,
			bIdentifier.Hash,
		)
		return nil, errors.ErrInternalServerError
	}
}

func (c *BaseService) RetrieveLatest() (*types.Block, *rTypes.Error) {
	return c.blockRepo.RetrieveLatest()
}

func (c *BaseService) RetrieveGenesis() (*types.Block, *rTypes.Error) {
	return c.blockRepo.RetrieveGenesis()
}

func (c *BaseService) FindByIdentifier(index int64, hash string) (*types.Block, *rTypes.Error) {
	return c.blockRepo.FindByIdentifier(index, hash)
}

func (c *BaseService) FindByHashInBlock(
	identifier string,
	consensusStart int64,
	consensusEnd int64,
) (*types.Transaction, *rTypes.Error) {
	return c.transactionRepo.FindByHashInBlock(identifier, consensusStart, consensusEnd)
}

func (c *BaseService) FindBetween(start int64, end int64) ([]*types.Transaction, *rTypes.Error) {
	return c.transactionRepo.FindBetween(start, end)
}

func (c *BaseService) Results() (map[int]string, *rTypes.Error) {
	return c.transactionRepo.Results()
}

func (c *BaseService) TypesAsArray() ([]string, *rTypes.Error) {
	return c.transactionRepo.TypesAsArray()
}
