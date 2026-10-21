package com.antshorttv.messagecenter;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.platform.PlatformAuthorizationService;
import com.antshorttv.security.CurrentPrincipal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageCenterService {
    public static final List<String> CATEGORIES = List.of("SYSTEM", "SECURITY", "PRODUCT", "FINANCE", "OPERATION", "ACTIVITY");
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate sql;
    private final CurrentPrincipal principal;
    private final PlatformAuthorizationService authorization;

    public MessageCenterService(JdbcTemplate jdbc, CurrentPrincipal principal, PlatformAuthorizationService authorization) {
        this.jdbc = jdbc;
        this.sql = new NamedParameterJdbcTemplate(jdbc);
        this.principal = principal;
        this.authorization = authorization;
    }

    public record Message(String id, String category, String title, String content, String source,
                          String senderName, Instant createdAt, boolean read) {}
    public record Page(List<Message> items, long total, int page, int pageSize) {}
    public record Summary(long unread, Map<String, Long> categories, boolean canSend) {}
    public record Recipient(long id, String nickname) {}
    public record SendRequest(String category, String title, String content, boolean allUsers, List<Long> recipientIds) {}
    public record Sent(String id, long recipientCount) {}
    public record SentMessage(String id, String category, String title, String content, String source,
        String senderName, String audience, Instant createdAt, long recipientCount, long readCount, long unreadCount) {}
    public record SentPage(List<SentMessage> items, long total, int page, int pageSize) {}
    public record Delivery(long userId, String nickname, Instant readAt, boolean deleted) {}
    public record DeliveryPage(List<Delivery> items, long total, int page, int pageSize) {}
    public record ActionRequest(List<String> ids, boolean all, String category, String status, String keyword) {}
    private record Filter(String where, Map<String, Object> params) {}

    private boolean canSend(long user) { return authorization.roleCodes(user).contains("PLATFORM_ADMIN"); }

    private long requireAdmin() {
        long user = principal.require().userId();
        if (!canSend(user)) throw new BusinessException(ErrorCode.FORBIDDEN, "仅平台管理员可发送消息。");
        return user;
    }

    private Filter filter(String category, String status, String keyword) {
        Map<String, Object> p = new HashMap<>();
        p.put("user", principal.require().userId());
        String where = " user_id=:user and deleted_at is null";
        String messageWhere = "1=1";
        if (category != null && !category.isBlank()) {
            validateCategory(category);
            messageWhere += " and category=:category";
            p.put("category", category);
        }
        if (status != null && !status.isBlank()) {
            if (!List.of("READ", "UNREAD").contains(status)) throw invalid("消息状态不正确。");
            where += status.equals("READ") ? " and read_at is not null" : " and read_at is null";
        }
        if (keyword != null && !keyword.isBlank()) {
            if (keyword.length() > 200) throw invalid("搜索标题不能超过200字。");
            messageWhere += " and title like :keyword escape '!'";
            p.put("keyword", "%" + keyword.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        return new Filter(where + " and message_id in (select id from inbox_message where " + messageWhere + ")", p);
    }

    public Page list(String category, String status, String keyword, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw invalid("分页参数不正确。");
        Filter f = filter(category, status, keyword);
        String from = " from message_recipient r join inbox_message m on m.id=r.message_id where " + f.where;
        long total = sql.queryForObject("select count(*)" + from, f.params, Long.class);
        f.params.put("limit", pageSize);
        f.params.put("offset", ((long) page - 1) * pageSize);
        List<Message> items = sql.query("select m.id,m.category,m.title,null as content,m.source,m.sender_name,m.created_at,r.read_at" + from
            + " order by m.created_at desc,m.id desc limit :limit offset :offset", f.params, (rs, row) -> message(rs));
        return new Page(items, total, page, pageSize);
    }

    public Summary summary() {
        long user = principal.require().userId();
        Map<String, Long> counts = new LinkedHashMap<>();
        CATEGORIES.forEach(c -> counts.put(c, 0L));
        jdbc.query("""
            select m.category,count(*) as amount from message_recipient r
            join inbox_message m on m.id=r.message_id
            where r.user_id=? and r.deleted_at is null and r.read_at is null group by m.category
            """, rs -> { counts.put(rs.getString("category"), rs.getLong("amount")); }, user);
        return new Summary(counts.values().stream().mapToLong(Long::longValue).sum(), counts, canSend(user));
    }

    public Message detail(String id) {
        var rows = jdbc.query("""
            select m.*,r.read_at from message_recipient r join inbox_message m on m.id=r.message_id
            where r.user_id=? and r.message_id=? and r.deleted_at is null
            """, (rs, row) -> message(rs), principal.require().userId(), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在或已删除。");
        return rows.get(0);
    }

    private static final String SENT_COLUMNS = """
        m.*, (select count(*) from message_recipient r where r.message_id=m.id) as recipient_count,
        (select count(*) from message_recipient r where r.message_id=m.id and r.read_at is not null) as read_count
        """;

    public SentPage sentMessages(String category, String source, String keyword, int page, int pageSize) {
        requireAdmin();
        if (page < 1 || pageSize < 1 || pageSize > 100) throw invalid("分页参数不正确。");
        Map<String, Object> params = new HashMap<>();
        String where = " where 1=1";
        if (category != null && !category.isBlank()) {
            validateCategory(category); where += " and m.category=:category"; params.put("category", category);
        }
        if (source != null && !source.isBlank()) {
            if (!List.of("ADMIN", "SYSTEM").contains(source)) throw invalid("消息来源不正确。");
            where += " and m.source=:source"; params.put("source", source);
        }
        if (keyword != null && !keyword.isBlank()) {
            if (keyword.length() > 200) throw invalid("搜索标题不能超过200字。");
            where += " and m.title like :keyword escape '!'";
            params.put("keyword", "%" + keyword.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        long total = sql.queryForObject("select count(*) from inbox_message m" + where, params, Long.class);
        params.put("limit", pageSize); params.put("offset", ((long) page - 1) * pageSize);
        var items = sql.query("select " + SENT_COLUMNS + " from inbox_message m" + where
            + " order by m.created_at desc,m.id desc limit :limit offset :offset", params, (rs, row) -> sentMessage(rs, false));
        return new SentPage(items, total, page, pageSize);
    }

    public SentMessage sentDetail(String id) {
        requireAdmin();
        var rows = jdbc.query("select " + SENT_COLUMNS + " from inbox_message m where m.id=?",
            (rs, row) -> sentMessage(rs, true), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "发送记录不存在。");
        return rows.get(0);
    }

    public DeliveryPage deliveries(String id, int page, int pageSize) {
        SentMessage sent = sentDetail(id);
        if (page < 1 || pageSize < 1 || pageSize > 100) throw invalid("分页参数不正确。");
        var items = jdbc.query("""
            select r.user_id,u.nickname,r.read_at,r.deleted_at from message_recipient r
            join app_user u on u.id=r.user_id where r.message_id=? order by r.user_id limit ? offset ?
            """, (rs, row) -> new Delivery(rs.getLong("user_id"), rs.getString("nickname"),
                rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant(),
                rs.getTimestamp("deleted_at") != null), id, pageSize, ((long) page - 1) * pageSize);
        return new DeliveryPage(items, sent.recipientCount, page, pageSize);
    }

    private SentMessage sentMessage(ResultSet rs, boolean includeContent) throws SQLException {
        long total = rs.getLong("recipient_count"), read = rs.getLong("read_count");
        return new SentMessage(rs.getString("id"), rs.getString("category"), rs.getString("title"),
            includeContent ? rs.getString("content") : null, rs.getString("source"), rs.getString("sender_name"),
            rs.getString("audience"), rs.getTimestamp("created_at").toInstant(), total, read, total - read);
    }

    @Transactional
    public void update(boolean delete, ActionRequest request) {
        Filter f = filter(request.category, request.status, request.keyword);
        if (request.all) {
            if (request.ids != null && !request.ids.isEmpty()) throw invalid("不能同时选择全部和指定消息。");
        } else {
            if (request.ids == null || request.ids.isEmpty() || request.ids.size() > 100
                || request.ids.stream().anyMatch(id -> id == null || id.length() > 36)) throw invalid("请选择消息。");
            f.params.put("ids", request.ids);
        }
        f.params.put("now", Timestamp.from(Instant.now()));
        sql.update("update message_recipient set " + (delete ? "deleted_at=:now" : "read_at=coalesce(read_at,:now)")
            + " where " + f.where + (request.all ? "" : " and message_id in (:ids)"), f.params);
    }

    public List<Recipient> recipients(String keyword) {
        requireAdmin();
        String term = keyword == null ? "" : keyword.trim();
        if (term.length() > 100) throw invalid("搜索内容过长。");
        return jdbc.query("""
            select id,nickname from app_user where status='ACTIVE' and deleted_at is null
            and (nickname like ? escape '!' or cast(id as char)=?) order by id limit 50
            """, (rs, row) -> new Recipient(rs.getLong("id"), rs.getString("nickname")),
            "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%", term);
    }

    @Transactional
    public Sent send(SendRequest request) {
        long sender = requireAdmin();
        validateMessage(request.category, request.title, request.content);
        if (request.allUsers && request.recipientIds != null && !request.recipientIds.isEmpty()) throw invalid("发送范围不正确。");
        List<Long> recipients = request.allUsers ? null : validateRecipients(request.recipientIds);
        String name = jdbc.queryForObject("select nickname from app_user where id=?", String.class, sender);
        String id = create(request.category, request.title, request.content, "ADMIN", sender, name, request.allUsers ? "ALL" : "SELECTED");
        if (request.allUsers) {
            int count = jdbc.update("""
                insert into message_recipient(message_id,user_id)
                select ?,id from app_user where status='ACTIVE' and deleted_at is null
                """, id);
            return new Sent(id, count);
        }
        deliver(id, recipients);
        return new Sent(id, recipients.size());
    }

    /** Internal business-flow entry point. Deliberately not exposed by any HTTP controller. */
    @Transactional
    public Sent sendSystem(List<Long> recipientIds, String category, String title, String content) {
        validateMessage(category, title, content);
        List<Long> recipients = validateRecipients(recipientIds);
        String id = create(category, title, content, "SYSTEM", null, "系统通知", "SELECTED");
        deliver(id, recipients);
        return new Sent(id, recipients.size());
    }

    private String create(String category, String title, String content, String source, Long sender, String name, String audience) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
            insert into inbox_message(id,category,title,content,source,sender_id,sender_name,created_at,audience)
            values (?,?,?,?,?,?,?,?,?)
            """, id, category, title.trim(), content.trim(), source, sender, name == null ? "平台管理员" : name, Timestamp.from(Instant.now()), audience);
        return id;
    }

    private void deliver(String id, List<Long> recipients) {
        jdbc.batchUpdate("insert into message_recipient(message_id,user_id) values (?,?)",
            recipients, 100, (ps, user) -> { ps.setString(1, id); ps.setLong(2, user); });
    }

    private List<Long> validateRecipients(List<Long> recipients) {
        if (recipients == null || recipients.isEmpty() || recipients.size() > 500
            || recipients.stream().anyMatch(id -> id == null || id < 1)) throw invalid("请选择1至500名有效收件人。");
        List<Long> unique = recipients.stream().distinct().toList();
        long found = sql.queryForObject("select count(*) from app_user where id in (:ids) and status='ACTIVE' and deleted_at is null",
            Map.of("ids", unique), Long.class);
        if (found != unique.size()) throw invalid("部分收件人不存在或已停用。");
        return unique;
    }

    private void validateMessage(String category, String title, String content) {
        validateCategory(category);
        if (title == null || title.isBlank() || title.length() > 200) throw invalid("消息标题必填，最多200字。");
        if (content == null || content.isBlank() || content.length() > 10000) throw invalid("消息正文必填，最多10000字。");
    }

    private void validateCategory(String category) {
        if (category == null || !CATEGORIES.contains(category)) throw invalid("消息分类不正确。");
    }

    private Message message(ResultSet rs) throws SQLException {
        return new Message(rs.getString("id"), rs.getString("category"), rs.getString("title"), rs.getString("content"),
            rs.getString("source"), rs.getString("sender_name"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("read_at") != null);
    }

    private static BusinessException invalid(String text) { return new BusinessException(ErrorCode.VALIDATION_ERROR, text); }
}
