package main

import (
	"fmt"
	"log"
	"os"

	"github.com/jinzhu/gorm"
	_ "github.com/jinzhu/gorm/dialects/postgres"
)

// Establish connection to the Postgres Database
func connectToDb() *gorm.DB {
	hostArg := os.Getenv("HEDERA_MIRROR_ROSETTA_DB_HOST")
	dbPortArg := os.Getenv("DB_PORT")
	dbUserArg := os.Getenv("DB_USER")
	dbNameArg := os.Getenv("DB_NAME")
	dbPasswordArg := os.Getenv("DB_PASSWORD")

	connectionStr := fmt.Sprintf("host=%s port=%s user=%s dbname=%s password=%s sslmode=disable", hostArg, dbPortArg, dbUserArg, dbNameArg, dbPasswordArg)
	db, err := gorm.Open("postgres", connectionStr)
	if err != nil {
		log.Fatal(err)
	}
	log.Println("Successfully connected to Database")

	return db
}
