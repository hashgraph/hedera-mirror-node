#!/bin/bash

# Define Solidity versions and output directories
SOLC_VERSIONS=("0.8.7")
CONTRACT_PATH="src/test/solidity_historical/EvmCodesHistorical.sol"
OUTPUT_DIRS=("build/generated/sources/web3j/test/java")

brew install jq
brew install solc-select

# Uncomment this line when we have an official release ->
#curl -L get.web3j.io | sh && source ~/.web3j/source.sh

# Remove the following 2 lines when we have an official release ->
chmod +x ./install_web3j.sh
./install_web3j.sh

# Loop over Solidity versions
for i in "${!SOLC_VERSIONS[@]}"; do
  # Install solc-select version
  solc-select install "${SOLC_VERSIONS[$i]}"

  # Use specific solc version
  solc-select use "${SOLC_VERSIONS[$i]}"

  # Compile Solidity contract with OpenZeppelin imports path
  solc --base-path . --allow-paths node_modules --abi --bin --overwrite -o build/temp_contracts "${CONTRACT_PATH}"

  # Generate Java files using web3j
  web3j generate solidity \
    -b build/temp_contracts/EvmCodesHistorical.bin \
    -a build/temp_contracts/EvmCodesHistorical.abi \
    -o "${OUTPUT_DIRS[$i]}" \
    -p com.hedera.mirror.web3.web3j.generated \
    -B

done

rm -rf build/temp_contracts