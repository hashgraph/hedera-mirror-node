@contractbase @fullsuite @equivalence
Feature: in-equivalence tests

  Scenario Outline: Validate in-equivalence system accounts for balance, extcodesize, extcodecopy, extcodehash
    Given I successfully create selfdestruct contract
    Given I successfully create equivalence call contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I execute selfdestruct and set beneficiary to invalid <account> address
    Then I execute balance opcode to system account <account> address would return 0
    Then I verify extcodesize opcode against a system account <account> address returns 0
    Then I verify extcodecopy opcode against a system account <account> address returns empty bytes
    Then I verify extcodehash opcode against a system account <account> address returns empty bytes
    Examples:
      | account   |
      | "0.0.0"   |
      | "0.0.1"   |
      | "0.0.2"   |
      | "0.0.3"   |
      | "0.0.4"   |
      | "0.0.5"   |
      | "0.0.6"   |
      | "0.0.7"   |
      | "0.0.8"   |
      | "0.0.9"   |
      | "0.0.10"  |
      | "0.0.11"  |
      | "0.0.356" |
      | "0.0.357" |
      | "0.0.358" |
      | "0.0.359" |
      | "0.0.360" |
      | "0.0.361" |
      | "0.0.750" |

  Scenario Outline: Validate in-equivalence tests for addresses over 750
    Given I successfully create selfdestruct contract
    Given I successfully create equivalence call contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I execute balance opcode against a contract with balance
    Then I execute selfdestruct and set beneficiary to valid "0.0.800" address
    Given I successfully create selfdestruct contract
    Then I execute selfdestruct and set beneficiary to the deleted contract address
    Then I verify extcodesize opcode against a system account "0.0.999" address returns 0
    Then I verify extcodecopy opcode against a system account "0.0.999" address returns empty bytes
    Then I verify extcodehash opcode against a system account "0.0.999" address returns empty bytes

  Scenario Outline: Validate in-equivalence tests for system accounts with call, staticcall, delegatecall
  and callcode
    Given I successfully create equivalence call contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I make internal <callType> to system account <account> <amountType> amount

    Examples:
      | callType       | account   | amountType |
      | "call"         | "0.0.0"   | "without"  |
      | "call"         | "0.0.0"   | "with"     |
      | "call"         | "0.0.357" | "without"  |
      | "call"         | "0.0.357" | "with"     |
      | "call"         | "0.0.741" | "without"  |
      | "call"         | "0.0.741" | "with"     |
      | "call"         | "0.0.800" | "without"  |
      | "call"         | "0.0.800" | "with"     |
      | "staticcall"   | "0.0.0"   | "without"  |
      | "staticcall"   | "0.0.357" | "without"  |
      | "staticcall"   | "0.0.741" | "without"  |
      | "staticcall"   | "0.0.800" | "without"  |
      | "delegatecall" | "0.0.0"   | "without"  |
      | "delegatecall" | "0.0.357" | "without"  |
      | "delegatecall" | "0.0.741" | "without"  |
      | "delegatecall" | "0.0.800" | "without"  |
      | "callcode"     | "0.0.0"   | "without"  |
      | "callcode"     | "0.0.0"   | "with"     |
      | "callcode"     | "0.0.357" | "without"  |
      | "callcode"     | "0.0.357" | "with"     |
      | "callcode"     | "0.0.741" | "without"  |
      | "callcode"     | "0.0.741" | "with"     |
      | "callcode"     | "0.0.800" | "without"  |
      | "callcode"     | "0.0.800" | "with"     |

  Scenario Outline: Validate in-equivalence tests for internal calls - precompiles
    Given I successfully create equivalence call contract
    Given I ensure token "FUNGIBLE" has been created
    And I associate "FUNGIBLE" to contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I execute "internal" call against Ecrecover precompile
    Then I execute "static" call against Ecrecover precompile
    Then I execute "delegate" call against Ecrecover precompile
    Then I execute "internal" call against SHA-256 precompile
    Then I execute "static" call against SHA-256 precompile
    Then I execute "delegate" call against SHA-256 precompile
    Then I execute "internal" call against Ripemd-160 precompile
    Then I execute "static" call against Ripemd-160 precompile
    Then I execute "delegate" call against Ripemd-160 precompile
    Then I execute directCall to "0.0.0" address without amount
    Then I execute directCall to "0.0.0" address with amount 10000
    Then I make internal call to ethereum precompile "0.0.1" address with amount
    Then I make internal call to ethereum precompile "0.0.9" address with amount
    Then I execute internal "call" against PRNG precompile address "without" amount
    Then I execute internal "call" against PRNG precompile address "with" amount
    Then I execute internal "staticcall" against PRNG precompile address "without" amount
    Then I execute internal "delegatecall" against PRNG precompile address "without" amount
    Then I execute internal "callcode" against PRNG precompile address "without" amount
    Then I execute internal "callcode" against PRNG precompile address "with" amount
    Then I execute internal call against exchange rate precompile address without amount
    Then I execute internal call against exchange rate precompile address with amount
    Then I execute internal "call" against HTS precompile with approve function for "FUNGIBLE" "without" amount
    Then I execute internal "call" against HTS precompile with approve function for "FUNGIBLE" "with" amount
    Then I execute internal "staticcall" against HTS precompile with approve function for "FUNGIBLE" "without" amount
    Then I execute internal "delegatecall" against HTS precompile with approve function for "FUNGIBLE" "without" amount
    Then I execute internal "callcode" against HTS precompile with approve function for "FUNGIBLE" "without" amount
    #Then I execute internal "callcode" against HTS precompile with approve function for "FUNGIBLE" "with" amount


  Scenario Outline: Validate in-equivalence tests for HTS Transfers
    Given I successfully create estimate precompile contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Given I successfully create tokens
    And I update the account and token key
    Then the mirror node REST API should return status 200 for the HAPI transactions
    Then I call precompile with transfer FUNGIBLE token to a <account> address
    Then I call precompile with transfer NFT token to a <account> address
    Then I call precompile with transferFrom FUNGIBLE token to a <account> address

    Examples:
      | account   |
#      | "0.0.0" |  INVALID_ALIAS_KEY
      | "0.0.1"   |
      | "0.0.2"   |
      | "0.0.3"   |
      | "0.0.4"   |
      | "0.0.5"   |
      | "0.0.6"   |
      | "0.0.7"   |
      | "0.0.8"   |
      | "0.0.9"   |
      | "0.0.10"  |
      | "0.0.11"  |
#      | "0.0.350" |  INVALID_ALIAS_KEY
#      | "0.0.356" |  INVALID_ALIAS_KEY
#      | "0.0.357" |  INVALID_ALIAS_KEY
#      | "0.0.358" |  INVALID_ALIAS_KEY
#      | "0.0.359" |  INVALID_ALIAS_KEY
#      | "0.0.360" |  INVALID_ALIAS_KEY
#      | "0.0.361" |  INVALID_ALIAS_KEY
      | "0.0.750" |
      | "0.0.800" |

  Scenario: Validate in-equivalence tests for HTS Transfers with state modification
    Given I successfully create estimate precompile contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Given I successfully create tokens
    Given I mint a new nft
    Then I call precompile with transferFromNFT to a "0.0.1" address
    Then I call precompile with transferFromNFT to a "0.0.9" address
    Then I call precompile with transferFromNFT to a "0.0.10" address
    Then I call precompile with transferFromNFT to a "0.0.11" address
    #Then I call precompile with transferFromNFT to a "0.0.350" address
    #Then I call precompile with transferFromNFT to a "0.0.357" address
#    Then I call precompile with transferFromNFT to a "0.0.358" address
#    Then I call precompile with transferFromNFT to a "0.0.359" address
#    Then I call precompile with transferFromNFT to a "0.0.360" address
#    Then I call precompile with transferFromNFT to a "0.0.361" address
    Then I call precompile with transferFromNFT to a "0.0.750" address
    Then I call precompile with transferFromNFT to a "0.0.800" address