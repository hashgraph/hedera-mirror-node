@contractbase @fullsuite @equivalence
Feature: in-equivalence tests

  Scenario Outline: Validate in-equivalence system accounts for balance, extcodesize, extcodecopy, extcodehash
    Given I successfully create selfdestruct contract
    Given I successfully create equivalence call contract
    Given I successfully create estimate precompile contract
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

  Scenario Outline: Validate in-equivalence tests for different calls
    Given I successfully create equivalence call contract
    Given I ensure token "FUNGIBLE" has been created
    And I associate "FUNGIBLE" to contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I execute directCall to "0.0.0" address without amount
    Then I execute directCall to "0.0.0" address with amount 10000
    Then I make internal call to ethereum precompile "0.0.1" address with amount
    Then I make internal call to ethereum precompile "0.0.9" address with amount
    Then I make internal call to system account "0.0.357" without amount
    Then I make internal call to system account "0.0.357" with amount
    Then I execute internal call against HTS precompile with approve function for "FUNGIBLE" without amount
    Then I execute internal call against HTS precompile with approve function for "FUNGIBLE" with amount
    Then I execute internal call against PRNG precompile address without amount
    Then I execute internal call against PRNG precompile address with amount
    Then I execute internal call against exchange rate precompile address without amount
    Then I execute internal call against exchange rate precompile address with amount
    Then I make internal call to system account "0.0.741" without amount
    Then I make internal call to system account "0.0.741" with amount
