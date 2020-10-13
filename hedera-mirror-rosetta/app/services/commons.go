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

package services

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
	"log"
)

// Commons - Struct implementing common functionalities used by more than 1 service
type Commons struct {
	blockRepo       repositories.BlockRepository
	transactionRepo repositories.TransactionRepository
}

// NewCommons - Service containing common functions that are shared between other services
func NewCommons(blockRepo repositories.BlockRepository, transactionRepo repositories.TransactionRepository) Commons {
	return Commons{
		blockRepo:       blockRepo,
		transactionRepo: transactionRepo,
	}
}

// RetrieveBlock - Retrieves Block by a given PartialBlockIdentifier
func (c *Commons) RetrieveBlock(bIdentifier *rTypes.PartialBlockIdentifier) (*types.Block, *rTypes.Error) {
	if bIdentifier.Hash != nil && bIdentifier.Index != nil {
		h := hex.SafeRemoveHexPrefix(*bIdentifier.Hash)
		return c.blockRepo.FindByIdentifier(*bIdentifier.Index, h)
	} else if bIdentifier.Hash == nil && bIdentifier.Index != nil {
		return c.blockRepo.FindByIndex(*bIdentifier.Index)
	} else if bIdentifier.Index == nil && bIdentifier.Hash != nil {
		h := hex.SafeRemoveHexPrefix(*bIdentifier.Hash)
		return c.blockRepo.FindByHash(h)
	} else {
		log.Printf(`An error occurred while retrieving Block with Index [%d] and Hash [%v]. Should not happen.`, bIdentifier.Index, bIdentifier.Hash)
		return nil, errors.Errors[errors.InternalServerError]
	}
}
