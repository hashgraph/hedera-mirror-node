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

package types

import (
	"context"
	"time"

	"gorm.io/gorm"
)

type DbClient struct {
	db               *gorm.DB
	statementTimeout uint
}

func (d *DbClient) GetDb() *gorm.DB {
	return d.db
}

func (d *DbClient) GetDbWithContext(ctx context.Context) (*gorm.DB, context.CancelFunc) {
	if d.statementTimeout == 0 {
		return d.db, noop
	}

	if ctx == nil {
		ctx = context.Background()
	}

	childCtx, cancel := context.WithTimeout(ctx, time.Duration(d.statementTimeout)*time.Second)
	return d.db.WithContext(childCtx), cancel
}

func NewDbClient(db *gorm.DB, statementTimeout uint) *DbClient {
	return &DbClient{db: db, statementTimeout: statementTimeout}
}

func noop() {
	// empty cancel function
}
