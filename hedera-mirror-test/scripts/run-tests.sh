#!/bin/sh
set -ex

#cucumberFlags="@BalanceCheck"
#testProfile=acceptance
#subscribeThreadCount=20
#jmeterTestPlan=E2E_Subscribe_Only.jmx

echo "Running $testProfile Mirror Node tests"

set_acceptance_configs() {
    testoptions="-Dcucumber.filter.tags=$cucumberFlags"
}

set_perf_configs() {
    testoptions="-Djmeter.subscribeThreadCount=$subscribeThreadCount -Djmeter.test=$jmeterTestPlan"
}

handle_mode() {
    case $testProfile in
    acceptance)
        echo "Setting acceptance tests configs"
        set_acceptance_configs
        testProfile=acceptance-tests
        ;;
    performance)
        echo "Setting performance tests configs"
        set_perf_configs
        testProfile=performance-tests
        ;;
    *)
        echo "Test profile was '$testProfile', value must be either 'acceptance' or 'performance'"
        ;;
    esac
}

run_test() {
    handle_mode
    ./mvnw integration-test -pl hedera-mirror-test -P=$testProfile $testoptions
}

run_test

echo "Completed $testProfile Mirror Node tests"
