package net.atos.entng.rbs.test.units.service.impl;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import net.atos.entng.rbs.service.impl.ResourceTypeServiceSqlImpl;
import org.entcore.common.sql.Sql;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(VertxUnitRunner.class)
public class ResourceTypeServiceSqlImplTest {

    private ResourceTypeServiceSqlImpl resourceTypeServiceSql;
    private Vertx vertx;

    @Before
    public void setUp() {
        this.vertx = Vertx.vertx();
        this.resourceTypeServiceSql = Mockito.spy(new ResourceTypeServiceSqlImpl());
        Sql.getInstance().init(vertx.eventBus(), "fr.openent.rbs");
    }

    @Test
    public void testList(TestContext ctx) {
        Async async = ctx.async();
        List<String> groupsAndUserIds = Arrays.asList("groupsAndUserId1", "groupsAndUserId2");
        UserInfos userInfos = new UserInfos();
        userInfos.setUserId("userId");

        List<String> expectedQuery = Arrays.asList(
                "SELECT t.*, json_agg(row_to_json(row(ts.member_id,ts.action)::rbs.share_tuple)) as shared, array_to_json(array_agg(m.group_id))" +
                        " as groups  FROM rbs.resource_type AS t LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id" +
                        " LEFT JOIN rbs.members AS m ON (ts.member_id = m.id AND m.group_id IS NOT NULL) WHERE ts.member_id" +
                        " IN (?,?) OR t.owner = ? AND t.school_id = ? GROUP BY t.id ORDER BY t.name",
                "SELECT t.*, json_agg(row_to_json(row(ts.member_id,ts.action)::rbs.share_tuple)) as shared, array_to_json(array_agg(m.group_id))" +
                        " as groups  FROM rbs.resource_type AS t LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id" +
                        " LEFT JOIN rbs.members AS m ON (ts.member_id = m.id AND m.group_id IS NOT NULL) WHERE ts.member_id" +
                        " IN (?,?) OR t.owner = ? GROUP BY t.id ORDER BY t.name",
                "SELECT t.*, json_agg(row_to_json(row(ts.member_id,ts.action)::rbs.share_tuple)) as shared, array_to_json(array_agg(m.group_id))" +
                        " as groups  FROM rbs.resource_type AS t LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id" +
                        " LEFT JOIN rbs.members AS m ON (ts.member_id = m.id AND m.group_id IS NOT NULL) WHERE t.school_id = ?" +
                        " GROUP BY t.id ORDER BY t.name",
                "SELECT t.*, json_agg(row_to_json(row(ts.member_id,ts.action)::rbs.share_tuple)) as shared, array_to_json(array_agg(m.group_id))" +
                        " as groups  FROM rbs.resource_type AS t LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id" +
                        " LEFT JOIN rbs.members AS m ON (ts.member_id = m.id AND m.group_id IS NOT NULL) WHERE ts.member_id IN (?,?)" +
                        " OR t.owner = ? OR t.school_id IN (?,?) GROUP BY t.id ORDER BY t.name"
        );
        List<JsonArray> expectedParams = Arrays.asList(
                new JsonArray(Arrays.asList("groupsAndUserId1","groupsAndUserId2","userId","structureId")),
                new JsonArray(Arrays.asList("groupsAndUserId1","groupsAndUserId2","userId")),
                new JsonArray(Arrays.asList("structureId")),
                new JsonArray(Arrays.asList("groupsAndUserId1","groupsAndUserId2","userId","schoolId1","schoolId2"))
        );

        final Integer[] i = {0};
        vertx.eventBus().consumer("fr.openent.rbs", message -> {
            JsonObject body = (JsonObject) message.body();
            ctx.assertEquals("prepared", body.getString("action"));
            ctx.assertEquals(expectedQuery.get(i[0]), body.getString("statement"));
            ctx.assertEquals(expectedParams.get(i[0]).toString(), body.getJsonArray("values").toString());
            i[0]++;
            if (i[0] == expectedQuery.size()) {
                async.complete();
            }
        });

        this.resourceTypeServiceSql.list(groupsAndUserIds, userInfos, "structureId", null);
        this.resourceTypeServiceSql.list(groupsAndUserIds, userInfos, null, null);

        UserInfos.Function function = new UserInfos.Function();
        List<String> list = Arrays.asList("schoolId1", "schoolId2");
        function.setScope(list);
        Map<String, UserInfos.Function> map = new HashMap<>();
        map.put(DefaultFunctions.ADMIN_LOCAL, function);
        userInfos.setFunctions(map);
        this.resourceTypeServiceSql.list(groupsAndUserIds, userInfos, "structureId", null);
        this.resourceTypeServiceSql.list(groupsAndUserIds, userInfos, null, null);

        groupsAndUserIds = Arrays.asList();
        userInfos = new UserInfos();
        Mockito.doNothing().when(this.resourceTypeServiceSql).list(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        this.resourceTypeServiceSql.list(groupsAndUserIds, userInfos, null);
        Mockito.verify(resourceTypeServiceSql, Mockito.times(1)).list(groupsAndUserIds, userInfos, null, null);
    }

    @Test
    public void checkListResourceTypesNullThirdArgument(TestContext ctx) {
        Async async = ctx.async();
        List<String> groupsAndUserIds = Arrays.asList("groupsAndUserId1", "groupsAndUserId2");
        UserInfos userInfos = new UserInfos();
        userInfos.setUserId("userId");

        String expectedQuery = "SELECT t.*, " +
                "json_agg(row_to_json(row(ts.member_id,ts.action)::rbs.share_tuple)) as shared, " +
                "array_to_json(array_agg(m.group_id)) as groups  " +
                "FROM rbs.resource_type AS t " +
                "LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id " +
                "LEFT JOIN rbs.members AS m ON (ts.member_id = m.id AND m.group_id IS NOT NULL) " +
                "WHERE ts.member_id IN (?,?) " +
                "OR t.owner = ? " +
                "GROUP BY t.id " +
                "ORDER BY t.name";

        JsonArray expectedParams = new JsonArray(Arrays.asList("groupsAndUserId1", "groupsAndUserId2", "userId"));

        vertx.eventBus().consumer("fr.openent.rbs", message -> {
            JsonObject body = (JsonObject) message.body();
            ctx.assertEquals("prepared", body.getString("action"));
            ctx.assertEquals(expectedQuery, body.getString("statement"));
            ctx.assertEquals(expectedParams.toString(), body.getJsonArray("values").toString());
            async.complete();
        });

        this.resourceTypeServiceSql.list(groupsAndUserIds, userInfos, null, event -> {});
    }

    @Test
    public void checkListResourceTypesDefinedThirdArgument(TestContext ctx) {
        Async async = ctx.async();
        List<String> groupsAndUserIds = Arrays.asList("groupsAndUserId1", "groupsAndUserId2");
        UserInfos userInfos = new UserInfos();
        userInfos.setUserId("userId");
        String structureId = "structureId";

        String expectedQuery = "SELECT t.*, " +
                "json_agg(row_to_json(row(ts.member_id,ts.action)::rbs.share_tuple)) as shared, " +
                "array_to_json(array_agg(m.group_id)) as groups  " +
                "FROM rbs.resource_type AS t " +
                "LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id " +
                "LEFT JOIN rbs.members AS m ON (ts.member_id = m.id AND m.group_id IS NOT NULL) " +
                "WHERE ts.member_id IN (?,?) " +
                "OR t.owner = ? " +
                "AND t.school_id = ? " +
                "GROUP BY t.id " +
                "ORDER BY t.name";

        JsonArray expectedParams = new JsonArray(Arrays.asList("groupsAndUserId1", "groupsAndUserId2", "userId", structureId));

            vertx.eventBus().consumer("fr.openent.rbs", message -> {
            JsonObject body = (JsonObject) message.body();
            ctx.assertEquals("prepared", body.getString("action"));
            ctx.assertEquals(expectedQuery, body.getString("statement"));
            ctx.assertEquals(expectedParams.toString(), body.getJsonArray("values").toString());
            async.complete();
        });

            this.resourceTypeServiceSql.list(groupsAndUserIds, userInfos, structureId, event -> {});
    }
}
