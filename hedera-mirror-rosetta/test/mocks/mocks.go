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

package mocks

import (
	"fmt"
	"reflect"
	"regexp"
	"strings"
	"testing"

	"database/sql/driver"
	"github.com/DATA-DOG/go-sqlmock"
	"github.com/iancoleman/strcase"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

var sqlNamedParamRe = regexp.MustCompile(`(@[^ ,)"'\n]+)`)

// replaces named parameter to indexed format $1, $2, ...
var queryMatcher = sqlmock.QueryMatcherFunc(func(expectedSQL, actualSQL string) error {
	namedParams := sqlNamedParamRe.FindAllString(expectedSQL, -1)

	index := 1
	namedIndexes := make(map[string]string)
	for _, name := range namedParams {
		if _, ok := namedIndexes[name]; !ok {
			namedIndexes[name] = fmt.Sprintf("$%d", index)
			index++
		}
	}

	for name, indexStr := range namedIndexes {
		expectedSQL = strings.ReplaceAll(expectedSQL, name, indexStr)
	}

	return sqlmock.QueryMatcherRegexp.Match(regexp.QuoteMeta(expectedSQL), actualSQL)
})

// DatabaseMock returns a mocked gorm.DB connection and Sqlmock for mocking actual queries
func DatabaseMock(t *testing.T) (*gorm.DB, sqlmock.Sqlmock) {
	db, mock, err := sqlmock.New(sqlmock.QueryMatcherOption(queryMatcher))
	if err != nil {
		t.Errorf("Error: '%s'", err)
	}

	dialector := postgres.New(postgres.Config{
		Conn:                 db,
		DriverName:           "postgres",
		DSN:                  "sqlmock_db_0",
		PreferSimpleProtocol: true,
	})
	gdb, err := gorm.Open(dialector, &gorm.Config{})
	if err != nil {
		t.Errorf("Error: '%s'", err)
	}
	return gdb, mock
}

// GetFieldsNamesToSnakeCase returns an array of snake-cased fields names
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
