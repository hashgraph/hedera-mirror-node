package mocks

import (
	"database/sql/driver"
	"github.com/DATA-DOG/go-sqlmock"
	"github.com/iancoleman/strcase"
	"github.com/jinzhu/gorm"
	"reflect"
	"testing"
)

// DatabaseMock returns a mocked gorm.DB connection and Sqlmock for mocking actual queries
func DatabaseMock(t *testing.T) (*gorm.DB, sqlmock.Sqlmock) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Errorf("Error: '%s'", err)
	}

	gdb, err := gorm.Open("postgres", db)
	if err != nil {
		t.Errorf("Error: '%s'", err)
	}
	return gdb, mock
}

// GetFieldsToSnakeCase returns an array of snake-cased fields names
func GetFieldsNamesToSnakeCase(v interface{}) []string {
	fields := getFieldsNames(v)
	for i := 0; i < len(fields); i++ {
		fields[i] = strcase.ToSnake(fields[i])
	}
	return fields
}

// getFieldsNames returns an array of fields names using reflection
func getFieldsNames(v interface{}) []string {
	value := reflect.Indirect(reflect.ValueOf(v))
	var result []string
	for i := 0; i < value.NumField(); i++ {
		result = append(result, value.Type().Field(i).Name)
	}
	return result
}

func GetFieldsValuesAsDriverValue(v interface{}) []driver.Value {
	value := reflect.Indirect(reflect.ValueOf(v))
	var result []driver.Value
	for i := 0; i < value.NumField(); i++ {
		result = append(result, value.Field(i).Interface())
	}

	return result
}
