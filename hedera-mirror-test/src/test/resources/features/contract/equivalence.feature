@contractbase @fullsuite @equivalence @acceptance
Feature: in-equivalence tests

  Scenario Outline: Validate in-equivalence system accounts for balance, extcodesize, extcodecopy, extcodehash
  and selfdestruct op codes
    Given I successfully create selfdestruct contract
    Given I successfully create equivalence call contract
    Then the mirror node REST API should return status 200 for the HAPI transactions
    Then I verify the equivalence contract bytecode is deployed
    Then I verify the selfdestruct contract bytecode is deployed
    Then I execute selfdestruct and set beneficiary to <account> address
    Then I execute balance opcode to system account <account> address would return 0
    Then I verify extcodesize opcode against a system account <account> address returns 0
    Then I verify extcodecopy opcode against a system account <account> address returns empty bytes
    Then I verify extcodehash opcode against a system account <account> address returns empty bytes
    Examples:
      | account    |
      | "0.0.1"    |
      | "0.0.10"   |
      | "0.0.358"  |
      | "0.0.750"  |
      | "0.0.999"  |

#   Separated scenario as it needs to be executed only once
    Scenario Outline: Validate in-equivalence for selfdestruct and balance against contract
      Given I successfully create selfdestruct contract
      Then I verify the selfdestruct contract bytecode is deployed
      Given I successfully create equivalence call contract
      Then I verify the equivalence contract bytecode is deployed
      Then the mirror node REST API should return status 200 for the HAPI transactions
      Then I execute balance opcode against a contract with balance
      Then I execute selfdestruct and set beneficiary to the deleted contract address

  Scenario Outline: Validate in-equivalence tests for system accounts with call, staticcall, delegatecall
  and callcode
    Given I successfully create equivalence call contract
    Then I verify the equivalence contract bytecode is deployed
    Given I successfully create fungible token for internal calls tests
    Then I make internal <callType> to system account <account> <amountType> amount

    Examples:
      | callType       | account   | amountType |
      | "call"         | "0.0.0"   | "without"  |
#      | "call"         | "0.0.0"   | "with"     | fails
      | "call"         | "0.0.4"   | "without"  |
#      | "call"         | "0.0.4"   | "with"     | fails
      | "call"         | "0.0.100" | "without"  |
#      | "call"         | "0.0.100" | "with"     | fails
      | "call"         | "0.0.358" | "without"  |
#      | "call"         | "0.0.358" | "with"     | fails
      | "call"         | "0.0.359" | "without"  |
#      | "call"         | "0.0.359" | "with"     | fails
      | "call"         | "0.0.741" | "without"  |
#      | "call"         | "0.0.741" | "with"     | fails
      | "call"         | "0.0.800" | "without"  |
      | "call"         | "0.0.800" | "with"     |
      | "staticcall"   | "0.0.0"   | "without"  |
      | "staticcall"   | "0.0.4"   | "without"  |
      | "staticcall"   | "0.0.358" | "without"  |
      | "staticcall"   | "0.0.359" | "without"  |
      | "staticcall"   | "0.0.741" | "without"  |
      | "staticcall"   | "0.0.800" | "without"  |
      | "delegatecall" | "0.0.0"   | "without"  |
      | "delegatecall" | "0.0.4"   | "without"  |
      | "delegatecall" | "0.0.358" | "without"  |
      | "delegatecall" | "0.0.359" | "without"  |
      | "delegatecall" | "0.0.741" | "without"  |
      | "delegatecall" | "0.0.800" | "without"  |
      | "callcode"     | "0.0.0"   | "without"  |
#      | "callcode"     | "0.0.0"   | "with"     | fails
      | "callcode"     | "0.0.4"   | "without"  |
#      | "callcode"     | "0.0.4"   | "with"     | add assertion
      | "callcode"     | "0.0.358" | "without"  |
#      | "callcode"     | "0.0.358" | "with"     | fails
      | "callcode"     | "0.0.741" | "without"  |
#      | "callcode"     | "0.0.741" | "with"     | fails
      | "callcode"     | "0.0.800" | "without"  |
      | "callcode"     | "0.0.800" | "with"     |

  Scenario Outline: Validate in-equivalence tests for internal calls
    Given I successfully create equivalence call contract
    Given I successfully create selfdestruct contract
    Then I verify the equivalence contract bytecode is deployed
    Then I verify the selfdestruct contract bytecode is deployed
    Given I ensure token "FUNGIBLE" has been created
    Given I ensure token "NFT" has been created
    Given I successfully create tokens
    And I associate "FUNGIBLE" to contract
    Then the mirror node REST API should return status 200 for the HAPI transactions
    Then I execute internal "call" against "payable" contract "with" amount
    Then I execute internal "call" against "non-payable" contract "with" amount
    Then I execute internal "call" against "payable" contract "without" amount
    Then I execute internal "staticcall" against "payable" contract "without" amount
    Then I execute internal "delegatecall" against "payable" contract "without" amount
    Then I execute internal "callcode" against "payable" contract "with" amount
    Then I execute internal "callcode" against "non-payable" contract "with" amount
    Then I execute internal "callcode" against "payable" contract "without" amount
    Then I execute internal "call" against Identity precompile
    Then I execute internal "staticcall" against Identity precompile
    Then I execute internal "delegatecall" against Identity precompile
    Then I execute internal "call" against Ecrecover precompile
    Then I execute internal "staticcall" against Ecrecover precompile
    Then I execute internal "delegatecall" against Ecrecover precompile
    Then I execute internal "call" against SHA-256 precompile
    Then I execute internal "staticcall" against SHA-256 precompile
    Then I execute internal "delegatecall" against SHA-256 precompile
    Then I execute internal "call" against Ripemd-160 precompile
    Then I execute internal "staticcall" against Ripemd-160 precompile
    Then I execute internal "delegatecall" against Ripemd-160 precompile
    Then I execute internal "call" against PRNG precompile address "without" amount
#    Then I execute internal "call" against PRNG precompile address "with" amount - success but should fail
    Then I execute internal "staticcall" against PRNG precompile address "without" amount
    Then I execute internal "delegatecall" against PRNG precompile address "without" amount
    Then I execute internal "callcode" against PRNG precompile address "without" amount
#    Then I execute internal "callcode" against PRNG precompile address "with" amount - success but should fail
    Then I execute internal "call" against exchange rate precompile address "without" amount
#    Then I execute internal "call" against exchange rate precompile address "with" amount - success but should fail
    Then I execute internal "staticcall" against exchange rate precompile address "without" amount
    Then I execute internal "delegatecall" against exchange rate precompile address "without" amount

  Scenario Outline: Validate in-equivalence tests for HTS Transfers
    Given I successfully create estimate precompile contract for in equivalence validation
    Then I verify the estimate precompile contract bytecode is deployed in mirror node
    Given I successfully create tokens
    Given I mint a new nft
    Then the mirror node REST API should return status 200 for the HAPI transactions
    Then I call precompile with transfer "FUNGIBLE" token to a <account> address
    Then I call precompile with transfer "NFT" token to a <account> address
    Then I call precompile with transferFrom "FUNGIBLE" token to a <account> address
    Then I call precompile with transferFrom "NFT" token to a <account> address
    Then I call precompile with transferFromNFT to a <account> address

    Examples:
      | account     |
#      contract reverts due to INVALID_ALIAS_KEY - C but should be INVALID_RECEIVING_NODE_ACCOUNT
#      contract reverts due to INVALID_ACCOUNT_ID - M but should be INVALID_RECEIVING_NODE_ACCOUNT
      | "0.0.0"     |
#      contract reverts due to TOKEN_NOT_ASSOCIATED_TO_ACCOUNT - M but should be INVALID_RECEIVING_NODE_ACCOUNT
      | "0.0.1"     |
#      contract reverts due to TOKEN_NOT_ASSOCIATED_TO_ACCOUNT - M but should be INVALID_RECEIVING_NODE_ACCOUNT
      | "0.0.4"     |
#      contract reverts due to INVALID_ALIAS_KEY - C but should be INVALID_RECEIVING_NODE_ACCOUNT
#      contract reverts due to INVALID_ACCOUNT_ID - M but should be INVALID_RECEIVING_NODE_ACCOUNT
      | "0.0.358"   |
#      contract reverts due to INVALID_ALIAS_KEY - C but should be INVALID_RECEIVING_NODE_ACCOUNT
#      contract reverts due to INVALID_ACCOUNT_ID - M but should be INVALID_RECEIVING_NODE_ACCOUNT
      | "0.0.359"   |
#      contract reverts due to INVALID_ALIAS_KEY - C but should be INVALID_RECEIVING_NODE_ACCOUNT
#      contract reverts due to INVALID_ACCOUNT_ID - M but should be INVALID_RECEIVING_NODE_ACCOUNT
      | "0.0.360"   |
#      contract reverts due to TOKEN_NOT_ASSOCIATED_TO_ACCOUNT - M but should be INVALID_RECEIVING_NODE_ACCOUNT
      | "0.0.741"   |
      | "0.0.800"   |

  Scenario: Validate in-equivalence tests for HTS Transfers with state modification
    Given I successfully create estimate precompile contract for in equivalence validation
    Then I verify the estimate precompile contract bytecode is deployed in mirror node
    Given I successfully create equivalence call contract
    Then I verify the equivalence contract bytecode is deployed
    Given I successfully create tokens
    Given I mint a new nft
    Then the mirror node REST API should return status 200 for the HAPI transactions
    Then I call precompile with transferFrom "NFT" token to a contract
    Then I call precompile with transferFrom "FUNGIBLE" token to a contract
    Given I mint a new nft
    Then the mirror node REST API should return status 200 for the HAPI transactions
    Then I call precompile with transferFrom "FUNGIBLE" token to a "BOB" EVM address
#    Then I call precompile with transferFrom "NFT" token to a "BOB" EVM address // success but should fail
    Then I call precompile with transferFrom "NFT" token to a "ALICE" EVM address
    Then I call precompile with transferFrom "FUNGIBLE" token to a "ALICE" EVM address
    Given I mint a new nft
    Then the mirror node REST API should return status 200 for the HAPI transactions
#    Then I call precompile with transferFrom "NFT" token to an EVM address // fails on mirror node since the address is not found in the state and it is not created
#    Then I call precompile with transferFrom "FUNGIBLE" token to an EVM address // same as above
    Given I mint a new nft
    Then the mirror node REST API should return status 200 for the HAPI transactions
    And I update the "BOB" account and token key for contract "ESTIMATE_PRECOMPILE"
    Then I call precompile with signer BOB to transferFrom "NFT" token to ALICE
    Then I call precompile with signer BOB to transferFrom "FUNGIBLE" token to ALICE