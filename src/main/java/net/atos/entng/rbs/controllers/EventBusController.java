package net.atos.entng.rbs.controllers;

import fr.wseduc.bus.BusAddress;
import fr.wseduc.webutils.Either;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.rbs.core.constants.Field;
import net.atos.entng.rbs.models.Booking;
import net.atos.entng.rbs.models.Resource;
import net.atos.entng.rbs.service.BookingService;
import org.entcore.common.bus.BusResponseHandler;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.user.UserUtils;

import java.util.List;
import java.util.stream.Collectors;

public class EventBusController extends ControllerHelper {

    private final BookingService bookingService;

    public EventBusController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Handles the event bus
     * @param message {@link Message<JsonObject>} the information recieved
     */
    @BusAddress("net.atos.entng.rbs")
    public void bus(final Message<JsonObject> message) {
        JsonObject body = message.body();
        String action = body.getString(Field.ACTION);
        String userId = body.getString(Field.USERID);
        switch (action) {
            case "save-bookings":
                UserUtils.getUserInfos(eb, userId, user -> {
                    JsonArray bookingsArray = body.getJsonArray(Field.BOOKINGS);
                    List<Booking> bookings = bookingsArray
                            .stream()
                            .map((bookingObject) -> {
                                return new Booking(((JsonObject) bookingObject),
                                        new Resource(((JsonObject)bookingObject).getJsonObject(Field.RESOURCE, new JsonObject())));
                            })
                            .collect(Collectors.toList());
                    List<Integer> resourceIds = bookingsArray.stream()
                            .map((booking) -> ((JsonObject)booking).getJsonObject(Field.RESOURCE, new JsonObject()).getInteger(Field.ID, null))
                            .collect(Collectors.toList());

                    bookingService.createBookings(resourceIds, bookings, user)
                        .onSuccess((res) -> BusResponseHandler.busArrayHandler(message).handle(new Either.Right<>(res)))
                        .onFailure((err) -> BusResponseHandler.busArrayHandler(message).handle(new Either.Left<>(err.getMessage())));
                });

                break;
            case "delete-bookings":
                UserUtils.getUserInfos(eb, userId, user -> {
                    List<Integer> bookings = body.getJsonArray(Field.BOOKINGS).getList();
                    bookingService.checkRightsAndDeleteBookings(bookings, user)
                        .onSuccess((res) -> BusResponseHandler.busArrayHandler(message).handle(new Either.Right<>(new JsonArray(res))))
                        .onFailure((err) -> BusResponseHandler.busArrayHandler(message).handle(new Either.Left<>(err.getMessage())));
                });

                break;
            default:
                message.reply(new JsonObject()
                        .put("status", "error")
                        .put("message", "Invalid action."));
        }
    }

}