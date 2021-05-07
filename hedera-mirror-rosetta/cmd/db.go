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

package main

import (
	"fmt"
	"log"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/types"
	"github.com/jinzhu/gorm"
	_ "github.com/jinzhu/gorm/dialects/postgres"
)

// Establish connection to the Postgres Database
func connectToDb(dbConfig types.Db) *gorm.DB {
	connectionStr := fmt.Sprintf("host=%s port=%d user=%s dbname=%s password=%s sslmode=disable", dbConfig.Host, dbConfig.Port, dbConfig.Username, dbConfig.Name, dbConfig.Password)
	db, err := gorm.Open("postgres", connectionStr)
	if err != nil {
		log.Fatal(err)
	}
	log.Println("Successfully connected to Database")

	return db
}
