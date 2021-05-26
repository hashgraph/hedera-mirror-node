-------------------
-- Support upsert capabilities for entity related tables
-------------------

-- allow nullable on entity deleted as transaction cannot make this assumption
alter table entity
    alter column deleted drop default,
    alter column deleted drop not null;

-- create getNewAccountFreezeStatus function
-- if no freeze_key return NOT_APPLICABLE (0), else return FROZEN (1) or UNFROZEN (2) based on if freeze_default is true
create or replace function getNewAccountFreezeStatus(tokenId bigint) returns smallint as
$$
declare
    key           bytea;
    freezeDefault boolean;
    status        smallint;
begin
    select into freezeDefault, key freeze_default, freeze_key from token where token_id = tokenId;
    if key is null then
        status := 0;
    else
        if freezeDefault then
            status := 1;
        else
            status := 2;
        end if;
    end if;

    return status;
end
$$ language plpgsql;


-- create getNewAccountKycStatus function - takes tokenId and gives KycStatus default
-- if no key_key return NOT_APPLICABLE (0), else return REVOKED (2)
create or replace function getNewAccountKycStatus(tokenId bigint) returns smallint as
$$
declare
    key    bytea;
    status smallint;
begin
    select into key kyc_key from token where token_id = tokenId;
    if key is null then
        status := 0;
    else
        status := 2;
    end if;

    return status;
end
$$ language plpgsql;

-- create getNewAccountKycStatus function - takes tokenId and gives KycStatus default
-- if no key_key return NOT_APPLICABLE (0), else return REVOKED (2)
create or replace function getTokenName(tokenId bigint) returns smallint as
$$
declare
    tokenName varchar;
begin
    select into tokenName name from token where token_id = tokenId;
    return tokenName;
end
$$ language plpgsql;
