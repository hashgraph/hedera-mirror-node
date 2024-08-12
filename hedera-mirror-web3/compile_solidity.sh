#!/bin/bash
set -euo pipefail

# Define Solidity versions and output directories
SOLC_VERSIONS=("0.8.7")
CONTRACT_PATH="./src/test/solidity_historical/EvmCodesHistorical.sol"
OUTPUT_DIRS=("./build/generated/sources/web3j/test/java")

#brew install jq
#brew install solc-select

chmod +x ./install_solc_select.sh
./install_solc_select.sh

# Uncomment this line when we have an official release ->
#curl -L get.web3j.io | sh && source ~/.web3j/source.sh

# Remove the following 2 lines when we have an official release ->
# Detect the operating system
OS=$(uname)

case $OS in
    "Linux")
        chmod +x ./install_web3j_linux.sh
        ./install_web3j_linux.sh
        ;;
    "Darwin")
        chmod +x ./install_web3j_mac.sh
        ./install_web3j_mac.sh
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

  # Compile Solidity contract with OpenZeppelin imports path
  solc --base-path . --allow-paths node_modules --abi --bin --overwrite -o build/temp_contracts "${CONTRACT_PATH}"

  # Generate Java files using web3j
  $HOME/.web3j/web3j generate solidity \
    -b build/temp_contracts/EvmCodesHistorical.bin \
    -a build/temp_contracts/EvmCodesHistorical.abi \
    -o "${OUTPUT_DIRS[$i]}" \
    -p com.hedera.mirror.web3.web3j.generated \
    -B

done

echo "The java directory: "
ls -l ./build/generated/sources/web3j/test/java/com/hedera/mirror/web3/web3j/generated

echo "The generated file:"
cat ./build/generated/sources/web3j/test/java/com/hedera/mirror/web3/web3j/generated/EvmCodesHistorical.java

echo "Verify of the locations are the same"
ls -l /runner/_work/hedera-mirror-node/hedera-mirror-node/hedera-mirror-web3/build/generated/sources/web3j/test/java/com/hedera/mirror/web3/web3j/generated/

rm -rf build/temp_contracts