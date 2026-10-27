package com.antshorttv.points;

import com.antshorttv.accounting.*;
import com.antshorttv.ai.AiModelRouter;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.aiimage.AiImageTaskService;
import com.antshorttv.aiimage.AssetImageBatchService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionAccessService;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.productiontask.ProductionTaskService;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.review.ReviewWorkbenchService;
import com.antshorttv.review.ReviewContentService;
import com.antshorttv.review.ReviewUnitPlanner;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.video.AiVideoTaskService;
import com.antshorttv.video.VideoDecompositionService;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Prices a proposed UI operation without executing it or changing points. */
@Service
public class AiPointEstimateService {
    private final JdbcTemplate jdbc;
    private final TenantContextResolver tenants;
    private final ProjectPermissionGuard permissions;
    private final ProjectAiConfigService configs;
    private final AiModelRouter router;
    private final AiImageTaskService images;
    private final AiVideoTaskService videos;
    private final AssetImageBatchService imageBatches;
    private final ReviewWorkbenchService reviews;
    private final VideoDecompositionService decomposition;
    private final ProductionTaskService productionTasks;
    private final AiExecutionAccessService executionAccess;
    private final AiExecutionService executions;
    private final AiPointReservationMapper reservations;
    private final AiModelPointPriceVersionMapper pointVersions;
    private final AiModelPointPriceComponentMapper components;
    private final WorkflowAgentRunner agents;
    private final AiPointEstimateCalculator calculator;
    private final ObjectMapper json;
    private final WorkbenchPointPolicy workbenchPolicy;
    @org.springframework.beans.factory.annotation.Autowired
    private com.antshorttv.freecreation.FreeCreationService freeCreations;
    @org.springframework.beans.factory.annotation.Value("${review.workflow.deep-unit-characters:24000}")
    private int reviewUnitCharacters = 24000;
    @org.springframework.beans.factory.annotation.Value("${review.workflow.deep-unit-overlap:1200}")
    private int reviewUnitOverlap = 1200;

    public AiPointEstimateService(JdbcTemplate jdbc, TenantContextResolver tenants, ProjectPermissionGuard permissions,
        ProjectAiConfigService configs, AiModelRouter router, AiImageTaskService images, AiVideoTaskService videos,
        AssetImageBatchService imageBatches, ReviewWorkbenchService reviews, VideoDecompositionService decomposition,
        ProductionTaskService productionTasks, AiExecutionAccessService executionAccess, AiExecutionService executions,
        AiPointReservationMapper reservations, AiModelPointPriceVersionMapper pointVersions,
        AiModelPointPriceComponentMapper components, WorkflowAgentRunner agents,
        AiPointEstimateCalculator calculator, ObjectMapper json, WorkbenchPointPolicy workbenchPolicy) {
        this.jdbc=jdbc; this.tenants=tenants; this.permissions=permissions; this.configs=configs; this.router=router;
        this.images=images; this.videos=videos; this.imageBatches=imageBatches; this.reviews=reviews;
        this.decomposition=decomposition; this.productionTasks=productionTasks; this.executionAccess=executionAccess;
        this.executions=executions; this.reservations=reservations; this.pointVersions=pointVersions;
        this.workbenchPolicy=workbenchPolicy;
        this.components=components; this.agents=agents; this.calculator=calculator; this.json=json;
    }

    @Transactional(readOnly = true)
    public Estimate estimate(Long tenantId, List<Operation> operations) {
        tenants.requireActiveMember(tenantId);
        var planning = new PlanningSession();
        var pricing = new AiPointEstimateCalculator.Context(tenantId);
        List<AiPointEstimatePlan> plans = new ArrayList<>();
        List<AiPointEstimateCalculator.Line> lines = new ArrayList<>();
        Map<String, BigDecimal> remainingByProject = new HashMap<>();
        int operationIndex = 0;
        for (var operation : operations) {
            var operationPlans = planning.plan(tenantId, operation);
            plans.addAll(operationPlans);
            String newProjectScope = "new:" + operationIndex++;
            for (var plan : operationPlans) {
                var line = calculator.calculate(tenantId, plan, pricing);
                if (plan.workbench()) {
                    String scope = plan.projectId() == null ? newProjectScope : "project:" + plan.projectId();
                    BigDecimal remaining = remainingByProject.computeIfAbsent(scope,
                        ignored -> workbenchPolicy.estimateRemaining(tenantId, plan.projectId()));
                    BigDecimal points = line.points().min(remaining);
                    remainingByProject.put(scope, remaining.subtract(points));
                    line = new AiPointEstimateCalculator.Line(line.label(), line.modelId(), line.pointPriceVersionId(),
                        points, line.estimated(), line.trialEligible());
                }
                lines.add(line);
            }
        }
        BigDecimal total = lines.stream().map(AiPointEstimateCalculator.Line::points).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = lines.stream().filter(line -> !line.trialEligible())
            .map(AiPointEstimateCalculator.Line::points).reduce(BigDecimal.ZERO, BigDecimal::add);
        var accounts = jdbc.queryForList("select balance, trial_balance from team_point_account where tenant_id = ?", tenantId);
        BigDecimal available = accounts.isEmpty() ? BigDecimal.ZERO : decimal(accounts.get(0).get("balance"));
        BigDecimal trial = accounts.isEmpty() ? BigDecimal.ZERO : decimal(accounts.get(0).get("trial_balance"));
        return new Estimate(total, available, available.subtract(trial),
            total.compareTo(available) <= 0 && paid.compareTo(available.subtract(trial)) <= 0,
            lines, "文本按当前上下文及输出预算估算，视频按计费预算估算；实际按模型用量结算，未使用的预占积分会释放。自动分析包含后续自动分镜的预估；技术重试产生的新调用另按实际用量计费。"
                + (plans.stream().anyMatch(AiPointEstimatePlan::workbench)
                    ? "工作台剧本分析与分镜按套餐折扣后积分的20%计费，同一项目本阶段累计最多1000积分（含已扣费与重试）；达到上限后不再追加扣费。" : ""));
    }

    /** All memoized reads live only for the current estimate invocation. */
    private final class PlanningSession {
        private final Map<String, com.antshorttv.ai.AiModelRoute> routes = new HashMap<>();
        private final Map<String, com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan> agentPlans = new HashMap<>();
        private final Map<Long, Long> effectivePointVersions = new HashMap<>();
        private final Map<Long, List<AiModelPointPriceComponentEntity>> priceComponents = new HashMap<>();
        private final LocalDateTime at = LocalDateTime.now();
        private final Map<String,Long> configuredModels = new HashMap<>();
        private final Map<String,String> projectScripts = new HashMap<>();
        private final Map<String,Set<Long>> storyboardEpisodes = new HashMap<>();

        private Long resolveModel(Long tenant,Long project,String capability) {
            return configuredModels.computeIfAbsent(tenant+":"+project+":"+capability,
                ignored -> configs.resolveModelId(tenant,project,capability));
        }

    private List<AiPointEstimatePlan> plan(Long tenant, Operation operation) {
        String path = operation.url();
        if ("/api/free-creations".equals(path)) return List.of(freeCreations.pointEstimate(tenant, operation.data()));
        JsonNode data = operation.data() == null ? json.createObjectNode() : operation.data();
        var project = Pattern.compile("^/api/projects/(\\d+)/(.*)$").matcher(path);
        if (project.matches()) return projectPlan(tenant, Long.valueOf(project.group(1)), project.group(2), data);
        if ("/api/projects".equals(path)) {
            String content = data.path("initialScriptContent").asText("").trim();
            return content.isEmpty() ? List.of() : analysis(tenant, null, content);
        }
        if ("/api/script-review/submissions".equals(path)) {
            String content = data.path("content").asText("").trim();
            List<String> requestedDimensions = new ArrayList<>();
            data.path("selectedDimensions").forEach(value -> requestedDimensions.add(value.asText()));
            List<String> dimensions;
            try {
                dimensions = com.antshorttv.review.ReviewDimension.parseAll(requestedDimensions).stream()
                    .map(com.antshorttv.review.ReviewDimension::label).toList();
            } catch (IllegalArgumentException exception) {
                throw invalid("包含未知审核维度。");
            }
            if (content.isBlank() || content.length() > 200000 || dimensions.isEmpty())
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请输入有效剧本并选择审核维度。");
            var config = json.createObjectNode();
            config.put("reviewMode", "DEEP");
            config.put("reviewScopeType", "ALL");
            config.set("selectedDimensions", json.valueToTree(dimensions));
            return review(tenant, content, config, reviews.pointEstimateModelId(tenant), null);
        }
        var review = Pattern.compile("^/api/script-review/(projects|tasks)/(\\d+)/(tasks|retry)$").matcher(path);
        if (review.matches()) {
            long id = Long.parseLong(review.group(2));
            if ("tasks".equals(review.group(1))) return retryReview(tenant, id);
            reviews.detailProject(tenant, id);
            Long version = nullableLong(data, "versionId");
            if (version == null) version = number(row("select current_version_id from review_project where tenant_id=? and id=?", tenant, id), "current_version_id");
            String content = text(row("select content from review_script_version where tenant_id=? and project_id=? and id=?", tenant, id, version), "content");
            return review(tenant, content, data, reviews.pointEstimateModelId(tenant), null);
        }
        if ("/api/video-script-decomposition/batches".equals(path)) {
            Long projectId = nullableLong(data, "projectId");
            if (projectId != null) permissions.require(tenant, projectId, "AI_SERVICE:USE");
            List<AiPointEstimatePlan> plans = new ArrayList<>();
            for (var ignored : data.path("videos")) plans.addAll(decomposition(tenant, projectId, nullableLong(data, "modelId")));
            return plans;
        }
        var videoEpisode = Pattern.compile("^/api/video-script-decomposition/episodes/(\\d+)/retry$").matcher(path);
        if (videoEpisode.matches()) return retryDecomposition(tenant, Long.parseLong(videoEpisode.group(1)));
        var execution = Pattern.compile("^/api/tenants/(\\d+)/ai-executions/(\\d+)/(retry|regenerate)$").matcher(path);
        if (execution.matches() && tenant.equals(Long.valueOf(execution.group(1))))
            return execution(tenant, Long.parseLong(execution.group(2)), "retry".equals(execution.group(3)));
        var task = Pattern.compile("^/api/tenants/(\\d+)/production-tasks/([^/]+)/(retry|regenerate)$").matcher(path);
        if (task.matches() && tenant.equals(Long.valueOf(task.group(1)))) {
            String key = java.net.URLDecoder.decode(task.group(2), StandardCharsets.UTF_8);
            Map<String,Object> detail = productionTasks.detail(tenant, key);
            String action = task.group(3).toUpperCase(Locale.ROOT);
            if (!(detail.get("allowedActions") instanceof List<?> allowed) || !allowed.contains(action)) throw invalid("该任务当前不可执行此操作。");
            long id = Long.parseLong(key.split(":")[1]);
            Long projectId = number(detail, "projectId");
            return switch (String.valueOf(detail.get("type"))) {
                case "FREE_CREATION" -> List.of(freeCreations.pointEstimateControl(tenant,id,action));
                case "IMAGE" -> List.of(images.pointEstimateExisting(tenant, projectId, id));
                case "REVIEW" -> retryReview(tenant, id);
                case "VIDEO_EPISODE" -> retryDecomposition(tenant, id);
                case "SCRIPT_OPERATION" -> execution(tenant, number(row("select execution_id from script_ai_operation where tenant_id=? and id=?", tenant, id), "execution_id"), true);
                case "STORYBOARD_ITEM" -> execution(tenant, number(row("select execution_id from storyboard_batch_item where tenant_id=? and id=?", tenant, id), "execution_id"), true);
                default -> throw invalid("该任务没有可预估的 AI 操作。");
            };
        }
        throw invalid("该操作尚不支持积分预估，已阻止提交。");
    }

    private List<AiPointEstimatePlan> projectPlan(Long tenant, Long project, String path, JsonNode data) {
        return projectPlan(tenant, project, path, data, null, null);
    }

    private List<AiPointEstimatePlan> projectPlan(Long tenant, Long project, String path, JsonNode data,
                                                Long frozenModel, AiPointReservationEntity reservation) {
        return projectPlans(tenant, project, path, data, frozenModel, reservation).stream()
            .map(plan -> plan.inProject(project)).toList();
    }

    private List<AiPointEstimatePlan> projectPlans(Long tenant, Long project, String path, JsonNode data,
                                                 Long frozenModel, AiPointReservationEntity reservation) {
        permissions.require(tenant, project, "AI_SERVICE:USE");
        if ("ai-image-tasks".equals(path)) return List.of(images.pointEstimateRequest(tenant, project, data));
        if (path.matches("ai-image-tasks/\\d+/regenerate")) return List.of(images.pointEstimateExisting(tenant, project, pathId(path, 1)));
        if ("ai-video-tasks".equals(path)) return List.of(videos.pointEstimateRequest(tenant, project, data));
        if (path.matches("ai-video-tasks/\\d+/regenerate")) return List.of(videos.pointEstimateExisting(tenant, project, pathId(path, 1)));
        if ("asset-image-batches".equals(path)) return imageBatches.pointEstimateRequest(tenant, project, data);
        if (path.matches("asset-image-batches/\\d+/retry-failed")) return imageBatches.pointEstimatesRetry(tenant, project, pathId(path, 1));
        Long model = frozenModel == null ? resolveModel(tenant, project, "TEXT") : frozenModel;
        String content = scriptContent(tenant, project);
        if (path.startsWith("script-analysis/current/retry/")) {
            var tasks = jdbc.queryForList("select execution_id from script_analysis_task where tenant_id=? and project_id=? order by id desc limit 1", tenant, project);
            Long executionId = tasks.isEmpty() ? null : number(tasks.get(0),"execution_id");
            if (executionId != null && !"CANCELED".equals(executions.requireTask(executionId).status))
                return execution(tenant, executionId, true);
            return analysis(tenant, project, content);
        }
        if ("script-analysis/current/reanalyze".equals(path)) return analysis(tenant, project, content);
        if (path.matches("script-analysis/versions/\\d+/reanalyze")) {
            String version = text(row("select content from script_version where tenant_id=? and project_id=? and id=?", tenant, project, pathId(path, 2)), "content");
            return analysis(tenant, project, version);
        }
        if ("script-analysis/current/regenerate-episodes".equals(path))
            return List.of(textPlan(tenant, "重新拆分剧集", model, content, "short-drama-episode-splitting", 2, reservation));
        if (path.matches("episodes/\\d+/(summary|assets)/regenerate")) {
            String body = episodeContent(tenant, project, pathId(path, 1));
            if (path.contains("/summary/")) return List.of(textPlan(tenant, "重新生成本集概要", model, body, "short-drama-episode-summary", 2, reservation));
            List<AiPointEstimatePlan> plans = new ArrayList<>();
            plans.add(textPlan(tenant, "重新识别本集资产", model, body, "short-drama-asset-recognition", 3, reservation));
            plans.add(assetNormalization(tenant,project,model,content,reservation));
            if (!hasStoryboard(tenant, project, pathId(path, 1))) plans.add(storyboard(tenant, model, body));
            return plans;
        }
        if ("storyboards/ai-breakdown".equals(path)) return List.of(textPlan(tenant, "生成分镜", model, episodeContent(tenant, project, requiredLong(data, "episodeId")), "short-drama-storyboard", 2, reservation));
        if ("storyboard-batches".equals(path)) {
            List<AiPointEstimatePlan> plans = new ArrayList<>();
            for (JsonNode id : data.path("episodeIds")) plans.add(storyboard(tenant, model, episodeContent(tenant, project, id.longValue())));
            return plans;
        }
        return switch (path) {
            case "asset-normalization" -> List.of(assetNormalization(tenant,project,model,content,reservation));
            case "prop-simplification" -> List.of(propSimplification(tenant,project,model,content,reservation));
            case "character-prompt-similarity" -> List.of(characterSimilarity(tenant,project,model,reservation));
            case "asset-reextraction" -> assetExtraction(tenant, project, model);
            case "scripts/ai-generate" -> List.of(textPlan(tenant, "AI 生成剧本", model, data.toString(), null, 1, reservation));
            case "scripts/ai-rewrite" -> List.of(textPlan(tenant, "AI 改写剧本", model, content + data, null, 1, reservation));
            case "prompts/ai-generate" -> List.of(textPlan(tenant, "AI 生成提示词", model, content + data, null, 1, reservation));
            default -> throw invalid("该项目操作尚不支持积分预估。");
        };
    }

    private List<AiPointEstimatePlan> analysis(Long tenant, Long project, String content) {
        return analysis(tenant, project, content, resolveModel(tenant, project, "TEXT"), null);
    }

    private List<AiPointEstimatePlan> analysis(Long tenant, Long project, String content, Long model,
                                             AiPointReservationEntity reservation) {
        List<AiPointEstimatePlan> plans = new ArrayList<>();
        plans.add(textPlan(tenant, "剧本全局理解", model, content, "short-drama-global-understanding", 2, reservation));
        plans.add(textPlan(tenant, "剧集智能拆分", model, content, "short-drama-episode-splitting", 2, reservation));
        List<Map<String,Object>> episodes = project == null ? List.of() : jdbc.queryForList(
            "select id, content from script_episode where tenant_id=? and project_id=? and status='ACTIVE' and retired_at is null order by episode_no", tenant, project);
        if (episodes.isEmpty()) {
            for (var episode : com.antshorttv.script.ScriptEpisodeParser.parse(content)) {
                plans.add(textPlan(tenant, "第" + episode.episodeNo() + "集概要（按集标题估算）", model, episode.content(), "short-drama-episode-summary", 2, reservation));
                plans.add(textPlan(tenant, "第" + episode.episodeNo() + "集资产识别", model, episode.content(), "short-drama-asset-recognition", 3, reservation));
                plans.add(storyboard(tenant, model, episode.content()));
            }
        } else for (var episode : episodes) {
            String body = text(episode, "content");
            plans.add(textPlan(tenant, "分集概要", model, body, "short-drama-episode-summary", 2, reservation));
            plans.add(textPlan(tenant, "分集资产识别", model, body, "short-drama-asset-recognition", 3, reservation));
            if (!hasStoryboard(tenant, project, number(episode, "id"))) plans.add(storyboard(tenant, model, body));
        }
        plans.add(assetNormalization(tenant,project,model,content,reservation));
        return plans.stream().map(plan -> plan.inProject(project)).toList();
    }

    private List<AiPointEstimatePlan> assetExtraction(Long tenant, Long project, Long model) {
        var episodes = jdbc.queryForList("select id, content from script_episode where tenant_id=? and project_id=? and status='ACTIVE' and retired_at is null order by episode_no", tenant, project);
        if (episodes.isEmpty()) throw invalid("请先完成剧集拆分后再提取资产。");
        List<AiPointEstimatePlan> plans = new ArrayList<>();
        for (var episode : episodes) {
            plans.add(textPlan(tenant, "分集资产提取及提示词生成", model, text(episode,"content"), "short-drama-asset-recognition", 3));
            if (!hasStoryboard(tenant, project, number(episode,"id"))) plans.add(storyboard(tenant, model,text(episode,"content")));
        }
        plans.add(assetNormalization(tenant,project,model,scriptContent(tenant,project),null));
        return plans;
    }

    private AiPointEstimatePlan characterSimilarity(Long tenant, Long project, Long model, AiPointReservationEntity reservation) {
        permissions.require(tenant, project, "ELEMENT:AI_EXTRACT");
        var input = jdbc.queryForList("""
            select c.id,c.name,c.prompt from character_asset c
            where c.tenant_id=? and c.project_id=? and c.deleted_at is null and c.merge_target_id is null
              and c.script_id=(select s.id from script s where s.tenant_id=? and s.project_id=?
                and s.deleted_at is null order by s.id desc limit 1) order by c.id
            """, tenant, project, tenant, project);
        com.fasterxml.jackson.databind.JsonNode snapshot = json.valueToTree(input);
        com.antshorttv.script.CharacterVariantSimilarityAnalysis.capture(jdbc, json, tenant, project, snapshot);
        return textPlan(tenant, "角色形态相似度检查", model,
            com.antshorttv.script.CharacterPromptSimilarityAnalysis.INSTRUCTIONS + snapshot
                + com.antshorttv.script.CharacterPromptSimilarityAnalysis.schema(json), null, 1, reservation,
            com.antshorttv.script.CharacterPromptSimilarityAnalysis.OUTPUT_TOKENS);
    }

    private AiPointEstimatePlan propSimplification(Long tenant,Long project,Long model,String content,AiPointReservationEntity reservation) {
        StringBuilder corpus=new StringBuilder(content);
        for(String table:List.of("prop_asset","scene_asset")) corpus.append(jdbc.queryForList(
            "select id,name,prompt,content_json from "+table+" where tenant_id=? and project_id=? and deleted_at is null order by id",tenant,project));
        corpus.append(jdbc.queryForList("select id,name,prompt from asset_visual_variant where tenant_id=? and project_id=? and asset_type='PROP' and deleted_at is null order by id",tenant,project));
        return textPlan(tenant,"道具精简方案分析",model,corpus.toString(),"short-drama-prop-simplification",1,reservation);
    }

    private AiPointEstimatePlan assetNormalization(Long tenant,Long project,Long model,String content,AiPointReservationEntity reservation) {
        StringBuilder corpus=new StringBuilder(content);
        if(project!=null) for(String table:List.of("character_asset","scene_asset","prop_asset"))
            corpus.append(jdbc.queryForList("select name,content_json from "+table+" where tenant_id=? and project_id=? and deleted_at is null order by id",tenant,project));
        return textPlan(tenant,"全剧资产自动归一",model,corpus.toString(),"short-drama-asset-normalization",3,reservation);
    }

    private AiPointEstimatePlan storyboard(Long tenant, Long model, String content) {
        return textPlan(tenant, "生成分镜（含自动分镜）", model, content, "short-drama-storyboard", 2);
    }

    private AiPointEstimatePlan textPlan(Long tenant, String label, Long model, String content, String agentCode, int calls) {
        return textPlan(tenant, label, model, content, agentCode, calls, null);
    }

    private AiPointEstimatePlan textPlan(Long tenant, String label, Long model, String content, String agentCode, int calls,
                                         AiPointReservationEntity reservation) {
        return textPlan(tenant, label, model, content, agentCode, calls, reservation, 8192);
    }

    private AiPointEstimatePlan textPlan(Long tenant, String label, Long model, String content, String agentCode, int calls,
                                         AiPointReservationEntity reservation, int outputLimit) {
        routes.computeIfAbsent(model+":TEXT", ignored -> router.route(model, "TEXT"));
        int output = outputLimit;
        String context = content;
        if (agentCode != null) {
            var plan = agentPlans.computeIfAbsent(tenant+":"+agentCode, ignored -> agents.freezeFormal(agentCode, tenant));
            output = plan.agent().maxTokens();
            context += plan.agent().systemPrompt();
            for (var skill : plan.skillSnapshots()) context += skill.content();
        }
        var result = tokenPlan(label, model, context.getBytes(StandardCharsets.UTF_8).length + 1024L, output, calls,
            reservation == null ? null : reservation.pointPriceVersionId);
        if (WorkbenchPointPolicy.includesAgent(agentCode)) result = result.workbench(null);
        return reservation == null ? result : result.frozen(reservation);
    }

    private AiPointEstimatePlan tokenPlan(String label, Long model, long input, long output, int calls) {
        return tokenPlan(label, model, input, output, calls, null);
    }

    private AiPointEstimatePlan tokenPlan(String label, Long model, long input, long output, int calls, Long frozenVersion) {
        Long version = frozenVersion;
        if (version == null) {
            version = effectivePointVersions.computeIfAbsent(model, ignored -> {
                var current = pointVersions.selectEffective(model, at);
                if (current == null) throw invalid("所选模型尚未配置有效积分价格。");
                return current.id;
            });
        }
        Set<String> metrics = new HashSet<>();
        AiUsageMetric inputMetric = AiUsageMetric.INPUT_TOKEN;
        BigDecimal highestInputRate = BigDecimal.valueOf(-1);
        for (var component : priceComponents.computeIfAbsent(version, components::selectByVersion)) {
            if (!AiAccountingJson.read(component.dimensionsJson).isEmpty()) continue;
            if (component.pointRate.signum() > 0) metrics.add(component.metric);
            if (Set.of("INPUT_TOKEN","CACHED_INPUT_TOKEN","CACHE_WRITE_TOKEN").contains(component.metric)) {
                BigDecimal rate = component.pointRate.divide(component.unitSize, 12, java.math.RoundingMode.HALF_UP);
                if (rate.compareTo(highestInputRate) > 0) {
                    highestInputRate = rate;
                    inputMetric = AiUsageMetric.valueOf(component.metric);
                }
            }
        }
        Map<AiUsageMetric,BigDecimal> usage = new EnumMap<>(AiUsageMetric.class);
        usage.put(AiUsageMetric.CALL, BigDecimal.valueOf(calls));
        if (metrics.stream().anyMatch(metric -> Set.of("INPUT_TOKEN","OUTPUT_TOKEN","CACHED_INPUT_TOKEN","CACHE_WRITE_TOKEN").contains(metric))) {
            // Match per-call authorization: a preview cannot promise a particular cache category.
            usage.put(inputMetric, BigDecimal.valueOf(input).multiply(BigDecimal.valueOf(calls)));
            usage.put(AiUsageMetric.OUTPUT_TOKEN, BigDecimal.valueOf(output).multiply(BigDecimal.valueOf(calls)));
        }
        return new AiPointEstimatePlan(label, model, usage, Map.of(), true);
    }

    private List<AiPointEstimatePlan> review(Long tenant, String content, JsonNode data, Long model,
                                           AiPointReservationEntity reservation) {
        List<String> dimensions = new ArrayList<>();
        data.path("selectedDimensions").forEach(value -> dimensions.add(value.asText()));
        String scopeType = data.path("reviewScopeType").asText("ALL");
        Map<String,Object> scope = data.path("reviewScope").isObject()
            ? json.convertValue(data.path("reviewScope"), new com.fasterxml.jackson.core.type.TypeReference<>() {}) : Map.of();
        var frozen = new ReviewContentService().freeze(content, scopeType, scope, dimensions);
        if (!"DEEP".equals(data.path("reviewMode").asText()))
            return List.of(textPlan(tenant, "快速审核", model, frozen.content(), "script-review", 1, reservation));
        var units = new ReviewUnitPlanner().plan(content, scopeType, scope, frozen, reviewUnitCharacters, reviewUnitOverlap);
        List<AiPointEstimatePlan> result = new ArrayList<>();
        for (var unit : units) for (var dimension : dimensions)
            result.add(textPlan(tenant, "深度审核：" + dimension, model, frozen.content() + unit.content(), "script-review", 1, reservation));
        result.add(textPlan(tenant, "深度审核报告汇总", model, frozen.content(), "script-review", 1, reservation));
        return result;
    }

    private List<AiPointEstimatePlan> retryReview(Long tenant, Long task) {
        reviews.taskDetail(tenant, task);
        var source = row("select t.*, v.content from review_task t join review_script_version v on v.id=t.script_version_id and v.tenant_id=t.tenant_id where t.tenant_id=? and t.id=?", tenant, task);
        com.fasterxml.jackson.databind.node.ObjectNode config=json.createObjectNode();
        try {
            config.set("selectedDimensions", json.readTree(text(source, "selected_dimensions_json")));
            config.set("reviewScope", json.readTree(text(source, "review_scope_json")));
        }
        catch (Exception exception) { throw invalid("审核维度配置不可用。"); }
        config.put("reviewMode",text(source,"review_mode"));
        config.put("reviewScopeType",text(source,"review_scope_type"));
        Long executionId=number(source,"execution_id");
        var reservation=executionId==null?null:reservations.selectByExecutionId(executionId);
        Long model=executionId==null?reviews.pointEstimateModelId(tenant):executions.requireTask(executionId).requestedModelId;
        return review(tenant, text(source, "content"), config, model, reservation);
    }

    private List<AiPointEstimatePlan> decomposition(Long tenant, Long project, Long model) {
        if (model == null) throw invalid("请先选择视频理解模型。");
        routes.computeIfAbsent(model+":VIDEO_UNDERSTANDING", ignored -> router.route(model, "VIDEO_UNDERSTANDING"));
        return List.of(tokenPlan("视频理解", model, 1_000_000, 8192, 1),
            textPlan(tenant, "视频剧本草稿生成", resolveModel(tenant, project, "TEXT"), "", null, 1));
    }

    private List<AiPointEstimatePlan> retryDecomposition(Long tenant, Long episode) {
        decomposition.episodeDetail(tenant, episode);
        var source = row("select e.project_id, b.model_id from video_decomposition_episode e join video_decomposition_batch b on b.id=e.batch_id and b.tenant_id=e.tenant_id where e.tenant_id=? and e.id=?", tenant, episode);
        return decomposition(tenant, number(source, "project_id"), number(source, "model_id"));
    }

    private List<AiPointEstimatePlan> execution(Long tenant, Long executionId, boolean frozen) {
        executionAccess.requireControl(tenant, executionId);
        var task = executions.requireTask(executionId);
        if("FREE_CREATION".equals(task.businessType)) return List.of(freeCreations.pointEstimateControl(tenant,task.businessId,frozen?"RETRY":"REGENERATE"));
        var reservation = frozen ? reservations.selectByExecutionId(executionId) : null;
        List<AiPointEstimatePlan> plans;
        if ("AI_IMAGE_TASK".equals(task.businessType)) plans = List.of(images.pointEstimateExisting(tenant, task.projectId, task.businessId));
        else if ("AI_VIDEO_TASK".equals(task.businessType)) plans = List.of(videos.pointEstimateExisting(tenant, task.projectId, task.businessId));
        else if ("REVIEW_TASK".equals(task.businessType)) plans = retryReview(tenant, task.businessId);
        else if ("SCRIPT_ANALYSIS_TASK".equals(task.businessType)) plans = analysis(tenant, task.projectId, scriptContent(tenant, task.projectId), task.requestedModelId, reservation);
        else if ("SCRIPT_AI_OPERATION".equals(task.businessType)) {
            var op = row("select operation_type,redacted_input_json from script_ai_operation where tenant_id=? and id=?", tenant, task.businessId);
            JsonNode input;
            try { input = json.readTree(text(op, "redacted_input_json")); }
            catch (Exception exception) { throw invalid("原任务参数不可用。"); }
            if ("CHARACTER_PROMPT_SIMILARITY".equals(text(op, "operation_type"))) {
                var snapshot = row("select input_json from character_prompt_similarity_check where tenant_id=? and project_id=? and execution_id=?",
                    tenant, task.projectId, task.id);
                String corpus = com.antshorttv.script.CharacterPromptSimilarityAnalysis.INSTRUCTIONS
                    + text(snapshot, "input_json") + com.antshorttv.script.CharacterPromptSimilarityAnalysis.schema(json);
                return List.of(textPlan(tenant, "角色形态相似度检查", task.requestedModelId, corpus, null, 1, reservation,
                    com.antshorttv.script.CharacterPromptSimilarityAnalysis.OUTPUT_TOKENS).inProject(task.projectId));
            }
            String path = switch (text(op,"operation_type")) {
                case "STORYBOARD_BREAKDOWN" -> "storyboards/ai-breakdown";
                case "SCRIPT_GENERATE" -> "scripts/ai-generate";
                case "SCRIPT_REWRITE" -> "scripts/ai-rewrite";
                case "SCOPED_ASSET_REEXTRACTION" -> "asset-reextraction";
                case "ASSET_NORMALIZATION" -> "asset-normalization";
                case "PROP_SIMPLIFICATION" -> "prop-simplification";
                case "PROMPT_GENERATE" -> "prompts/ai-generate";
                case "SCRIPT_EPISODE_SPLIT" -> "script-analysis/current/regenerate-episodes";
                case "SCRIPT_EPISODE_SUMMARY" -> "episodes/" + requiredLong(input,"episodeId") + "/summary/regenerate";
                case "SCRIPT_EPISODE_ASSETS" -> "episodes/" + requiredLong(input,"episodeId") + "/assets/regenerate";
                default -> throw invalid("任务类型不支持积分预估。");
            };
            plans = projectPlan(tenant, task.projectId, path, input, task.requestedModelId, reservation);
        } else throw invalid("任务类型不支持积分预估。");
        return frozen && reservation != null && Set.of("AI_IMAGE_TASK","AI_VIDEO_TASK").contains(task.businessType)
            ? plans.stream().map(p -> p.frozen(reservation)).toList() : plans;
    }

    private String scriptContent(Long tenant, Long project) {
        return projectScripts.computeIfAbsent(tenant+":"+project, ignored -> {
            var rows = jdbc.queryForList("select content from script where tenant_id=? and project_id=? and deleted_at is null order by id desc limit 1", tenant, project);
            return rows.isEmpty() ? "" : text(rows.get(0), "content");
        });
    }
    private String episodeContent(Long tenant, Long project, Long episode) {
        return text(row("select content from script_episode where tenant_id=? and project_id=? and id=? and status='ACTIVE' and retired_at is null", tenant, project, episode), "content");
    }
    private boolean hasStoryboard(Long tenant, Long project, Long episode) {
        return storyboardEpisodes.computeIfAbsent(tenant+":"+project, ignored -> new HashSet<>(jdbc.queryForList(
            "select distinct episode_id from storyboard where tenant_id=? and project_id=? and deleted_at is null and episode_id is not null",
            Long.class,tenant,project))).contains(episode);
    }
    private Map<String,Object> row(String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "预估对象不存在或无权访问。");
        return rows.get(0);
    }
    }
    private static String text(Map<String,Object> row, String key) { return Objects.toString(row.get(key), ""); }
    private static Long number(Map<String,Object> row, String key) { return row.get(key) instanceof Number n ? n.longValue() : null; }
    private static BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
    private static Long nullableLong(JsonNode data, String field) { return data.hasNonNull(field) ? data.path(field).longValue() : null; }
    private static Long requiredLong(JsonNode data, String field) {
        Long value = nullableLong(data,field);
        if (value == null || value <= 0) throw invalid("缺少有效的" + field);
        return value;
    }
    private static Long pathId(String path, int index) { return Long.valueOf(path.split("/")[index]); }
    private static BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_ERROR, message); }

    public record Operation(@NotBlank String url, JsonNode data) {}
    public record Estimate(BigDecimal estimatedPoints, BigDecimal availablePoints, BigDecimal paidAvailablePoints,
                           boolean sufficient, List<AiPointEstimateCalculator.Line> lines, String notice) {}
}
