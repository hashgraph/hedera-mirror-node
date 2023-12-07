@contractbase @fullsuite @equivalence
Feature: in-equivalence tests

  Scenario Outline: Validate in-equivalence system accounts for balance, extcodesize, extcodecopy, extcodehash
    and selfdestruct op codes
    Given I successfully create selfdestruct contract
    Given I successfully create equivalence call contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I execute balance opcode against a contract with balance
    Then I execute selfdestruct and set beneficiary to <account> address
    Then I execute balance opcode to system account <account> address would return 0
    Then I verify extcodesize opcode against a system account <account> address returns 0
    Then I verify extcodecopy opcode against a system account <account> address returns empty bytes
    Then I verify extcodehash opcode against a system account <account> address returns empty bytes
    Given I successfully create selfdestruct contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I execute selfdestruct and set beneficiary to the deleted contract address
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
      | "0.0.999" |
