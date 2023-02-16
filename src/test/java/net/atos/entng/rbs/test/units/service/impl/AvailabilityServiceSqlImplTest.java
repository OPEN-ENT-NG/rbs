package net.atos.entng.rbs.test.units.service.impl;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import net.atos.entng.rbs.Rbs;
import net.atos.entng.rbs.service.impl.AvailabilityServiceSqlImpl;
import org.entcore.common.sql.Sql;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Arrays;

@RunWith(VertxUnitRunner.class)
public class AvailabilityServiceSqlImplTest {
    private Vertx vertx;
    private AvailabilityServiceSqlImpl availabilityServiceSql;


    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        vertx = Vertx.vertx();
        Field DB_SCHEMA = Rbs.class.getDeclaredField("DB_SCHEMA");
        DB_SCHEMA.setAccessible(true);
        DB_SCHEMA.set(null, "rbs");

        Field AVAILABILITY_TABLE = Rbs.class.getDeclaredField("AVAILABILITY_TABLE");
        AVAILABILITY_TABLE.setAccessible(true);
        AVAILABILITY_TABLE.set(null, Rbs.DB_SCHEMA + ".availability");

        this.availabilityServiceSql = Mockito.spy(new AvailabilityServiceSqlImpl());
        Sql.getInstance().init(vertx.eventBus(), "fr.openent.rbs");
    }

    @Test
    public void testListAvailabilities_Should_Return_SqlRequest_WithRessourcesId(TestContext ctx) {
        Async async = ctx.async();

        JsonArray resourcesIds = new JsonArray(Arrays.asList(10, 11, 12));

        String expectedQuery = "SELECT * FROM rbs.availability WHERE resource_id IN (?,?,?) ORDER BY start_date ASC";

        JsonArray expectedParams = new JsonArray()
                .add(10)
                .add(11)
                .add(12);

        vertx.eventBus().consumer("fr.openent.rbs", message -> {
            JsonObject body = (JsonObject) message.body();
            ctx.assertEquals("prepared", body.getString("action"));
            ctx.assertEquals(expectedQuery, body.getString("statement"));
            ctx.assertEquals(expectedParams.toString(), body.getJsonArray("values").toString());
            async.complete();
        });

        this.availabilityServiceSql.listAvailabilities(resourcesIds, null);
    }

    @Test
    public void testListAvailabilities_Should_Return_SqlRequest_WithRessourcesSetAsNULLOrEmpty(TestContext ctx) {
        Async async = ctx.async();

        String expectedQuery = "SELECT * FROM rbs.availability ORDER BY start_date ASC";

        vertx.eventBus().consumer("fr.openent.rbs", message -> {
            JsonObject body = (JsonObject) message.body();
            ctx.assertEquals("prepared", body.getString("action"));
            ctx.assertEquals(expectedQuery, body.getString("statement"));
            async.complete();
        });

        this.availabilityServiceSql.listAvailabilities(null, null);
    }
}
