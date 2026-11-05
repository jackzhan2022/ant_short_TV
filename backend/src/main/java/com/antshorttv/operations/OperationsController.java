package com.antshorttv.operations;

import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.monitoring.MonitoringEvent;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.operations.alerts.OperationsAlertService;
import com.antshorttv.platform.PlatformPermissionGuard;
import com.antshorttv.security.CurrentPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/platform/operations")
public class OperationsController {
    private final CurrentPrincipal principal;
    private final PlatformPermissionGuard permissions;
    private final OperationsQueryService queries;
    private final OperationsSettingsService settings;
    private final OperationsAlertService alerts;
    private final OperationLogService audit;
    public OperationsController(CurrentPrincipal principal,PlatformPermissionGuard permissions,OperationsQueryService queries,
        OperationsSettingsService settings,OperationsAlertService alerts,OperationLogService audit) {
        this.principal=principal;this.permissions=permissions;this.queries=queries;this.settings=settings;this.alerts=alerts;this.audit=audit;
    }
    private AuthenticatedUser require(String extra) {
        var user=principal.require();
        if(user.impersonation()!=null)throw new BusinessException(ErrorCode.FORBIDDEN,"代登录会话不能访问平台运维。");
        permissions.require("PLATFORM_OPERATIONS_VIEW");if(extra!=null)permissions.require("PLATFORM_OPERATIONS_"+extra);return user;
    }
    @GetMapping("/snapshot") public ApiResponse<?> snapshot(@RequestParam Map<String,String> parameters){require(null);return ApiResponse.success(queries.snapshot(query(parameters)));}
    @GetMapping("/requests") public ApiResponse<?> requests(@RequestParam Map<String,String> parameters){require(null);return ApiResponse.success(queries.requests(query(parameters)));}
    @GetMapping("/{source:ai|executions|jobs|events|alerts|notifications}")
    public ApiResponse<?> list(@PathVariable String source,@RequestParam Map<String,String> parameters){
        require(java.util.Set.of("ai","executions","events").contains(source)?"LOG_VIEW":null);
        var page=queries.list(source,query(parameters),null);
        if(source.equals("jobs")) {
            // Job timings are metrics. Error evidence is returned only by the separate log permission.
            boolean canLog=true;try{permissions.require("PLATFORM_OPERATIONS_LOG_VIEW");}catch(BusinessException denied){canLog=false;}
            if(!canLog){@SuppressWarnings("unchecked") var rows=(java.util.List<Map<String,Object>>)page.get("items");rows.forEach(row->row.remove("errorMessage"));}
        }
        return ApiResponse.success(page);
    }
    @GetMapping("/events/{id}") public ApiResponse<?> event(@PathVariable long id){require("LOG_VIEW");return ApiResponse.success(queries.event(id));}
    @GetMapping("/executions/{executionId}") public ApiResponse<?> execution(@PathVariable long executionId,@RequestParam Map<String,String> parameters){require("LOG_VIEW");return ApiResponse.success(queries.execution(executionId,query(parameters)));}
    @GetMapping("/alerts/{id}/actions") public ApiResponse<?> actions(@PathVariable long id,@RequestParam Map<String,String> parameters){require(null);return ApiResponse.success(queries.list("actions",query(parameters),id));}
    @GetMapping("/rules") public ApiResponse<?> rules(){require(null);var rules=alerts.rules();return ApiResponse.success(OperationsQueryService.map("items",rules,"total",rules.size(),"nextCursor",null));}
    @PostMapping("/rules") public ApiResponse<?> createRule(@RequestBody Map<String,Object> body,HttpServletRequest request){
        var user=require("RULE_EDIT");var after=alerts.saveRule(null,body);log(user,"RULE_EDIT",((Number)after.get("id")).longValue(),request,Map.of("after",after));return ApiResponse.success(after);
    }
    @PutMapping("/rules/{id}") public ApiResponse<?> updateRule(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest request){
        var user=require("RULE_EDIT");var before=alerts.rules().stream().filter(rule->((Number)rule.get("id")).longValue()==id).findFirst().orElse(Map.of());
        var after=alerts.saveRule(id,body);log(user,"RULE_EDIT",id,request,Map.of("before",before,"after",after));return ApiResponse.success(after);
    }
    public record Action(@NotBlank @Size(max=500) String reason,String until) {}
    @PostMapping("/alerts/{id}/acknowledge") public ApiResponse<?> acknowledge(@PathVariable long id,@Valid @RequestBody Action action,HttpServletRequest request){
        var user=require("ALERT_MANAGE");var result=alerts.acknowledge(id,user.userId(),action.reason());log(user,"ALERT_ACKNOWLEDGE",id,request,Map.of("reason",clean(action.reason())));return ApiResponse.success(result);
    }
    @PostMapping("/alerts/{id}/silence") public ApiResponse<?> silence(@PathVariable long id,@Valid @RequestBody Action action,HttpServletRequest request){
        var user=require("ALERT_MANAGE");Instant until;try{until=Instant.parse(action.until());}catch(RuntimeException e){throw new BusinessException(ErrorCode.VALIDATION_ERROR,"静默到期时间格式不正确");}
        var result=alerts.silence(id,user.userId(),until,action.reason());log(user,"ALERT_SILENCE",id,request,Map.of("reason",clean(action.reason()),"until",until.toString()));return ApiResponse.success(result);
    }
    @GetMapping("/settings") public ApiResponse<?> settings(){require(null);return ApiResponse.success(settings.current());}
    @PutMapping("/settings") public ApiResponse<?> settings(@RequestBody Map<String,Object> body,HttpServletRequest request){
        var user=require("SETTINGS_EDIT");var before=settings.current();var after=settings.update(body);log(user,"SETTINGS_EDIT",null,request,Map.of("before",before,"after",after));return ApiResponse.success(after);
    }
    private void log(AuthenticatedUser user,String operation,Long target,HttpServletRequest request,Map<String,?> detail){audit.record(user.userId(),null,"PLATFORM_OPERATIONS_"+operation,target,OperationResult.SUCCESS,request,detail);}
    private static String clean(String text){return new MonitoringEvent(Instant.EPOCH,"","","",text,"",null,null,null,null).sanitized().message();}
    private OperationsQuery query(Map<String,String> parameters){return OperationsQuery.parse(parameters,Instant.now());}
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<ApiResponse<Void>> conflict(ResponseStatusException exception){return ResponseEntity.status(exception.getStatusCode()).body(ApiResponse.fail(exception.getStatusCode().value()==409?"OPERATIONS_VERSION_CONFLICT":"OPERATIONS_ERROR",exception.getReason()));}
}
