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
	connectionStr := fmt.Sprintf("host=%s port=%s user=%s dbname=%s password=%s sslmode=disable", dbConfig.Host, dbConfig.Port, dbConfig.Username, dbConfig.Name, dbConfig.Password)
	db, err := gorm.Open("postgres", connectionStr)
	if err != nil {
		log.Fatal(err)
	}
	log.Println("Successfully connected to Database")

	return db
}
