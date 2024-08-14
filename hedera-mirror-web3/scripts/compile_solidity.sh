#!/bin/bash
set -euo pipefail

# Define Solidity versions and output directories
SOLC_VERSIONS=("0.8.7")
OUTPUT_DIR=("./build/generated/sources/web3j/test/java")

chmod +x ./scripts/install_solc_select.sh
./scripts/install_solc_select.sh

# Uncomment this line when we have an official release ->
#curl -L get.web3j.io | sh && source ~/.web3j/source.sh

# Detect the operating system
OS=$(uname)

case $OS in
    "Linux"|"Darwin")
        chmod +x ./scripts/install_web3j.sh
        ./scripts/install_web3j.sh
        ;;
    *)
        echo "Unsupported OS: $OS"
        ;;
esac

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
    solc --base-path . --allow-paths node_modules --abi --bin --overwrite -o build/temp_contracts "$contract_path"

    # Generate Java files using web3j
    $HOME/.web3j/web3j generate solidity \
      -b "build/temp_contracts/${contract_name}.bin" \
      -a "build/temp_contracts/${contract_name}.abi" \
      -o "$OUTPUT_DIR" \
      -p "com.hedera.mirror.web3.web3j.generated" \
      -B
  done
done

rm -rf build/temp_contracts