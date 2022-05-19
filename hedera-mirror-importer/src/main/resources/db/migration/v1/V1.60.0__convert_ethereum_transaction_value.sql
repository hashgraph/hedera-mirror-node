---

alter table if exists ethereum_transaction rename column value to value_in_weibar;
alter table if exists ethereum_transaction add column value bigint null;

create or replace function weibar_to_tinybar(weibar bytea) returns bigint as $$
declare
  firstLen integer;
  i integer;
  len integer;
  scale constant decimal := 72057594037927936;
  tempValue decimal;
  value decimal := 0;
begin
  if weibar is null then
    return null;
  end if;

  select length(weibar) into len;
  if len <= 7 then
    execute 'select x''' || encode(weibar, 'hex') || '''::bigint' into value;
  else
    firstLen = len % 7;
    if firstLen <> 0 then
      execute 'select x''' || encode(substring(weibar, 1, firstLen), 'hex') || '''::bigint' into value;
    end if;

    i = firstLen + 1;
    loop
      execute 'select x''' || encode(substring(weibar, i, 7), 'hex') || '''::bigint' into tempValue;
      value = value * scale + tempValue;

      i = i + 7;
      if i > len then
        exit;
      end if;
    end loop;
  end if;

  return floor(value / 10000000000)::bigint;
end
$$ language plpgsql;

alter table if exists ethereum_transaction drop column value_in_weibar;

drop function if exists weibar_to_tinybar(weibar bytea);
