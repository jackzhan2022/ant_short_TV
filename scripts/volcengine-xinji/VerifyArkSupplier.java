import com.antshorttv.ai.*;
import com.antshorttv.video.*;
import com.antshorttv.accounting.AiUsageMetric;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.*;
import java.util.*;

public class VerifyArkSupplier {
    @SuppressWarnings("unchecked")
    static <T> T mapper(Class<T> type, Object row) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> row);
    }
    static void require(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
    public static void main(String[] args) throws Exception {
        for (String name : List.of("com.antshorttv.freecreation.FreeCreationService",
                "com.antshorttv.freecreation.FreeCreationExecutionHandler", "com.antshorttv.video.VideoReferenceDiagnostics")) {
            try { Class.forName(name, true, VerifyArkSupplier.class.getClassLoader()); }
            catch (ClassNotFoundException missing) { if (!name.endsWith("VideoReferenceDiagnostics")) throw missing; }
        }
        ObjectMapper json = new ObjectMapper();
        SeedanceArkVideoProviderAdapter adapter = new SeedanceArkVideoProviderAdapter(null, json);
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var videos = (AiVideoTaskService) unsafe.allocateInstance(AiVideoTaskService.class);
        var jsonField = AiVideoTaskService.class.getDeclaredField("objectMapper"); jsonField.setAccessible(true); jsonField.set(videos, json);
        var isSeedance = AiVideoTaskService.class.getDeclaredMethod("isSeedance", AiModelRoute.class); isSeedance.setAccessible(true);
        Map<?, ?> originalUsage = null, originalDimensions = null;
        for (String code : List.of("VOLCENGINE_ARK", "VOLCENGINE_ARK_XINJI")) {
            var model = new AiModelEntity(); model.setId(5L); model.setProviderId(7L); model.setCode("SEEDANCE_2_0_STANDARD");
            model.setServiceType("VIDEO"); model.setStatus("ENABLED");
            var provider = new AiProviderEntity(); provider.setId(7L); provider.setCode(code); provider.setStatus("ENABLED");
            var config = new AiProviderConfigEntity(); config.setProviderId(7L); config.setApiKeyCipher("offline-test"); config.setStatus("ENABLED");
            var router = new AiModelRouter(mapper(AiModelMapper.class, model), mapper(AiProviderMapper.class, provider),
                mapper(AiProviderConfigMapper.class, config), mapper(AiModelCapabilityMapper.class, new AiModelCapabilityEntity()), List.of(adapter));
            var route = router.route(5L, "VIDEO");
            require(route.provider().getCode().equals(code), "Supplier identity must be retained");
            require(route.providerConfig() == config && route.adapter() == adapter, "Supplier configuration or adapter mismatch");
            require(Boolean.TRUE.equals(isSeedance.invoke(videos, route)), "Native Ark validation not selected");
            var task = new AiVideoTaskEntity(); task.providerCode = code; task.resolution = "720P"; task.aspectRatio = "9:16"; task.requestSnapshotJson = "{}";
            var usage = videos.videoAuthorizedUsage(task, 5); var dimensions = videos.videoDimensions(task);
            require(usage.containsKey(AiUsageMetric.OUTPUT_TOKEN), "Ark token billing missing");
            require("720p".equals(dimensions.get("resolution")), "Ark pricing dimensions not normalized");
            if (originalUsage == null) { originalUsage = usage; originalDimensions = dimensions; }
            else require(originalUsage.equals(usage) && originalDimensions.equals(dimensions), "Billing changed after supplier migration");
        }
        require(VolcengineProviderCodes.matches("RUNNING", "running"), "Existing status comparisons changed");
        require(!VolcengineProviderCodes.matches("SUCCEEDED", "VOLCENGINE_ARK_XINJI"), "Unrelated comparisons changed");
        System.out.println("PASS: exact deployed candidate routes both suppliers, retains supplier identity and config, and preserves native validation and billing.");
    }
}
