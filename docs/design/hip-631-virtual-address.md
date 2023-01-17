# HIP-631 Virtual Addresses Design

## Purpose

[HIP-631](https://hips.hedera.com/hip/hip-631) describes new mechanisms to support "EVM equivalency" (allowing Hedera
accounts to better interoperate with Ethereum Smart contracts than the existing "EVM compatibilty" by allowing a Hedera Account to have
multiple "virtual addresses" over just a single alias.  A Hedera account will have the ability to add, disable, or delete
virtual addresses where any virtual address can be associated with only one given Hedera account at a time.
 When a virtual address is disabled, it is still locked to the Hedera Account it had been actuve with.
It is not until that virtual address is deleted than it can be added to another Hedera account.


HIP 631 is very large and extensive -- it proposes changes to HAPI, the Hedera SDK, the Mirror Node, wallets,
and more.  The purpose of this design doc is to isolate the Mirror Node requirements from the overall HIP document.

## Goals

* Enhance the database schema to store an account's list of virtual addresses.
* Store the historical state of an account's virtual address list.
* Enhance the REST API to show an account's active virtual addresses list (only a list of `ACTIVE` virtual addresses).

## Non-Goals

* Enhance gRPC APIs with virtual address information
* Enhance Web3 APIs with virtual address information
* Enhance the REST API to show an account's DISABLED or DELETED virtual addresses.
* Any sort of behavior based on the number of active/disabled virtual addresses.  (The HIP-631 document proposes incremental capacity limits as 1, then 3, then 10, then possibly an unbounded number.  For now, the Mirror Node implementation will just assume the limit is observed by HAPI/the consensus nodes, as it's really not possible for the mirror node to count how many virtual addresses already exist, when a new one is being added or deleted.)

## Architecture

### Database

#### Virtual Address Status

```sql
  create type virtual_address_status as enum ('ACTIVE', 'DELETED', 'DISABLED');
```

We will have an index that only holds the non-'DELETED' values, so skipping over the DELETED rows will be fast.  A row in 'DELETED' status will just be ignored.

#### Virtual Addresses

```sql
  create table if not exists entity_virtual_addresses (
     account_id          bigint not null,
     evm_address         bytea not null,
     status              virtual_address_status not null default 'ACTIVE',
     timestamp_range     int8range not null,
     primary key (account_id, evm_address)
 );

  create table if not exists entity_virtual_addresses_history (
  like entity_virtual_addresses including defaults,
     primary key (account_id, evm_address, timestamp_range)
 );

  create index if not exists entity_virtual_addresses__evm_address on entity_virtual_addresses (account_id, evm_address) 
  where status <> 'DELETED';
```

While the HIP-631 document calls this table `account_virtual_address`, since we will use it to hold virtual addresses for both accounts and contracts,
this table name is more in line with existing mirror node naming.

#### Existing entity table is unchanged.

We are choosing to use the existing `evm_address` field of the `entity` table to hold the default virtual address, rather than adding a new column for this purpose.
The HIP-631 document indicates that whenever a virtual address gets deleted, it cannot be the default virtual address getting deleted.<F12>, so we only need to update
the `evm_address` field of an existing account (or contract) when a `CryptoUpdate` operation is invoked that specifies `addVirtualAddress` and `setDefaultVirtualAddress(true)`
(at which point we would update the `evm_address` feild of that account/contract to be that of the new virtual address being added).  The HIP-631 document does not indicate
any other way (than adding a new address) to update the default virtual address of an account.

### Importer

#### CryptoUpdate Parsing

When parsing CryptoUpdate transactions,

* Heed new `CryptoUpdate` operations (`addVirtualAddress`, `disableVirtualAddress`, `removeVirtualAddress`, `setDefaultVirtualAddress`).
    - addVirtualAddress: The mirror node won't easily be able to see how many other virtual addresses already exist for that account, so we probably won't be able to issue a warning if too many virtual addresses are already assigned to that account.  In all cases, we should replace the default virtual address of the entity (the `evm_address` column) only if `setDefaultCVirtualAddress(true)` is specified in the `CryptoUpdate.`
    - disableVirtualAddress: The address being disabled, in theory, should not be the default virtual address (`evm_address`) field.  The mirror node won't be able to query the current status of the virtual address being disabled; we just UPSERT an entry with the `evm_address` matching the address being disabled, and the `status` set to `DISABLED`.
    - removeVirtualAddress: The address being deleted, in theory, should not be the default virtual address (`evm_address`) field.  The mirror node won't be able to query the current status of the virtual address being deleted; we just UPSERT an entry with the `evm_address` matching the address being disabled, and the `status` set to `DELETED`.

### REST API

#### getAccountByIdOrAliasOrEvmAddress

`api/v1/accounts/{idOrAliasOrEvmAddress}` changes:  Add the following two fields to the output: (one string and one tuple array)

```json
  {
    ...,
  
    hedera_address: '0x0...1', // we calculate this field by expressing the account's entity_id shard.realm.num in "long-zero" format.  It is *not* a new column to maintain.
    virtual_addresses: [
      {
        evm_address: '0x2...3',
        is_default: false
      },
      {
        evm_address: '0x4...5',
        is_default: true
      }
    ]
  }
```


* Not (currently) adding `api/v1/accounts/{idOrAliasOrEvmAddress}/virtualAddresses endpoint (for paging through a large number of virtual addresses), but reserve that endpoint
in case a future extension to this HIP decides to implement that.

* Returning any of the disabled (or deleted) virtual addresses for an account (or contract).  The only ones we return are the `ACTIVE` ones, and which (of those) is the default virtual address.

## Open Questions

1) How far in advance will we know about needing to support very large (e.g. ore than 10) virtual addresses for one account? (That is, to implement the
`api/v1/accounts/{idOrAliasOrEvmAddress}/virtualAddresses` endpoint.)

## Answered Questions

1) Are the CryptoUpdate migrations detailed in the https://hips.hedera.com/hip/hip-631#alias-to-virtual-account-migration section of the HIP-631 document
   external "batched" cryptoupdates?

   yes, I think that we intend to externalize those CryptoUpdate transactions. I am not sure about the "batch" part, is this the way we currently externalize similar transactions?

2) What happens if we attempt to delete or disable the current default virtual address?  Does a different virtual address takeover as default?
   Which one and how does the mirror node know which?

   - Deleting/disabling of virtual addresses that are not set as the default one should be straightforward.

   - If we have only one virtual address (it will be default as well) then it may be possible to delete/disable it.

   - In general it *may not be possible to delete/disable* a virtual address that is the default one *unless before that* we update the default virtual address to be another one.
