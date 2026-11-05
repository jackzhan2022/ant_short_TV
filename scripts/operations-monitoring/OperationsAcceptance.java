package com.antshorttv.operations.telemetry;

import com.antshorttv.monitoring.MonitoringProperties;
import com.antshorttv.operations.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;
import org.springframework.mock.web.*;
import org.springframework.web.servlet.HandlerMapping;

/** Controlled loopback HTTP + in-memory H2 fixture, never connects to business infrastructure. */
public final class OperationsAcceptance {
    public static void main(String[] args) throws Exception {
        int seconds=args.length>0?Integer.parseInt(args[0]):1800;
        Path output=Path.of(args.length>1?args[1]:"operations-acceptance.json");
        var json=new ObjectMapper();
        var jdbc=OperationsTestDatabase.create();var database=new OperationsDatabase(jdbc);
        var settings=new OperationsSettingsService(database,json,new MonitoringProperties(false,"","","fixture","soak",10,true));settings.current();
        var telemetry=new OperationsTelemetry(database,settings,json);
        var filter=new OperationsHttpFilter(telemetry);
        jdbc.execute("create table fixture_business(id bigint primary key,value_count bigint)");
        for(int i=0;i<100;i++)jdbc.update("insert into fixture_business values(?,?)",i,i*17);
        var executor=Executors.newFixedThreadPool(8);var persistence=Executors.newSingleThreadScheduledExecutor();
        var server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),64);server.setExecutor(executor);
        server.createContext("/api/fixture/projects/",exchange->{
            try{
                var request=new MockHttpServletRequest("GET",exchange.getRequestURI().getPath());
                request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/fixture/projects/{id}");
                var response=new MockHttpServletResponse();
                filter.doFilter(request,response,(req,res)->{
                    // A deterministic indexed SQL workload shared by both comparison phases.
                    long total=jdbc.queryForObject("select sum(value_count) from fixture_business where id>=? and id<?",Long.class,10,90);
                    res.setContentType("application/json");res.getWriter().write("{\"total\":"+total+"}");
                });
                byte[] body=response.getContentAsByteArray();exchange.getResponseHeaders().set("Content-Type","application/json");
                if(response.getHeader("X-Request-Id")!=null)exchange.getResponseHeaders().set("X-Request-Id",response.getHeader("X-Request-Id"));
                exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
            }catch(Exception failed){exchange.sendResponseHeaders(500,-1);}finally{exchange.close();}
        });
        server.start();persistence.scheduleWithFixedDelay(telemetry::flush,1,1,TimeUnit.SECONDS);
        var client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build();
        URI url=URI.create("http://localhost:"+server.getAddress().getPort()+"/api/fixture/projects/1");
        var off=Collections.synchronizedList(new ArrayList<Double>());var on=Collections.synchronizedList(new ArrayList<Double>());
        var errors=new AtomicLong();var inflight=new AtomicInteger();var maximumInFlight=new AtomicInteger();
        // Warm the same code paths/JIT before collecting either comparison arm.
        for(int i=0;i<300;i++)client.send(HttpRequest.newBuilder(url).GET().build(),HttpResponse.BodyHandlers.discarding());
        telemetry.flush();System.gc();Thread.sleep(100);long heapStart=heap();
        long started=System.nanoTime();int expectedRequests=(seconds+60)*100,issued=0;long heapMax=heapStart;int dimensionMax=0,queueMax=0;
        while(issued<expectedRequests){
            long elapsed=(System.nanoTime()-started)/1_000_000_000L;
            telemetry.enabled=issued>=6000;
            boolean enabled=telemetry.enabled;
            long begin=System.nanoTime();
            int active=inflight.incrementAndGet();maximumInFlight.accumulateAndGet(active,Math::max);
            client.sendAsync(HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.discarding())
                .whenComplete((reply,failure)->{if(failure!=null||reply.statusCode()!=200)errors.incrementAndGet();else(enabled?on:off).add((System.nanoTime()-begin)/1_000_000d);inflight.decrementAndGet();});
            issued++;
            if(issued%100==0){heapMax=Math.max(heapMax,heap());queueMax=Math.max(queueMax,telemetry.queue.size());telemetry.metricsLock.lock();try{dimensionMax=Math.max(dimensionMax,telemetry.metrics.size());}finally{telemetry.metricsLock.unlock();}}
            if(issued%6000==0)System.out.println("elapsed="+elapsed+" requests="+issued+" inflight="+inflight+" queue="+telemetry.queue.size()+" dimensions="+dimensionMax);
            long target=started+issued*10_000_000L;long wait=target-System.nanoTime();if(wait>0)LockSupport.parkNanos(wait);
        }
        double actualDuration=(System.nanoTime()-started)/1_000_000_000d;
        long drainUntil=System.nanoTime()+10_000_000_000L;while(inflight.get()>0&&System.nanoTime()<drainUntil)Thread.sleep(10);
        telemetry.enabled=true;telemetry.flush();server.stop(0);persistence.shutdownNow();executor.shutdownNow();
        var queryTimes=queryBench(database,settings,json);
        System.gc();Thread.sleep(100);long heapEnd=heap();
        double off95=percentile(off,.95),on95=percentile(on,.95),increase=on95-off95;
        var result=new LinkedHashMap<String,Object>();
        result.put("fixture","loopback JDK HTTP server, actual OperationsHttpFilter/Telemetry, isolated H2; not production/MySQL validation");
        result.put("durationSeconds",seconds);result.put("actualDurationSeconds",actualDuration);result.put("baselineSeconds",60);result.put("qps",100);result.put("requests",issued);result.put("offSamples",off.size());result.put("onSamples",on.size());result.put("errors",errors.get());result.put("maxInFlight",maximumInFlight.get());
        result.put("httpOffP95Ms",off95);result.put("httpOnP95Ms",on95);result.put("httpP95IncreaseMs",increase);result.put("httpP95IncreasePercent",100*increase/off95);
        result.put("httpBudgetPassed",off.size()>=1000&&on.size()>=1000&&increase<=20&&increase<=off95*.05);
        result.put("heapStartBytes",heapStart);result.put("heapEndBytes",heapEnd);result.put("heapPeakBytes",heapMax);result.put("queueMaximum",queueMax);result.put("metricMapMaximum",dimensionMax);
        result.put("boundedMemoryPassed",queueMax<=5000&&dimensionMax<=6006&&heapEnd-heapStart<64L*1024*1024);
        result.put("query",queryTimes);result.put("fullDurationPassed",seconds>=1800&&actualDuration>=seconds+60&&on.size()>=180000&&errors.get()==0);
        Files.createDirectories(output.toAbsolutePath().getParent());Files.writeString(output,json.writerWithDefaultPrettyPrinter().writeValueAsString(result));System.out.println(json.writeValueAsString(result));
    }
    private static Map<String,Object> queryBench(OperationsDatabase database,OperationsSettingsService settings,ObjectMapper json)throws Exception{
        var jdbc=database.jdbc();Instant end=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        String dimensions="{\"scope\":\"ALL\"}";String key=OperationsTelemetry.hash(dimensions);
        for(int i=1;i<=1440;i++)seed(jdbc,end.minusSeconds(i*60L),"MINUTE",key,dimensions);
        for(int i=1;i<=168;i++)seed(jdbc,end.minusSeconds(i*3600L),"HOUR",key,dimensions);
        var queries=new OperationsQueryService(database,settings,json);var values=new LinkedHashMap<String,Object>();
        for(int hours:List.of(24,168)){
            var query=new OperationsQuery(end.minusSeconds(hours*3600L),end,20,null,Map.of());var times=new ArrayList<Double>();
            for(int i=0;i<35;i++){long started=System.nanoTime();queries.snapshot(query);if(i>=5)times.add((System.nanoTime()-started)/1_000_000d);}
            double p95=percentile(times,.95);values.put(hours+"hP95Ms",p95);values.put(hours+"hBudgetPassed",p95<=1000);
            var plan=jdbc.queryForList("explain select count_value from ops_metric_bucket where family='HTTP' and granularity=? and dimensions_key=? and bucket_at>=? and bucket_at<?",hours==24?"MINUTE":"HOUR",key,Timestamp.from(query.from()),Timestamp.from(query.to()));values.put(hours+"hExplain",plan.toString());
        }return values;
    }
    private static void seed(org.springframework.jdbc.core.JdbcTemplate jdbc,Instant at,String granularity,String key,String dimensions){
        jdbc.update("insert into ops_metric_bucket(bucket_at,granularity,instance_id,family,dimensions_key,dimensions_json,count_value,histogram_json,values_json,updated_at) values(?,?,'fixture','HTTP',?,?,100,'[0,0,100,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]','{}',?)",Timestamp.from(at),granularity,key,dimensions,Timestamp.from(at));
    }
    private static long heap(){Runtime runtime=Runtime.getRuntime();return runtime.totalMemory()-runtime.freeMemory();}
    private static double percentile(List<Double> samples,double q){var copy=new ArrayList<>(samples);copy.sort(Double::compare);return copy.isEmpty()?0:copy.get(Math.max(0,(int)Math.ceil(copy.size()*q)-1));}
}
