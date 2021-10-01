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
	"context"
	"errors"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	log "github.com/sirupsen/logrus"
	"gorm.io/gorm"
)

// tokenRepository struct that has connection to the Database
type tokenRepository struct {
	dbClient interfaces.DbClient
}

// NewTokenRepository creates an instance of a tokenRepository struct
func NewTokenRepository(dbClient interfaces.DbClient) interfaces.TokenRepository {
	return &tokenRepository{dbClient}
}

func (tr *tokenRepository) Find(ctx context.Context, tokenIdStr string) (domain.Token, *rTypes.Error) {
	var token domain.Token
	entityId, err := domain.EntityIdFromString(tokenIdStr)
	if err != nil {
		return token, hErrors.ErrInvalidToken
	}

	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	if err = db.First(&token, entityId.EncodedId).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return token, hErrors.ErrTokenNotFound
		}

		log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
		return token, hErrors.ErrDatabaseError
	}

	return token, nil
}
