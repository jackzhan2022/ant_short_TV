ALTER TABLE download_task ADD COLUMN output_format VARCHAR(8) NULL;
ALTER TABLE download_task ADD COLUMN file_name VARCHAR(255) NULL;
ALTER TABLE download_task ADD COLUMN content_type VARCHAR(120) NULL;
