package net.atos.entng.rbs.test.units.service.impl;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import net.atos.entng.rbs.models.Booking;
import net.atos.entng.rbs.service.impl.BookingServiceSqlImpl;
import org.entcore.common.sql.Sql;
import org.entcore.common.user.UserInfos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

@RunWith(VertxUnitRunner.class)
public class BookingServiceSqlImplTest {

    private BookingServiceSqlImpl bookingService;


    @Before
    public void setUp() {
        Vertx vertx = Vertx.vertx();
        this.bookingService = Mockito.spy(new BookingServiceSqlImpl());
        Sql.getInstance().init(vertx.eventBus(), "fr.openent.rbs");
    }

    @Test
    public void testCreateBookings(TestContext ctx) {
        Async async =  ctx.async();
        UserInfos userInfos = new UserInfos();
        List<Integer> resourcesIds = Arrays.asList(10, 11, 12);
        JsonArray slot1 = new JsonArray(Arrays.asList("{\"iana\": \"Europe/Paris\"}"));
        JsonArray slot2 = new JsonArray(Arrays.asList("{\"iana\": \"Europe/Paris\"}", "{\"iana\": \"Europe/Paris\"}"));
        JsonArray slot3 = new JsonArray();
        JsonObject jsonObject1 = new JsonObject().put("slots", slot1);
        JsonObject jsonObject2 = new JsonObject().put("slots", slot2);
        JsonObject jsonObject3 = new JsonObject().put("slots", slot3);
        List<Booking> bookingList = Arrays.asList(new Booking(jsonObject1, null), new Booking(jsonObject2, null), new Booking(jsonObject3, null));

        this.bookingService.createBookings(resourcesIds, bookingList, userInfos).onComplete(event -> {

            ctx.assertEquals(1, bookingList.get(0).getSlots().size());
            ctx.assertEquals(2, bookingList.get(1).getSlots().size());
            ctx.assertEquals(0, bookingList.get(2).getSlots().size());
            Mockito.verify(bookingService, Mockito.times(bookingList.size())).createBooking(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
            async.complete();
        });
    }
}
