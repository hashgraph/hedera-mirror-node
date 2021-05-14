#!/bin/bash

function run_offline_mode() {
  supervisord --configuration supervisord-offline.conf
}

function run_online_mode() {
  supervisord
}

function main() {
  if [[ -n "$NETWORK" ]]; then
    export HEDERA_MIRROR_IMPORTER_NETWORK=$NETWORK
    export HEDERA_MIRROR_ROSETTA_NETWORK=$NETWORK
  fi

  case $MODE in
    "offline")
      run_offline_mode
    ;;
    *)
      run_online_mode
    ;;
  esac
}

main
