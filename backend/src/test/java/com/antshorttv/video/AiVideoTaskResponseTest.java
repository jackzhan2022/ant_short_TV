package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AiVideoTaskResponseTest {
    @Test
    void identifiesRejectedImagesUsingSubmittedOrderRatherThanImageLabels() {
        AiVideoTaskEntity task = task();
        task.requestSnapshotJson = """
            {"references":[
              {"reference":{"mediaType":"IMAGE","compiledLabel":"图片3","displayName":"Serena"},
               "displayName":"晚宴礼服","browserDisplayUrl":"/materials/serena.png",
               "providerUrl":"https://provider.example/private?token=secret"},
              {"reference":{"mediaType":"VIDEO","compiledLabel":"视频1","displayName":"动作参考"}},
              {"reference":{"mediaType":"IMAGE","compiledLabel":"图片1","displayName":"Rowan"},
               "displayName":"Rowan","browserDisplayUrl":"/materials/rowan.png"}
            ],"omittedReferences":[{"displayName":"未提交图片"}]}
            """;
        task.errorMessage = "服务商提交失败：PrivacyInformation: input image 'content[1]' 'content[3]' "
            + "may contain real person. content[1] content[2] content[0] content[99]. Request id: request-1";

        AiVideoTaskResponse response = AiVideoTaskResponse.from(task, List.of());

        assertThat(response.errorMessage()).contains("图片3（Serena / 晚宴礼服）", "图片1（Rowan）",
            "content[2]", "content[0]", "content[99]", "Request id: request-1")
            .doesNotContain("content[1]", "content[3]", "未提交图片", "token=secret");
        var images = response.referenceDiagnostics().path("failedImages");
        assertThat(images).hasSize(2);
        assertThat(images.get(0).path("contentIndex").asInt()).isEqualTo(1);
        assertThat(images.get(0).path("imageUrl").asText()).isEqualTo("/materials/serena.png");
        assertThat(images.get(1).path("contentIndex").asInt()).isEqualTo(3);
        assertThat(images.get(1).path("displayName").asText()).isEqualTo("Rowan");
        assertThat(images.toString()).doesNotContain("provider.example", "token=secret");
        // Reading historical failures enriches the response without changing stored diagnostics.
        assertThat(task.errorMessage).contains("content[1]", "content[3]");
    }

    @Test
    void identifiesTheFirstFrameForLegacyTasksWithoutASnapshot() {
        AiVideoTaskEntity task = task();
        task.firstFrameUrl = "/materials/first-frame.png";
        task.errorMessage = "input image content[1] rejected";

        AiVideoTaskResponse response = AiVideoTaskResponse.from(task, List.of());

        assertThat(response.errorMessage()).isEqualTo("input image 首帧图（分镜首帧） rejected");
        assertThat(response.referenceDiagnostics().at("/failedImages/0/imageUrl").asText())
            .isEqualTo(task.firstFrameUrl);
    }

    @Test
    void doesNotGuessImagesForMissingInvalidOrTextOnlySnapshots() {
        for (String snapshot : new String[] {null, "{", "{}", "{\"references\":[]}"}) {
            AiVideoTaskEntity task = task();
            task.requestSnapshotJson = snapshot;
            task.errorMessage = "input image content[1] content[9999999999999] rejected";
            AiVideoTaskResponse response = AiVideoTaskResponse.from(task, List.of());
            assertThat(response.errorMessage()).isEqualTo(task.errorMessage);
            assertThat(response.referenceDiagnostics().path("failedImages").size()).isZero();
        }
    }

    @Test
    void doesNotUseAStaleFirstFrameForTextOnlyTasksOrErrorsWithoutAnIndex() {
        AiVideoTaskEntity task = task();
        task.firstFrameUrl = "/materials/stale.png";
        task.requestSnapshotJson = "{\"references\":[]}";
        task.errorMessage = "content[1] rejected";
        assertThat(AiVideoTaskResponse.from(task, List.of()).errorMessage()).isEqualTo(task.errorMessage);
        task.requestSnapshotJson = null;
        task.errorMessage = "input image may contain real person";
        assertThat(AiVideoTaskResponse.from(task, List.of()).referenceDiagnostics().path("failedImages")).isEmpty();
        task.errorMessage = null;
        assertThat(AiVideoTaskResponse.from(task, List.of()).errorMessage()).isNull();
    }

    private AiVideoTaskEntity task() {
        AiVideoTaskEntity task = new AiVideoTaskEntity();
        task.providerCode = "VOLCENGINE_ARK";
        task.status = "FAILED";
        return task;
    }
}
