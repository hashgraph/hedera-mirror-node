@filebase @fullsuite
Feature: File Base Coverage Feature

    @critical @release @acceptance @update
    Scenario Outline: Validate File Flow - FileCreate and FileUpdate
        Given I successfully create a file
        When the network confirms file presence
        Then the mirror node REST API should return status <httpStatusCode> for the file transaction
        When I successfully update the file with <bytesLength> bytes
        And the network confirms file update
        Then the mirror node REST API should return status <httpStatusCode> for the file transaction
        Examples:
            | httpStatusCode | bytesLength |
            | 200            | "FULL"      |

    @critical @release @acceptance @update
    Scenario Outline: Validate File Flow - FileCreate, FileUpdate and FileAppend
        Given I successfully create a file
        When the network confirms file presence
        Then the mirror node REST API should return status <httpStatusCode> for the file transaction
        When I successfully update the file with <bytesLength> bytes
        And the network confirms partial file contents
        Then the mirror node REST API should return status <httpStatusCode> for the file transaction
        When I successfully append to the file
        And the network confirms an append update
        Then the mirror node REST API should return status <httpStatusCode> for the file transaction
        Examples:
            | httpStatusCode | bytesLength |
            | 200            | "PARTIAL"   |

    @critical @release @acceptance @delete
    Scenario Outline: Validate File Flow - FileCreate and FileDelete
        Given I successfully create a file
        When the network confirms file presence
        Then the mirror node REST API should return status <httpStatusCode> for the file transaction
        When I successfully delete the file
        And the network confirms file absence
        Then the mirror node REST API should return status <httpStatusCode> for the file transaction
        Examples:
            | httpStatusCode |
            | 200            |
