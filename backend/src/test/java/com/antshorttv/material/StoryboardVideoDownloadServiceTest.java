package com.antshorttv.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StoryboardVideoDownloadServiceTest {
    JdbcTemplate jdbc;
    ProjectPermissionGuard permissions;
    MaterialFileAccessService files;
    StoryboardVideoDownloadService service;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("create table project(id bigint primary key, tenant_id bigint, deleted_at timestamp)");
        jdbc.execute("create table storyboard(id bigint primary key, tenant_id bigint, project_id bigint, episode_no int, shot_no int, current_video_result_id bigint, current_video_url varchar(300), current_shot_result_id bigint, current_shot_video_url varchar(300), deleted_at timestamp)");
        for (String table : new String[]{"ai_video_result", "shot_compose_result"}) {
            jdbc.execute("create table " + table + "(id bigint primary key, tenant_id bigint, project_id bigint, storyboard_id bigint, storage_path varchar(300), file_size bigint, status varchar(20))");
        }
        jdbc.update("insert into project values(22,11,null),(23,12,null)");
        jdbc.update("insert into storyboard values(31,11,22,1,2,201,'/raw31.mp4',null,null,null),(32,11,22,1,1,202,'/raw32.mp4',301,'/composed32.mp4',null),(33,11,22,1,3,null,null,null,null,null),(41,11,22,2,1,203,'/raw41.mp4',null,null,null),(51,12,23,1,1,204,'/foreign.mp4',null,null,null)");
        jdbc.update("insert into ai_video_result values(201,11,22,31,'materials/11/22/raw31.mp4',5,'ACTIVE'),(202,11,22,32,'materials/11/22/raw32.mp4',5,'ACTIVE'),(203,11,22,41,'materials/11/22/raw41.mp4',5,'ACTIVE'),(204,12,23,51,'materials/12/23/foreign.mp4',5,'ACTIVE')");
        jdbc.update("insert into shot_compose_result values(301,11,22,32,'materials/11/22/composed32.mp4',5,'ACTIVE')");
        for (String table : new String[]{"ai_video_result", "shot_compose_result"}) {
            jdbc.update("update " + table + " set file_size=char_length(storage_path)");
        }
        permissions = mock(ProjectPermissionGuard.class);
        files = mock(MaterialFileAccessService.class);
        when(permissions.require(eq(11L), eq(22L), anyString())).thenReturn(new TenantContext(33L,11L,44L,"MEMBER"));
        when(files.resource(anyString())).thenAnswer(call -> new ByteArrayResource(((String) call.getArgument(0)).getBytes(StandardCharsets.UTF_8)));
        service = new StoryboardVideoDownloadService(jdbc, permissions, files, new ObjectStorageKeyFactory());
    }

    @Test
    void selectedShotPrefersComposedResultAndChecksDownloadPermission() throws Exception {
        var result = service.downloadStoryboard(22L,32L);
        assertThat(new String(result.resource().getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("materials/11/22/composed32.mp4");
        assertThat(result.fileName()).contains("01", "分镜", ".mp4");
        verify(permissions).require(11L,22L,"SHOT_COMPOSE:DOWNLOAD");
        verify(files,never()).resource("materials/11/22/raw32.mp4");
    }

    @Test
    void archiveContainsOnlyThisEpisodeInShotOrderAndCleansTemporaryFile() throws Exception {
        var result = service.downloadEpisode(22L,1);
        Path archive = result.resource().getFile().toPath();
        assertThat(archive).exists();
        var names = new ArrayList<String>();
        var contents = new ArrayList<String>();
        try (var zip = new ZipInputStream(result.resource().getInputStream(), StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) { names.add(entry.getName()); contents.add(new String(zip.readAllBytes(), StandardCharsets.UTF_8)); }
        }
        assertThat(names).hasSize(2);
        assertThat(names.get(0)).startsWith("001_");
        assertThat(names.get(1)).startsWith("002_");
        assertThat(contents).containsExactly("materials/11/22/composed32.mp4", "materials/11/22/raw31.mp4");
        assertThat(archive).doesNotExist();
        verify(files,never()).resource("materials/11/22/raw41.mp4");
        verify(permissions).require(11L,22L,"AI_VIDEO_RESULT:DOWNLOAD");
    }

    @Test
    void rejectsForeignShotsAndObjectKeys() {
        assertThatThrownBy(() -> service.downloadStoryboard(22L,51L)).isInstanceOf(BusinessException.class);
        jdbc.update("update ai_video_result set storage_path='materials/12/23/secret.mp4' where id=201");
        assertThatThrownBy(() -> service.downloadStoryboard(22L,31L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(files);
    }

    @Test
    void doesNotSilentlyFallbackToRawWhenTheSelectedCompositionWasDeleted() {
        jdbc.update("update shot_compose_result set status='DELETED' where id=301");
        assertThatThrownBy(() -> service.downloadStoryboard(22L,32L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(files);
    }

    @Test
    void checksAllPermissionsBeforeReadingAnyFiles() {
        when(permissions.require(11L,22L,"AI_VIDEO_RESULT:DOWNLOAD")).thenThrow(new BusinessException(ErrorCode.FORBIDDEN,"无权限"));
        assertThatThrownBy(() -> service.downloadEpisode(22L,1)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(files);
    }

    @Test
    void rejectsEmptyEpisodesAndInvalidEpisodeNumbers() {
        assertThatThrownBy(() -> service.downloadEpisode(22L,99)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.downloadEpisode(22L,0)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(files);
    }

    @Test
    void cleansPartialArchivesIfOneObjectCannotBeRead() throws Exception {
        Set<Path> before = temporaryArchives();
        when(files.resource("materials/11/22/raw31.mp4")).thenReturn(new AbstractResource() {
            public String getDescription() { return "broken object"; }
            public java.io.InputStream getInputStream() throws IOException { throw new IOException("missing object"); }
        });
        assertThatThrownBy(() -> service.downloadEpisode(22L,1)).isInstanceOf(IOException.class);
        assertThat(temporaryArchives()).isEqualTo(before);
    }

    @Test
    void controllerReturnsBinaryAttachmentInsteadOfJsonOrRedirect() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new StoryboardVideoDownloadController(service)).build();
        mvc.perform(get("/api/projects/22/storyboards/32/download-video"))
            .andExpect(status().isOk()).andExpect(content().contentType("video/mp4"))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andExpect(content().bytes("materials/11/22/composed32.mp4".getBytes(StandardCharsets.UTF_8)));
        mvc.perform(get("/api/projects/22/episodes/1/download-videos"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/zip"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".zip")));
    }

    @Test
    void includesAllAvailableShotsBeyondTheWorkspacePageSize() throws Exception {
        for (int i = 1; i <= 105; i++) {
            jdbc.update("insert into storyboard values(?,11,22,3,?,?,'/available.mp4',null,null,null)", 1000+i, i, 2000+i);
            jdbc.update("insert into ai_video_result values(?,11,22,?,'materials/11/22/extra.mp4',5,'ACTIVE')", 2000+i, 1000+i);
        }
        int count = 0;
        try (var zip = new ZipInputStream(service.downloadEpisode(22L,3).resource().getInputStream(), StandardCharsets.UTF_8)) {
            while (zip.getNextEntry() != null) { zip.readAllBytes(); count++; }
        }
        assertThat(count).isEqualTo(105);
    }

    @Test
    void deniedProjectAccessCannotReadObjects() {
        when(permissions.require(11L,22L,"PROJECT:VIEW")).thenThrow(new BusinessException(ErrorCode.FORBIDDEN,"无权访问"));
        assertThatThrownBy(() -> service.downloadStoryboard(22L,31L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(files);
    }

    @Test
    void headRequestsDoNotLeaveTemporaryArchives() throws Exception {
        Set<Path> before = temporaryArchives();
        MockMvcBuilders.standaloneSetup(new StoryboardVideoDownloadController(service)).build()
            .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head("/api/projects/22/episodes/1/download-videos"))
            .andExpect(status().isOk());
        assertThat(temporaryArchives()).isEqualTo(before);
    }

    @Test
    void rangeRequestsCannotLeakTemporaryArchives() throws Exception {
        Set<Path> before = temporaryArchives();
        var mvc = MockMvcBuilders.standaloneSetup(new StoryboardVideoDownloadController(service)).build();
        for (String range : new String[]{"bytes=", "bytes=0-10", "bytes=999999999-"}) {
            byte[] bytes = mvc.perform(get("/api/projects/22/episodes/1/download-videos").header("Range", range))
                .andExpect(status().isOk()).andExpect(content().contentType("application/zip"))
                .andReturn().getResponse().getContentAsByteArray();
            var contents = new ArrayList<String>();
            try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
                while (zip.getNextEntry() != null) contents.add(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertThat(contents).containsExactly("materials/11/22/composed32.mp4", "materials/11/22/raw31.mp4");
            assertThat(temporaryArchives()).isEqualTo(before);
        }
    }

    private Set<Path> temporaryArchives() throws IOException {
        try (var stream = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return stream.filter(path -> path.getFileName().toString().startsWith("antv-episode-videos-")).collect(Collectors.toSet());
        }
    }
}
