update style_library
   set storage_path = concat(
       'platform/style-library/',
       external_id,
       '/source/derived/display.',
       case
         when lower(source_image_url) regexp '\\.(jpe?g)([?#].*)?$' then 'jpg'
         when lower(source_image_url) regexp '\\.gif([?#].*)?$' then 'gif'
         else 'png'
       end
   );
