@contractbase @fullsuite
Feature: Ethereum transactions Coverage Feature

  @critical @release @acceptance
  Scenario Outline: Validate Ethereum Contract create and call

  The steps in this scenario are very similar to those in contract feature. The update and the deletion
  steps are skipped because of the immutability of the contracts that are created by ethereum transactions.

  Using the Java SDK and the mirror node REST API, create and call a smart contract and
  verify proper operation. Use that same parent contract to create a second child contract using
  the CREATE2 flow, namely by retrieving the child contract bytecode and using it, and a salt value,
  to compute the child's EVM address and then deploying the contract to that address.

  Once the child EVM address is known, issue a crypto transfer transaction to that EVM address, creating
  a hollow account. Verify the account exists and indeed is hollow, but that a contract does not yet
  exist at that EVM address. Then, using a parent contract function, deploy the child contract using
  the CREATE2 opcode and with the same bytecode and salt, thus this is done to the child EVM address.

  With the contract now present, and the account no longer hollow, invoke a function on the child contract
  to cause its self destruction, vacating the EVM address for future use. Also, Finally, delete the original parent
  contract and its underlying bytecode file.

  Given I successfully created a signer account with an EVM address alias
  Then validate the signer account and its balance

  Given I successfully create parent contract by ethereum transaction
  Then the mirror node REST API should return status <httpStatusCode> for the eth contract creation transaction
  And the mirror node REST API should verify the deployed contract entity by eth call

  When I successfully call the child creation function using EIP-1559 ethereum transaction
  Then the mirror node REST API should return status <httpStatusCode> for the ethereum transaction
  And the mirror node REST API should verify the child creation ethereum transaction

  Given I call the parent contract to retrieve child's bytecode by Legacy ethereum transaction
  Then the mirror node REST API should return status <httpStatusCode> for the ethereum transaction
  And the mirror node REST API should verify the ethereum called contract function

  When I call the parent contract evm address function with the bytecode of the child with EIP-2930 ethereum transaction
  Then the mirror node REST API should return status <httpStatusCode> for the ethereum transaction
  And the mirror node REST API should verify the ethereum called contract function

  And I create a hollow account using CryptoTransfer of <transferAmount> to the child's evm address
  Then the mirror node REST API should return status <httpStatusCode> for the transfer transaction
  And the mirror node REST API should verify the account is hollow and has <transferAmount>
  And the mirror node REST API should not find a contract when using child's evm address

  When I create a child contract by calling the parent contract function to deploy using CREATE2 with EIP-1559
  Then the mirror node REST API should return status <httpStatusCode> for the ethereum transaction
  And the mirror node REST API should retrieve the contract when using child's evm address
  And the mirror node REST API should verify that the account is not hollow

  When I successfully delete the child contract by calling vacateAddress function using EIP-1559 ethereum transaction
  Then the mirror node REST API should return status <httpStatusCode> for the vacateAddress call transaction
  Then I successfully delete the parent bytecode file




  Examples:
    | httpStatusCode | transferAmount |
    | 200            | 1000           |