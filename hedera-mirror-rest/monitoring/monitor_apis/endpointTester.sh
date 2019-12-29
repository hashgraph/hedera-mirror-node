#! /usr/bin/env bash
# Script to allow OPS to be able to hit monitor api endpoint continuously in between intervals
# The /status endpoint is hit with headers returned showing status
# /status endpoint gets results for all servers available
host=
looptimes=1
sleepseconds=5
individual=false

while getopts “h:l:s:i” OPTION; do
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
    i)
        individual=$OPTARG
        ;;
    ?)
        echo "Please call script as follows './endpointTester.sh -h <host> -l <looptimes> -s <sleepseconds>' -h is the only mandatory param"
        exit
        ;;
    esac
done

if [ "$#" -lt 1 ] || [[ -z $host ]]; then
    echo "Endpoint host parameter is missing. Please call script as follows './endpointTester.sh -h <host>'"
    exit 1
fi

url=http://$host/api/v1/status

function hit_status() {
    echo "Call ${url} for status"
    result=$(curl --silent -I ${url} --stderr - | grep HTTP)
    if [[ $result == *"200"* ]]; then
        echo "Success : 200 returned"
    else
        echo "Failure: Non 200 returned"
    fi
}

function hit_status_id() {
    # read servers from list
    servers=($(cat ./config/serverlist.json | jq --raw-output '.servers[] .name'))

    for s in "${servers[@]}"; do
        # form single server url
        indvurl="$url/$s"
        echo "Call ${indvurl} for status"

        result=$(curl --silent ${indvurl} --stderr - | jq '.httpCode')

        if [[ $result == "200" ]]; then
            echo "Success : ${result} returned"
        else
            echo "Failure: ${result} returned"
        fi
    done
}

for ((i = 1; i <= looptimes; ++i)); do

    if $individual; then
        echo "Multi endpoint scenario chosen"
        hit_status_id
    else
        echo "Single endpoint scenario chosen"
        hit_status
    fi

    echo "Run ${i}, wait ${sleepseconds} seconds before next run"
    sleep ${sleepseconds}

done
