@contractbase @fullsuite @equivalence
Feature: in-equivalence tests

  Scenario Outline: Validate in-equivalence self destruct negative tests
    Given I successfully create in-equivalence contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I execute selfdestruct and set beneficiary to invalid <firstAddressRange> address
    Examples:
      | firstAddressRange |
      | "0.0.1"      |
      | "0.0.2"      |
      | "0.0.3"      |
      | "0.0.4"      |
      | "0.0.5"      |
      | "0.0.6"      |
      | "0.0.7"      |
      | "0.0.8"      |
      | "0.0.9"      |
      | "0.0.10"     |
      | "0.0.11"     |
      | "0.0.356"    |
      | "0.0.357"    |
      | "0.0.358"    |
      | "0.0.359"    |
      | "0.0.360"      |
      | "0.0.361"      |
      | "0.0.750"      |

  Scenario Outline: Validate in-equivalence self destruct positive tests
    Given I successfully create in-equivalence contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I execute selfdestruct and set beneficiary to valid <firstAddressRange> address
    Examples:
      | firstAddressRange |
      | "0.0.800"      |
