#! /usr/bin/env bash
# Script to allow OPS to be able to hit monitor api endpoint continuously in between intervals
# Usage is as follows
# Multi endpoint check $ ./endpointTester.sh -h 34.66.207.234:5552 -l 1 -s 2
# Single endpoint check $ ./endpointTester.sh -h 34.66.207.234:5552 -n mainnet-6551 -l 1 -s 2
# Set servername using -n option to monitor a single desired server.
# Else each of the available servers in serverlist.json will be checked
# The /status/id endpoint is hit for each call and the http code response aswell as test success count is printed
# Reponses will be in the form "Success : 200 returned, 13 / 13 Passed" or "Failure : 409 returned, 0 / 1 Passed"

host=
looptimes=1
sleepseconds=5
multiendpoint=false
servername=

while getopts “h:l:s:m:n:” OPTION; do
    case $OPTION in
    h)
        host=$OPTARG
        ;;
    l)
        looptimes=$OPTARG
        ;;
    s)
        sleepseconds=$OPTARG
        ;;
    n)
        servername=$OPTARG
        ;;
    ?)
        echo "Please call script as follows './endpointTester.sh -h <host:port> -n <servername> -l <looptimes> -s <sleepseconds>' -h is the only mandatory param"
        exit
        ;;
    esac
done

if [ "$#" -lt 1 ] || [[ -z $host ]]; then
    echo "Endpoint host parameter is missing. Please call script as follows './endpointTester.sh -h <host>'"
    exit 1
fi

#set url
url=http://$host/api/v1/status

function hit_single_status_id() {
    indvurl=$1
    echo "Call ${indvurl} for status"

    response=$(curl --silent ${indvurl} --stderr -)
    #        echo "${response}"
    httpStatus=$(echo "${response}" | jq '.httpCode')
    numPassed=$(echo "${response}" | jq '.results.numPassedTests')
    numFailed=$(echo "${response}" | jq '.results.numFailedTests')
    nomTotal=$((numPassed + numFailed))
    report="${httpStatus} returned, ${numPassed} / ${nomTotal} Passed"

    if [[ $httpStatus == "200" ]]; then
        echo "Success : ${report}"
    else
        echo "Failure: ${report}"
    fi
}

function hit_multi_status_id() {
    # read servers from list
    servers=($(cat ./config/serverlist.json | jq --raw-output '.servers[] .name'))

    for s in "${servers[@]}"; do
        # form single server url
        hit_single_status_id "$url/$s"
        #        indvurl="$url/$s"
        #        echo "Call ${indvurl} for status"
        #
        #        response=$(curl --silent ${indvurl} --stderr -)
        #        #        echo "${response}"
        #        httpStatus=$(echo "${response}" | jq '.httpCode')
        #        numPassed=$(echo "${response}" | jq '.results.numPassedTests')
        #        numFailed=$(echo "${response}" | jq '.results.numFailedTests')
        #        nomTotal=$((numPassed + numFailed))
        #        report="${httpStatus} returned, ${numPassed} / ${nomTotal} Passed"
        #
        #        if [[ $httpStatus == "200" ]]; then
        #            echo "Success : ${report}"
        #        else
        #            echo "Failure: ${report}"
        #        fi
    done
}

for ((i = 1; i <= looptimes; ++i)); do

    if [[ -z $servername ]]; then
        echo "Multi endpoint scenario chosen"
        hit_multi_status_id
    else
        echo "Single endpoint scenario chosen"
        hit_single_status_id "$url/$servername"
    fi

    echo "Run ${i}, wait ${sleepseconds} seconds before next run"
    sleep ${sleepseconds}

done
