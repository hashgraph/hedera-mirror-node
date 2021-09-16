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

package persistence

import (
	"errors"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
	entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	dbTypes "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/types"
	log "github.com/sirupsen/logrus"
	"gorm.io/gorm"
)

// tokenRepository struct that has connection to the Database
type tokenRepository struct {
	dbClient *gorm.DB
}

// NewTokenRepository creates an instance of a tokenRepository struct
func NewTokenRepository(dbClient *gorm.DB) repositories.TokenRepository {
	return &tokenRepository{dbClient}
}

func (tr *tokenRepository) Find(tokenIdStr string) (*types.Token, *rTypes.Error) {
	entityId, err := entityid.FromString(tokenIdStr)
	if err != nil {
		return nil, hErrors.ErrInvalidToken
	}

	token := &dbTypes.Token{}
	if err := tr.dbClient.First(token, entityId.EncodedId).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, hErrors.ErrTokenNotFound
		}

		log.Errorf("%s: %s", hErrors.ErrDatabaseError.Message, err)
		return nil, hErrors.ErrDatabaseError
	}

	return token.ToDomainToken()
}
