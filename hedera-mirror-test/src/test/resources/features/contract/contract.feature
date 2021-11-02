@contractbase @fullsuite
Feature: Contract Base Coverage Feature

    @critical @release @acceptance @update
    Scenario Outline: Validate Contract Flow - ContractCreate and ContractUpdate
        Given I successfully create a contract from contracts bytes
        When the network confirms contract presence
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        When I successfully update the contract
        And the network confirms contract update
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        Examples:
            | httpStatusCode |
            | 200            |

    @critical @release @acceptance @delete
    Scenario Outline: Validate Contract Flow - ContractCreate and ContractDelete
        Given I successfully create a contract from contracts bytes
        When the network confirms contract presence
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        When I successfully delete the contract
        And the network confirms contract absence
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        Examples:
            | httpStatusCode |
            | 200            |
