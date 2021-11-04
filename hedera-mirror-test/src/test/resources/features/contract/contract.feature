@contractbase @fullsuite
Feature: Contract Base Coverage Feature

    @critical @release @acceptance @update
    Scenario Outline: Validate Contract Flow - ContractCreate and ContractUpdate
        Given I successfully create a contract from contract bytes
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        When I successfully update the contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        Examples:
            | httpStatusCode |
            | 200            |

    @critical @release @acceptance
    Scenario Outline: Validate Contract Call - ContractCreate and ContractCall
        Given I successfully create a contract from contract bytes
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        When I successfully call the contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        Examples:
            | httpStatusCode |
            | 200            |

    @critical @release @acceptance @delete
    Scenario Outline: Validate Contract Flow - ContractCreate and ContractDelete
        Given I successfully create a contract from contract bytes
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        When I successfully delete the contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deleted contract entity
        Examples:
            | httpStatusCode |
            | 200            |
