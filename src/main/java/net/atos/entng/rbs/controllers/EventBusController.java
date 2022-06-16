package net.atos.entng.rbs.controllers;

import fr.wseduc.bus.BusAddress;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Binding;
import fr.wseduc.webutils.http.HttpMethod;
import fr.wseduc.webutils.security.ActionType;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.rbs.core.constants.Field;
import net.atos.entng.rbs.filters.TypeAndResourceAppendPolicy;
import net.atos.entng.rbs.models.Booking;
import net.atos.entng.rbs.models.Resource;
import net.atos.entng.rbs.service.BookingService;
import org.entcore.common.bus.BusResponseHandler;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
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
                    Boolean isBookingOwner = body.getBoolean(Field.ISOWNER, null);
                    bookings.forEach(bookingId -> {
                        String bookingStringId = String.valueOf(bookingId);
                        if (Boolean.TRUE.equals(isBookingOwner)) {
//                            bookingService.delete(bookingStringId, user, res -> {
//                                if (res.isRight()) {
//                                    BusResponseHandler.busArrayHandler(message).handle(new Either.Right<>(result));
//                                } else if (res.isLeft()) {
//                                    BusResponseHandler.busArrayHandler(message).handle(new Either.Left<>(result));
//                                }
//                            });
                        } else {
                            bookingService.getBooking(bookingStringId, booking -> {
                                if (booking.isRight()) {
                                    String resourceId = booking.right().getValue().getLong(Field.RESOURCE_ID).toString();
                                    try {
                                        String method = BookingController.class.getName() +"|deleteBooking";
                                        Binding binding = new Binding(HttpMethod.DELETE, Pattern.compile(""), method, ActionType.RESOURCE);
                                        new TypeAndResourceAppendPolicy().authorize(resourceId, bookingStringId, binding, user, result -> {
                                            if (Boolean.TRUE.equals(result)) {
//                                                bookingService.delete(bookingStringId, user, res -> {
//                                                    if (res.isRight()) {
//                                                         BusResponseHandler.busArrayHandler(message).handle(new Either.Right<>(String.valueOf(result)));
//                                                    } else if (res.isLeft()) {
//                                                         BusResponseHandler.busArrayHandler(message).handle(new Either.Left<>(String.valueOf(result)));
//                                                    }
//                                                });
                                            } else {
                                                BusResponseHandler.busArrayHandler(message).handle(new Either.Left<>(String.valueOf(result)));
                                                log.info(String.format("[RBS@%s::bus] No deletion right: %s",
                                                        this.getClass().getSimpleName(), booking.left().getValue()));
                                            }
                                        });

                                    } catch (ClassCastException e) {
                                        log.error(String.format("[RBS@%s::bus] An error has occured when retrieving deletion rights: %s",
                                                this.getClass().getSimpleName(), booking.left().getValue()));
                                    }
                                } else if (booking.isLeft()){
                                    log.info(String.format("[RBS@%s::bus] An error has occured: %s",
                                            this.getClass().getSimpleName(), booking.left().getValue()));
                                }
                            });
                        }
                    });
                });

                break;
            default:
                message.reply(new JsonObject()
                        .put("status", "error")
                        .put("message", "Invalid action."));
        }
    }

}