#!/bin/bash
set -euo pipefail

# Define Solidity versions and output directories
OUTPUT_DIR="./build/generated/sources/web3j/test/java"
SOLC_VERSIONS=("0.8.7")
TEMP_DIR="build/temp_contracts"

chmod +x ./src/main/resources/scripts/install_solc_select.sh
./src/main/resources/scripts/install_solc_select.sh
rm -rf "${TEMP_DIR}"

# Detect the operating system
OS=$(uname)

case $OS in
    "Linux"|"Darwin")
        ;;
    *)
        echo "Unsupported OS: $OS"
        exit 1
        ;;
esac

chmod +x "$HOME/.web3j/source.sh"
source $HOME/.web3j/source.sh

# Loop over Solidity versions
for i in "${!SOLC_VERSIONS[@]}"; do
  # Install solc-select version
  solc-select install "${SOLC_VERSIONS[$i]}"

  # Use specific solc version
  solc-select use "${SOLC_VERSIONS[$i]}"

  # Loop over all Solidity files in the directory
  for contract_path in ./src/test/solidity_historical/*Historical.sol; do
    contract_name=$(basename "$contract_path" .sol)

    # Compile Solidity contract
    solc --base-path . --allow-paths node_modules --abi --bin --overwrite -o ${TEMP_DIR} "$contract_path"

    # Generate Java files using web3j
    $HOME/.web3j/web3j generate solidity \
      -b "${TEMP_DIR}/${contract_name}.bin" \
      -a "${TEMP_DIR}/${contract_name}.abi" \
      -o "$OUTPUT_DIR" \
      -p "com.hedera.mirror.web3.web3j.generated" \
      -B
  done
done
