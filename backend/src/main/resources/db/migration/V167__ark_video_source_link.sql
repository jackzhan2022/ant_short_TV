alter table ai_video_result add column source_video_url text null;
-- UTC; Ark source links expire 24 hours after generation completes.
alter table ai_video_result add column source_video_expires_at timestamp null;
