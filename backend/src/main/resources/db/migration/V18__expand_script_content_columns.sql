alter table script
  modify column content longtext not null;

alter table script_version
  modify column input_summary longtext null;

alter table script_version
  modify column content longtext not null;
