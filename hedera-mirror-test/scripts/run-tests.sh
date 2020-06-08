#!/bin/sh
set -e

#./mvnw integration-test --projects hedera-mirror-test/ -P=$testProfile -Dcucumber.filter.tags=$cucumberFlags \
#    -Dhedera.mirror.test.acceptance.emitBackgroundMessages=$emitBackgroundMessages \
#    -Dhedera.mirror.test.acceptance.messageTimeout=$messageTimeout \
#    -Dhedera.mirror.test.acceptance.existingTopicNum=$existingTopicNum \
#    -Dhedera.mirror.test.acceptance.mirrorNodeAddress=$mirrorNodeAddress \
#    -Dhedera.mirror.test.acceptance.nodeAddress=$nodeAddress \
#    -Dhedera.mirror.test.acceptance.nodeId=$nodeId \
#    -Dhedera.mirror.test.acceptance.operatorid=$operatorid \
#    -Dhedera.mirror.test.acceptance.operatorkey=$operatorkey

emitBackgroundMessages=false
messageTimeout=30s
existingTopicNum=60393
mirrorNodeAddress=hcs.testnet.mirrornode.hedera.com:5600
nodeAddress=testnet
nodeId=0.0.3
cucumberFlags="@BalanceCheck"
testProfile=acceptance-tests

echo "Running $testProfile Mirror Node tests"

function set_acceptance_configs() {
    testoptions="-Dcucumber.filter.tags=$cucumberFlags -Dhedera.mirror.test.acceptance.emitBackgroundMessages=$emitBackgroundMessages
            -Dhedera.mirror.test.acceptance.messageTimeout=$messageTimeout
            -Dhedera.mirror.test.acceptance.existingTopicNum=$existingTopicNum
            -Dhedera.mirror.test.acceptance.mirrorNodeAddress=$mirrorNodeAddress
            -Dhedera.mirror.test.acceptance.nodeAddress=$nodeAddress
            -Dhedera.mirror.test.acceptance.nodeId=$nodeId
            -Dhedera.mirror.test.acceptance.operatorid=$operatorid
            -Dhedera.mirror.test.acceptance.operatorkey=$operatorkey"
}

function set_perf_configs() {
    testoptions=""
}

while getopts m: OPTION; do
    case $OPTION in
    m | --mode)
        echo "mode was $OPTARG"
        if [[ $OPTARG == "acceptance" ]]; then
            echo "Setting acceptance tests configs"
            set_acceptance_configs
        elif [[ $OPTARG == "performance" ]]; then
            echo "Setting performance tests configs"
            testProfile=performance-tests
            set_perf_configs
        else
            echo "Mode value must be either 'acceptance' or 'performance'"
            exit
        fi
        ;;
    ?)
        echo "Please call script as follows './run-test.sh -testoptions <testoptions>'"
        exit
        ;;
    esac
done

function run_test() {
    ./mvnw clean integration-test -pl hedera-mirror-test -P=$testProfile $testoptions
}
run_test

echo "Completed $testProfile Mirror Node tests"
