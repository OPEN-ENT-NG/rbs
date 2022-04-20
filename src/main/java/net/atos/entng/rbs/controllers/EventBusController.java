package net.atos.entng.rbs.controllers;

import fr.wseduc.bus.BusAddress;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.rbs.models.Booking;
import net.atos.entng.rbs.models.Resource;
import net.atos.entng.rbs.service.BookingService;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;

public class EventBusController extends ControllerHelper {

    private final BookingService bookingService;

    public EventBusController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @BusAddress("net.atos.entng.rbs")
    public void bus(final Message<JsonObject> message) {
        JsonObject body = message.body();
        String action = body.getString("action");
        switch (action) {
            case "save-bookings":
                String userId = body.getString("userId");
                UserUtils.getUserInfos(eb, userId, new Handler<UserInfos>() {
                    @Override
                    public void handle(final UserInfos user) {
                        JsonArray bookingsArray = body.getJsonArray("bookings");
                        List<Booking> bookings = bookingsArray
                                .stream()
                                .map((bookingObject) -> {
                                    return new Booking(((JsonObject) bookingObject), new Resource(((JsonObject)bookingObject).getJsonObject("resource")));
                                })
                                .collect(Collectors.toList());
                        List<Integer> typesId = bookingsArray.stream()
                                .map((booking) -> ((JsonObject)booking).getJsonObject("type").getInteger("id"))
                                .collect(Collectors.toList());

                        bookingService.createBookings(typesId, bookings, user);
//                        bookingService.create(structure, student, busResponseHandler(message));
                    }
                });

                break;
            default:
                message.reply(new JsonObject()
                        .put("status", "error")
                        .put("message", "Invalid action."));
        }
    }

    private static <T> Handler<AsyncResult<T>> busResponseHandler(final Message<T> message) {
        return event -> {
            if (event.succeeded()) {
                message.reply((new JsonObject()).put("status", "ok").put("result", true));
            } else {
                JsonObject error = (new JsonObject()).put("status", "error").put("message", event.cause().getMessage());
                message.reply(error);
            }
        };
    }
}