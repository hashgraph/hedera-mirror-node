select run_command_on_colocated_placements(
  'entity_stake',
  'entity_stake_history',
  $cmd$
    create trigger entity_stake_trigger
      after update on %s
      for each row
      execute PROCEDURE add_entity_stake_history(%L)
  $cmd$
);
