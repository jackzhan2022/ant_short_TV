package com.antshorttv.inspiration;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(statements = {
    "create table if not exists inspiration_creation (id bigint primary key auto_increment, external_id varchar(64) not null unique, external_task_id varchar(64), creation_type varchar(64) not null, task_type varchar(64) not null, title varchar(255) not null, author_name varchar(64) not null, url varchar(1024) not null, storage_path varchar(512) not null, mime_type varchar(128), file_size bigint, detail_json longtext, source_created_at datetime, source_updated_at datetime, import_status varchar(32) not null default 'IMPORTED', import_error text, sort_order int not null default 0, created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp)",
    "delete from inspiration_creation",
    "insert into inspiration_creation (external_id, external_task_id, creation_type, task_type, title, author_name, url, storage_path, mime_type, file_size, detail_json, source_created_at, source_updated_at, import_status, import_error, sort_order, created_at, updated_at) values ('864900000000000001', null, 'IMAGE', '短剧灵感', '逆袭归来', '管理员', 'https://example.com/cover-01.jpg', 'inspiration-creations/public/864900000000000001/cover.jpg', 'image/jpeg', null, '{\"prompt\":\"被误解的女主多年后带着证据回归。\"}', now(), now(), 'IMPORTED', null, 1, now(), now()), ('864900000000000002', null, 'IMAGE', '短剧灵感', '隐藏草稿', '管理员', 'https://example.com/cover-02.jpg', 'inspiration-creations/public/864900000000000002/cover.jpg', 'image/jpeg', null, null, now(), now(), 'FAILED', null, 2, now(), now())"
})
class InspirationCreationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ObjectStorageService objectStorageService;

    @Test
    void listsImportedInspirationCreationsFromDedicatedGallery() throws Exception {
        when(objectStorageService.publicUrl(anyString()))
            .thenAnswer(invocation -> "https://minio.aixmax.cn/ant-short-tv/" + invocation.getArgument(0) + "?X-Amz-Signature=test");

        mockMvc.perform(get("/api/inspiration-creations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].externalId", is("864900000000000001")))
            .andExpect(jsonPath("$.data[0].title", is("逆袭归来")))
            .andExpect(jsonPath("$.data[0].authorName", is("管理员")))
            .andExpect(jsonPath("$.data[0].localUrl", is("https://minio.aixmax.cn/ant-short-tv/inspiration-creations/public/864900000000000001/cover.jpg?X-Amz-Signature=test")))
            .andExpect(jsonPath("$.data[0].detailJson").doesNotExist());
    }

    @Test
    void showsInspirationCreationDetailWithPromptJson() throws Exception {
        when(objectStorageService.publicUrl(anyString()))
            .thenAnswer(invocation -> "https://minio.aixmax.cn/ant-short-tv/" + invocation.getArgument(0) + "?X-Amz-Signature=test");

        mockMvc.perform(get("/api/inspiration-creations/864900000000000001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.externalId", is("864900000000000001")))
            .andExpect(jsonPath("$.data.title", is("逆袭归来")))
            .andExpect(jsonPath("$.data.localUrl", is("https://minio.aixmax.cn/ant-short-tv/inspiration-creations/public/864900000000000001/cover.jpg?X-Amz-Signature=test")))
            .andExpect(jsonPath("$.data.detailJson", is("{\"prompt\":\"被误解的女主多年后带着证据回归。\"}")))
            .andExpect(jsonPath("$.data.importError").doesNotExist());
    }

    @Test
    void servesInspirationMediaFromObjectStoragePath() throws Exception {
        when(objectStorageService.resource("inspiration-creations/public/864900000000000001/cover.jpg"))
            .thenReturn(new ByteArrayResource("image".getBytes()));

        mockMvc.perform(get("/api/inspiration-creations/864900000000000001/file"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_JPEG))
            .andExpect(content().bytes("image".getBytes()));
    }
}
