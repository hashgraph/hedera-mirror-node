// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

contract EquivalenceContract {
    address constant HTS_PRECOMPILE_ADDRESS = address(0x167);
    address constant EXCHANGE_RATE_PRECOMPILE_ADDRESS = address(0x168);
    address constant PRNG_PRECOMPILE_ADDRESS = address(0x169);

    function makeCallWithoutAmount(address _to, bytes memory _data) external returns (bool success, bytes memory returnData) {
        (success, returnData) = _to.call(_data);
    }

    function makeCallWithAmount(address _to, bytes memory _data) external payable returns (bool success, bytes memory returnData) {
        (success, returnData) = _to.call{value: msg.value}(_data);
    }

    function makeCallWithAmountRevert(address _to, bytes memory _data) external payable returns (bool success, bytes memory returnData) {
        (success, returnData) = _to.call{value: msg.value}(_data);

        // Revert if the call was not successful
        require(success, "Call failed");
    }

    function makeStaticCall(address _to, bytes memory _data) external returns (bool success, bytes memory returnData) {
        (success, returnData) = _to.staticcall(_data);
    }

    function makeDelegateCall(address _to, bytes memory _data) external returns (bool success, bytes memory returnData) {
        (success, returnData) = _to.delegatecall(_data);
    }

    function callCodeToContractWithoutAmount(address _addr, bytes calldata _customData) external returns (bytes32 returnData) {
        assembly {
            let input := mload(0x40)
            mstore(0x40, add(input, calldatasize()))
            calldatacopy(input, _customData.offset, calldatasize())

            let output := mload(0x40)
            mstore(0x40, add(output, 0x20))

            let success := callcode(
                900000, // gas
                _addr, // target address
                0, // value
                input, // input data location
                calldatasize(), // input data size
                output, // output data location
                0x20 // size of output data expected
            )

            returnData := mload(output) // assign output value to the var
        }
    }

    function callCodeToContractWithAmount(address _addr, bytes calldata _customData) external payable returns (bytes32 returnData) {
        assembly {
            let input := mload(0x40)
            mstore(0x40, add(input, calldatasize()))
            calldatacopy(input, _customData.offset, calldatasize())

            let output := mload(0x40)
            mstore(0x40, add(output, 0x20))

            let success := callcode(
                900000, // gas
                _addr, // target address
                callvalue(), // value
                input, // input data location
                calldatasize(), // input data size
                output, // output data location
                0x20 // size of output data expected
            )

            returnData := mload(output) // assign output value to the var
        }
    }

    function getBalance(address _address) external view returns (uint256) {
        return _address.balance;
    }

    function getCodeSize(address _address) external view returns (uint256) {
        uint256 size;
        assembly {
            size := extcodesize(_address)
        }
        return size;
    }

    function getCodeHash(address _address) external view returns (bytes32) {
        bytes32 codehash;
        assembly {
            codehash := extcodehash(_address)
        }
        return codehash;
    }

    function copyCode(address _address) external view returns (bytes memory) {
        uint256 size;
        assembly {
            size := extcodesize(_address)
        }
        bytes memory code = new bytes(size);

        assembly {
            extcodecopy(_address, add(code, 32), 0, size)
        }
        return code;
    }

    function getPseudorandomSeed() external returns (bytes32 randomBytes) {
        (bool success, bytes memory result) = PRNG_PRECOMPILE_ADDRESS.call(
            abi.encodeWithSignature("getPseudorandomSeed()"));
        require(success);
        randomBytes = abi.decode(result, (bytes32));
    }

    function getPseudorandomSeedWithAmount() external payable returns (bytes32 randomBytes) {
        (bool success, bytes memory result) = PRNG_PRECOMPILE_ADDRESS.call{value: msg.value}(
            abi.encodeWithSignature("getPseudorandomSeed()"));
        require(success);
        randomBytes = abi.decode(result, (bytes32));
    }

    function exchangeRateWithoutAmount(uint256 tinycents) external returns (uint256 tinybars) {
        (bool success, bytes memory result) = EXCHANGE_RATE_PRECOMPILE_ADDRESS.call(
            abi.encodeWithSignature("tinycentsToTinybars(uint256)", tinycents));
        require(success);
        tinybars = abi.decode(result, (uint256));
    }

    function exchangeRateWithAmount(uint256 tinycents) external payable returns (uint256 tinybars) {
        (bool success, bytes memory result) = EXCHANGE_RATE_PRECOMPILE_ADDRESS.call{value: msg.value}(
            abi.encodeWithSignature("tinycentsToTinybars(uint256)", tinycents));
        require(success);
        tinybars = abi.decode(result, (uint256));
    }

    function htsCallWithoutAmount(address token) external returns (bool success, bool isToken) {
        (bool callSuccess, bytes memory result) = HTS_PRECOMPILE_ADDRESS.call(
            abi.encodeWithSignature("isToken(address)", token));
        if (callSuccess) {
            (int64 responseCode, bool isTokenFlag) = abi.decode(result, (int32, bool));
            success = responseCode == 22; // success
            isToken = success && isTokenFlag;
        }
    }

    function htsCallWithAmount(address token) external payable returns (bool success, bool isToken) {
        (bool callSuccess, bytes memory result) = HTS_PRECOMPILE_ADDRESS.call{value: msg.value}(
            abi.encodeWithSignature("isToken(address)", token));
        if (callSuccess) {
            (int64 responseCode, bool isTokenFlag) = abi.decode(result, (int32, bool));
            success = responseCode == 22; // success
            isToken = success && isTokenFlag;
        }
    }

    function htsStaticCall(address token) external returns (bool success, bool isToken) {
        (bool callSuccess, bytes memory result) = HTS_PRECOMPILE_ADDRESS.staticcall(
            abi.encodeWithSignature("isToken(address)", token));
        if (callSuccess) {
            (int64 responseCode, bool isTokenFlag) = abi.decode(result, (int32, bool));
            success = responseCode == 22; // success
            isToken = success && isTokenFlag;
        }
    }

    function htsDelegateCall(address token) external returns (bool success, bool isToken) {
        (bool callSuccess, bytes memory result) = HTS_PRECOMPILE_ADDRESS.delegatecall(
            abi.encodeWithSignature("isToken(address)", token));
        if (callSuccess) {
            (int64 responseCode, bool isTokenFlag) = abi.decode(result, (int32, bool));
            success = responseCode == 22; // success
            isToken = success && isTokenFlag;
        }
    }

    function getCurrentAddress() external returns(address){
        return address(this);
    }

    function makeCallWithoutAmountToIdentityPrecompile()  external returns (bool success, bytes memory returnData) {
        bytes memory input = abi.encode("Hello, World");

        (bool success, bytes memory returnData) = address(0x04).call(abi.encode(input));
    }

    function makeCallWithAmountToIdentityPrecompile()  external payable returns (bool success, bytes memory returnData) {
        bytes memory input = abi.encode("Hello, World");

        (bool success, bytes memory returnData) = address(0x04).call{value: msg.value}(abi.encode(input));
    }

    function makeStaticCallToIdentityPrecompile()  external returns (bool success, bytes memory returnData) {
        bytes memory input = abi.encode("Hello, World");

        (bool success, bytes memory returnData) = address(0x04).staticcall(abi.encode(input));
    }

    function makeDelegateCallToIdentityPrecompile()  external returns (bool success, bytes memory returnData) {
        bytes memory input = abi.encode("Hello, World");

        (bool success, bytes memory returnData) = address(0x04).delegatecall(abi.encode(input));
    }

}
