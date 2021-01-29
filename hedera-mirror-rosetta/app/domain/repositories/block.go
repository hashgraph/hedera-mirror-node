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

package repositories

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
)

// BlockRepository Interface that all BlockRepository structs must implement
type BlockRepository interface {
	FindByIndex(index int64) (*types.Block, *rTypes.Error)
	FindByHash(hash string) (*types.Block, *rTypes.Error)
	FindByIdentifier(index int64, hash string) (*types.Block, *rTypes.Error)
	RetrieveGenesis() (*types.Block, *rTypes.Error)
	RetrieveLatest() (*types.Block, *rTypes.Error)
}
