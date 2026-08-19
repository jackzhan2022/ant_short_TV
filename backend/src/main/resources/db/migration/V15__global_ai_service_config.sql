update ai_service_config
   set is_default = false,
       updated_at = now()
 where is_default = true
   and deleted_at is null
   and id not in (
     select id
       from (
         select
           id,
           row_number() over (
             partition by service_type
             order by priority desc, id desc
           ) as row_no
         from ai_service_config
         where is_default = true
           and deleted_at is null
       ) ranked_defaults
      where row_no = 1
   );

create unique index uk_ai_service_config_default_global on ai_service_config (service_type, default_marker);
