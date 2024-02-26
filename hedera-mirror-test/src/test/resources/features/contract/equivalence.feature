@contractbase @fullsuite @equivalence @acceptance
Feature: in-equivalence tests

  Scenario Outline: Validate in-equivalence system accounts for balance, extcodesize, extcodecopy, extcodehash
  and selfdestruct op codes
    Given I successfully create selfdestruct contract
    Given I successfully create equivalence call contract
    Then the mirror node REST API should return status 200 for the contracts creation
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
      Then the mirror node REST API should return status 200 for the contracts creation
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


  Scenario Outline: Validate direct calls
    Given I successfully create selfdestruct contract
    Given I successfully deploy estimate precompile contract
    Given I successfully create equivalence call contract
    Then I verify the equivalence contract bytecode is deployed
    Given I create admin account
    Then I execute directCall to range <accounts> addresses without amount
    Then I execute directCall to range <accounts> addresses with amount 10000
    Then I execute directCall to contract "with" amount
    Then I execute directCall to contract "without" amount
    Then I execute directCall to address with non-payable contract with amount
    Examples:
      | accounts  |
      | "0.0.0"   |
      | "0.0.1"   |
      | "0.0.358" |
#      | "0.0.359" | - disabled due to bug 7811
      | "0.0.741" |
      | "0.0.800" |