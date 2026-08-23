update style_library
set storage_path = concat('style-library/public/', external_id, '/cover-compressed.jpg'),
    image_url = concat('/style-library/public/', external_id, '/cover-compressed.jpg'),
    updated_at = now()
where storage_path like 'style-library/public/%/cover.%';
