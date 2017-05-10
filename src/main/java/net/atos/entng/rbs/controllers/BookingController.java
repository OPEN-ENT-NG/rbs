/*
 * Copyright © Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package net.atos.entng.rbs.controllers;

import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import net.atos.entng.rbs.filters.TypeAndResourceAppendPolicy;
import net.atos.entng.rbs.service.BookingService;
import net.atos.entng.rbs.service.BookingServiceSqlImpl;
import net.atos.entng.rbs.service.ResourceService;
import net.atos.entng.rbs.service.ResourceServiceSqlImpl;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static net.atos.entng.rbs.BookingStatus.*;
import static net.atos.entng.rbs.BookingUtils.*;
import static net.atos.entng.rbs.Rbs.RBS_NAME;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;

public class BookingController extends ControllerHelper {

    private static final String BOOKING_CREATED_EVENT_TYPE = RBS_NAME + "_BOOKING_CREATED";
    private static final String BOOKING_UPDATED_EVENT_TYPE = RBS_NAME + "_BOOKING_UPDATED";
    private static final String BOOKING_DELETED_EVENT_TYPE = RBS_NAME + "_BOOKING_DELETED";
    private static final String BOOKING_VALIDATED_EVENT_TYPE = RBS_NAME + "_BOOKING_VALIDATED";
    private static final String BOOKING_REFUSED_EVENT_TYPE = RBS_NAME + "_BOOKING_REFUSED";
    private static final String PERIODIC_BOOKING_CREATED_EVENT_TYPE = RBS_NAME + "_PERIODIC_BOOKING_CREATED";
    private static final String PERIODIC_BOOKING_UPDATED_EVENT_TYPE = RBS_NAME + "_PERIODIC_BOOKING_UPDATED";
    private static final String PERIODIC_BOOKING_DELETED_EVENT_TYPE = RBS_NAME + "_PERIODIC_BOOKING_DELETED";
    private final static String DATE_FORMAT = BookingServiceSqlImpl.DATE_FORMAT;
    private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    private static final I18n i18n = I18n.getInstance();


    private final BookingService bookingService;
    private final ResourceService resourceService;

    public BookingController() {
        bookingService = new BookingServiceSqlImpl();
        resourceService = new ResourceServiceSqlImpl();
    }

    @Post("/resource/:id/booking")
    @ApiDoc("Create booking of a given resource")
    @SecuredAction(value = "rbs.contrib", type = ActionType.RESOURCE)
    @ResourceFilter(TypeAndResourceAppendPolicy.class)
    public void createBooking(final HttpServerRequest request) {

        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    RequestUtils.bodyToJson(request, pathPrefix + "createBooking",
                            getBookingHandler(user, request, true));
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    /**
     * @param isCreation : true when creating a booking, false when updating a booking.
     * @return Handler to create or update a booking
     */
    private Handler<JsonObject> getBookingHandler(final UserInfos user,
                                                  final HttpServerRequest request, final boolean isCreation) {

        return new Handler<JsonObject>() {
            @Override
            public void handle(final JsonObject object) {
                final String resourceId = request.params().get("id");
                final String bookingId = request.params().get("bookingId");

                final long startDate = object.getLong("start_date", 0L);
                final long endDate = object.getLong("end_date", 0L);
                final long now = getCurrentTimestamp();

                resourceService.getDelaysAndTypeProperties(Long.parseLong(resourceId), new Handler<Either<String, JsonObject>>() {
                    @Override
                    public void handle(Either<String, JsonObject> event) {
                        if (event.isRight() && event.right().getValue() != null) {

                            JsonObject resource = event.right().getValue();
                            String owner = resource.getString("owner", null);
                            String schoolId = resource.getString("school_id", null);
                            String jsonString = resource.getString("managers", null);
                            JsonArray managers = (jsonString != null) ? new JsonArray(jsonString) : null;

                            if (owner == null || schoolId == null) {
                                log.warn("Could not get owner or school_id for type of resource " + resourceId);
                            }

                            if (!canBypassDelaysConstraints(owner, schoolId, user, managers)) {
                                // check that booking dates respect min and max delays
                                if (isDelayLessThanMin(resource, startDate, now)) {
                                    long nbDays = TimeUnit.DAYS.convert(resource.getLong("min_delay"), TimeUnit.SECONDS);
                                    String errorMessage = i18n.translate(
                                            "rbs.booking.bad.request.minDelay.not.respected",
                                            I18n.acceptLanguage(request),
                                            Long.toString(nbDays));

                                    badRequest(request, errorMessage);
                                    return;
                                } else if (isDelayGreaterThanMax(resource, endDate, now)) {
                                    long nbDays = TimeUnit.DAYS.convert(resource.getLong("max_delay"), TimeUnit.SECONDS);
                                    String errorMessage = i18n.translate(
                                            "rbs.booking.bad.request.maxDelay.not.respected",
                                            I18n.acceptLanguage(request),
                                            Long.toString(nbDays));

                                    badRequest(request, errorMessage);
                                    return;
                                }
                            }

                            Handler<Either<String, JsonObject>> handler = new Handler<Either<String, JsonObject>>() {
                                @Override
                                public void handle(Either<String, JsonObject> event) {
                                    if (event.isRight()) {
                                        if (event.right().getValue() != null && event.right().getValue().size() > 0) {
                                            notifyBookingCreatedOrUpdated(request, user, event.right().getValue(), isCreation);
                                            renderJson(request, event.right().getValue(), 200);
                                        } else {
                                            String errorMessage = isCreation ?
                                                    "rbs.booking.create.conflict" : "rbs.booking.update.conflict";
                                            JsonObject error = new JsonObject()
                                                    .putString("error", errorMessage);
                                            renderJson(request, error, 409);
                                        }
                                    } else {
                                        badRequest(request, event.left().getValue());
                                    }
                                }
                            };

                            if (isCreation) {
                                bookingService.createBooking(resourceId, object, user, handler);
                            } else {
                                bookingService.updateBooking(resourceId, bookingId, object, handler);
                            }


                        } else {
                            badRequest(request, event.left().getValue());
                        }
                    }

                });
            }
        };
    }

    /*
     * Owner or managers of a resourceType, as well as local administrators of a resourceType's schoolId,
     * do no need to respect constraints on resources' delays
     */
    private boolean canBypassDelaysConstraints(String owner, String schoolId, UserInfos user, JsonArray managers) {
        if (user.getUserId().equals(owner)) {
            return true;
        }

        List<String> scope = getLocalAdminScope(user);
        if (scope != null && !scope.isEmpty() && scope.contains(schoolId)) {
            return true;
        }

        if (managers != null && managers.size() > 0) {
            // Create a list containing userId and groupIds of current user
            List<String> userAndGroupIds = new ArrayList<>();
            userAndGroupIds.add(user.getUserId());
            userAndGroupIds.addAll(user.getGroupsIds());

            // Return true if managers and userAndGroupIds have at least one common element
            if (!Collections.disjoint(userAndGroupIds, managers.toList())) {
                return true;
            }
        }

        return false;
    }


    /**
     * Notify moderators that a booking has been created or updated
     */
    private void notifyBookingCreatedOrUpdated(final HttpServerRequest request, final UserInfos user,
                                               final JsonObject message, final boolean isCreated) {

        final long id = message.getLong("id", 0L);
        final int status = message.getInteger("status", 0);
        final String startDate = message.getString("start_date", null);
        final String endDate = message.getString("end_date", null);

        final String eventType;
        final String notificationName;
        if (isCreated) {
            eventType = BOOKING_CREATED_EVENT_TYPE;
            notificationName = "booking-created";
        } else {
            eventType = BOOKING_UPDATED_EVENT_TYPE;
            notificationName = "booking-updated";
        }

        if (id == 0L || status == 0 || startDate == null || endDate == null) {
            log.error("Could not get bookingId, status, start_date or end_date from response. Unable to send timeline " + eventType + " notification.");
            return;
        }
        final String bookingId = Long.toString(id);

        // Do NOT send a notification if the booking has been automatically validated
        if (CREATED.status() == status) {

            bookingService.getResourceName(bookingId, new Handler<Either<String, JsonObject>>() {
                @Override
                public void handle(Either<String, JsonObject> event) {
                    if (event.isRight() && event.right().getValue() != null
                            && event.right().getValue().size() > 0) {

                        final String resourceName = event.right().getValue().getString("resource_name");

                        bookingService.getModeratorsIds(bookingId, user, new Handler<Either<String, JsonArray>>() {
                            @Override
                            public void handle(Either<String, JsonArray> event) {
                                if (event.isRight() && event.right() != null) {
                                    notifyModerators(request, user, event.right().getValue(),
                                            bookingId, startDate, endDate,
                                            resourceName, eventType, notificationName);
                                } else {
                                    log.error("Error when calling service getModeratorsIds. Unable to send timeline "
                                            + eventType + " notification.");
                                }
                            }
                        });

                    } else {
                        log.error("Error when calling service getResourceName. Unable to send timeline "
                                + eventType + " notification.");
                    }
                }
            });
        }

    }

    @Post("/resource/:id/booking/periodic")
    @ApiDoc("Create periodic booking of a given resource")
    @SecuredAction(value = "rbs.contrib", type = ActionType.RESOURCE)
    @ResourceFilter(TypeAndResourceAppendPolicy.class)
    public void createPeriodicBooking(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    RequestUtils.bodyToJson(request, pathPrefix + "createPeriodicBooking",
                            new CreateOrUpdatePeriodicHandler(user, request, true));
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    public class CreateOrUpdatePeriodicHandler implements Handler<JsonObject> {
        private final UserInfos user;
        private final HttpServerRequest request;
        private final boolean isCreation;

        public CreateOrUpdatePeriodicHandler(final UserInfos user,
                                             final HttpServerRequest request,
                                             final boolean isCreation) {
            this.user = user;
            this.request = request;
            this.isCreation = isCreation;
        }

        private String booleanArrayToBitString(JsonArray selectedDaysArray) {
            StringBuilder selectedDays = new StringBuilder();
            for (Object day : selectedDaysArray) {
                int isSelectedDay = ((Boolean) day) ? 1 : 0;
                selectedDays.append(isSelectedDay);
            }
            return selectedDays.toString();
        }

        @Override
        public void handle(final JsonObject booking) {
            final String resourceId = request.params().get("id");
            final String bookingId = request.params().get("bookingId");

            final long endDate = booking.getLong("periodic_end_date", 0L);
            final int occurrences = booking.getInteger("occurrences", 0);
            if (endDate == 0L && occurrences == 0) {
                badRequest(request, "rbs.booking.bad.request.enddate.or.occurrences");
                return;
            }

            final long firstSlotStartDate = booking.getLong("start_date", 0L);
            final long firstSlotEndDate = booking.getLong("end_date", 0L);

            // The first slot must begin and end on the same day
            final int firstSlotDay = getDayFromTimestamp(firstSlotStartDate);

            // cannot change the past
            if (firstSlotStartDate < getCurrentTimestamp()) {
                badRequest(request, "rbs.booking.bad.request.startdate.in.past.periodic.update");
                return;
            }

            JsonArray selectedDaysArrayTest = new JsonArray();
            int i = 0;
            for (Object obj : booking.getArray("days",null)) {
                if (getDayFromTimestamp(firstSlotEndDate) - getDayFromTimestamp(firstSlotStartDate) >= 1 && i == firstSlotDay) {
                    selectedDaysArrayTest.addBoolean(new Boolean(true));
                } else {
                    selectedDaysArrayTest.addBoolean((Boolean) obj);
                }
                i++;
            }

            final JsonArray selectedDaysArray = selectedDaysArrayTest;
            if (selectedDaysArray == null || selectedDaysArray.size() != 7) {
                badRequest(request, "rbs.booking.bad.request.invalid.days");
                return;
            }
            try {
                Object firstSlotDayIsSelected = selectedDaysArray.toList().get(firstSlotDay);
                // The day of the first slot must be a selected day
                if(!(Boolean) firstSlotDayIsSelected) {
                    badRequest(request, "rbs.booking.bad.request.first.day.not.selected");
                    return;
                }
            } catch (Exception e) {
                log.error("Error when checking that the day of the first slot is selected", e);
                renderError(request);
                return;
            }

            // The first and last slot must end at the same hour
            if (endDate > 0L && !haveSameTime(endDate, firstSlotEndDate)) {
                badRequest(request, "rbs.booking.bad.request.invalid.enddates");
                return;
            }

            // Store boolean array (selected days) as a bit string
            final String selectedDays;
            try {
                selectedDays = booleanArrayToBitString(selectedDaysArray);
            } catch (Exception e) {
                log.error("Error during processing of array 'days'", e);
                renderError(request);
                return;
            }

            resourceService.getDelaysAndTypeProperties(Long.parseLong(resourceId), new Handler<Either<String, JsonObject>>() {
                @Override
                public void handle(Either<String, JsonObject> event) {
                    if (event.isRight() && event.right().getValue() != null) {
                        JsonObject resourceDelayAndType = event.right().getValue();
                        Either<Boolean, String> checkResult = checkResourceConstraintsRespectedByPeriodicBooking(request, user, resourceId, resourceDelayAndType, booking);
                        if (checkResult.isRight() && checkResult.right().getValue() != null) {
                            badRequest(request, checkResult.right().getValue());
                            return;
                        } else if (checkResult.isLeft() && !checkResult.left().getValue()) {
                            renderError(request);
                            return;
                        }
                        // Create or update booking
                        if (isCreation) {
                            try {
                                bookingService.createPeriodicBooking(resourceId, selectedDays,
                                        firstSlotDay, booking, user,
                                        getHandlerForPeriodicNotification(user, request, isCreation));
                            } catch (Exception e) {
                                log.error("Error during service createPeriodicBooking", e);
                                renderError(request);
                            }
                        } else {
                            bookingService.getBooking(bookingId, new Handler<Either<String, JsonObject>>() {
                                @Override
                                public void handle(Either<String, JsonObject> event) {
                                    if (event.isRight() && event.right().getValue() != null) {
                                        JsonObject bookingToUpdate = event.right().getValue();
                                        try {
                                            final Date startDate = parseTimestampFromDB(bookingToUpdate.getString("start_date"));
                                            long startTimeInSec = TimeUnit.SECONDS.convert(startDate.getTime(), TimeUnit.MILLISECONDS);
                                            final long nowInSec = getCurrentTimestamp();
                                            // if already started: deleteFuturePeriodicBooking then create new PeriodicBooking
                                            // else just update
                                            if (startTimeInSec < nowInSec) {
                                                bookingService.deleteFuturePeriodicBooking(bookingId, startDate, new Handler<Either<String, JsonArray>>() {
                                                    @Override
                                                    public void handle(Either<String, JsonArray> event) {
                                                        if (event.isRight() && event.right().getValue() != null) {
                                                            getHandlerForPeriodicNotification(user, request, false).handle(event);
                                                            bookingService.createPeriodicBooking(resourceId, selectedDays,
                                                                    firstSlotDay, booking, user,
                                                                    getHandlerForPeriodicNotification(user, request, true));
                                                        } else {
                                                            badRequest(request, event.left().getValue());
                                                        }
                                                    }
                                                });
                                            } else {
                                                bookingService.updatePeriodicBooking(resourceId, bookingId, selectedDays,
                                                        firstSlotDay, booking, user,
                                                        getHandlerForPeriodicNotification(user, request, isCreation));
                                            }
                                        } catch (ParseException e) {
                                            log.error("Error during service updatePeriodicBooking", e);
                                            renderError(request);
                                        }

                                    } else {
                                        badRequest(request, event.left().getValue());
                                    }
                                }
                            });
                        }

                    } else {
                        badRequest(request, event.left().getValue());
                    }
                }

            });

        }


        private Either<Boolean, String> checkResourceConstraintsRespectedByPeriodicBooking(HttpServerRequest request, UserInfos user, String resourceId, JsonObject resourceDelayAndType,
                                                                                           JsonObject booking) {

            String owner = resourceDelayAndType.getString("owner", null);
            String schoolId = resourceDelayAndType.getString("school_id", null);
            String jsonString = resourceDelayAndType.getString("managers", null);
            JsonArray managers = (jsonString != null) ? new JsonArray(jsonString) : null;

            if (owner == null || schoolId == null) {
                log.warn("Could not get owner or school_id for type of resource " + resourceId);
            }

            if (!canBypassDelaysConstraints(owner, schoolId, user, managers)) {
                final long now = getCurrentTimestamp();
                // Check that booking dates respect min and max delays
                final long firstSlotStartDate = booking.getLong("start_date", 0L);
                final int firstSlotDay = getDayFromTimestamp(firstSlotStartDate);
                if (isDelayLessThanMin(resourceDelayAndType, firstSlotStartDate, now)) {
                    long nbDays = TimeUnit.DAYS.convert(resourceDelayAndType.getLong("min_delay"), TimeUnit.SECONDS);
                    String errorMessage = i18n.translate(
                            "rbs.booking.bad.request.minDelay.not.respected.by.firstSlot",
                            I18n.acceptLanguage(request),
                            Long.toString(nbDays));
                    return new Either.Right<>(errorMessage);
                } else {
                    final JsonArray selectedDaysArray = booking.getArray("days", null);
                    long lastSlotEndDate;
                    final long endDate = booking.getLong("periodic_end_date", 0L);
                    final long firstSlotEndDate = booking.getLong("end_date", 0L);
                    final int periodicity = booking.getInteger("periodicity");
                    String selectedDays = booleanArrayToBitString(selectedDaysArray);

                    if (endDate > 0L) { // Case when end_date is supplied
                        try {
                            int endDateDay = getDayFromTimestamp(endDate);
                            Object endDateDayIsSelected = selectedDaysArray.toList().get(endDateDay);

                            if ((Boolean) endDateDayIsSelected) {
                                lastSlotEndDate = endDate;
                            } else {
                                // If the endDateDay is not a selected day, compute the end date of the last slot
                                long durationInDays = TimeUnit.DAYS.convert(endDate - firstSlotEndDate, TimeUnit.SECONDS);
                                int nbOccurrences = getOccurrences(firstSlotDay, selectedDays, durationInDays, periodicity);
                                lastSlotEndDate = getLastSlotDate(nbOccurrences, periodicity, firstSlotEndDate, firstSlotDay, selectedDays);

                                // Replace the end date with the last slot's end date
                                booking.putNumber("periodic_end_date", lastSlotEndDate);
                                // Put the computed value of occurrences
                                booking.putNumber("occurrences", nbOccurrences);
                            }
                        } catch (Exception e) {
                            log.error("Error when checking that the day of the end date is selected", e);
                            return new Either.Left<>(false);
                        }

                    } else { // Case when occurrences is supplied
                        final int occurrences = booking.getInteger("occurrences", 0);
                        lastSlotEndDate = getLastSlotDate(occurrences, periodicity, firstSlotEndDate, firstSlotDay, selectedDays);
                    }

                    if (isDelayGreaterThanMax(resourceDelayAndType, lastSlotEndDate, now)) {
                        long nbDays = TimeUnit.DAYS.convert(resourceDelayAndType.getLong("max_delay"), TimeUnit.SECONDS);
                        String errorMessage = i18n.translate(
                                "rbs.booking.bad.request.maxDelay.not.respected.by.lastSlot",
                                I18n.acceptLanguage(request),
                                Long.toString(nbDays));
                        return new Either.Right<>(errorMessage);
                    }
                }
            }
            return new Either.Left<>(true);
        }

    }

    private Handler<Either<String, JsonArray>> getHandlerForPeriodicNotification(final UserInfos user,
                                                                                 final HttpServerRequest request, final boolean isCreation) {
        return new Handler<Either<String, JsonArray>>() {
            @Override
            public void handle(Either<String, JsonArray> event) {
                if (event.isRight()) {
                    notifyPeriodicBookingCreatedOrUpdated(request, user, event.right().getValue(), isCreation);
                    Renders.renderJson(request, event.right().getValue());
                } else {
                    badRequest(request, event.left().getValue());
                }
            }
        };
    }

    private void notifyPeriodicBookingCreatedOrUpdated(final HttpServerRequest request, final UserInfos user,
                                                       final JsonArray childBookings, final boolean isCreation) {

        // Send a notification if there is at least one child booking with status "created"
        boolean sendNotification = false;
        try {
            for (Object booking : childBookings) {
                int status = ((JsonObject) booking).getInteger("status", 0);
                if (CREATED.status() == status) {
                    sendNotification = true;
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error in method notifyPeriodicBookingCreatedOrUpdated");
            return;
        }

        if (sendNotification && childBookings != null && childBookings.get(0) != null) {
            JsonObject firstBooking = (JsonObject) childBookings.get(0);
            final long id = firstBooking.getLong("id", 0L);

            final String eventType;
            final String notificationName;
            if (isCreation) {
                eventType = PERIODIC_BOOKING_CREATED_EVENT_TYPE;
                notificationName = "periodic-booking-created";
            } else {
                eventType = PERIODIC_BOOKING_UPDATED_EVENT_TYPE;
                notificationName = "periodic-booking-updated";
            }

            if (id == 0L) {
                log.error("Could not get bookingId from response. Unable to send timeline " + eventType + " notification.");
                return;
            }
            final String bookingId = Long.toString(id);

            bookingService.getParentBooking(bookingId, new Handler<Either<String, JsonObject>>() {
                @Override
                public void handle(Either<String, JsonObject> event) {
                    if (event.isRight() && event.right().getValue() != null
                            && event.right().getValue().size() > 0) {

                        JsonObject parentBooking = event.right().getValue();
                        final long pId = parentBooking.getLong("id", 0L);
                        final String startDate = parentBooking.getString("start_date", null);
                        final String endDate = parentBooking.getString("end_date", null);
                        final String resourceName = parentBooking.getString("resource_name", null);

                        if (pId == 0L || startDate == null || endDate == null || resourceName == null) {
                            log.error("Could not get bookingId, start_date, end_date or resource_name from response. Unable to send timeline " +
                                    PERIODIC_BOOKING_CREATED_EVENT_TYPE + " or " + PERIODIC_BOOKING_UPDATED_EVENT_TYPE + " notification.");
                            return;
                        }
                        final String periodicBookingId = Long.toString(pId);

                        bookingService.getModeratorsIds(periodicBookingId, user,
                                new Handler<Either<String, JsonArray>>() {
                                    @Override
                                    public void handle(Either<String, JsonArray> event) {
                                        if (event.isRight() && event.right() != null) {
                                            notifyModerators(request, user, event.right().getValue(),
                                                    bookingId, startDate, endDate,
                                                    resourceName, eventType, notificationName);
                                        } else {
                                            log.error("Error when calling service getModeratorsIds. Unable to send timeline "
                                                    + eventType + " notification.");
                                        }
                                    }
                                });
                    } else {
                        log.error("Error when calling service getParentBooking. Unable to send timeline "
                                + eventType + " notification.");
                    }
                }
            });
        }

    }

    private void notifyModerators(final HttpServerRequest request, final UserInfos user, JsonArray moderators,
                                  final String bookingId, final String startDate, final String endDate, final String resourceName,
                                  final String eventType, final String notificationName) {

        final Set<String> recipientSet = new HashSet<>();
        final AtomicInteger remaining = new AtomicInteger(moderators.size());

        for (Object o : moderators) {
            JsonObject jo = (JsonObject) o;
            String userId = jo.getString("user_id");
            if (userId != null) {
                recipientSet.add(userId);
                remaining.getAndDecrement();
            } else {
                String groupId = jo.getString("group_id");
                if (groupId != null) {
                    UserUtils.findUsersInProfilsGroups(groupId, eb, user.getUserId(), false, new Handler<JsonArray>() {
                        @Override
                        public void handle(JsonArray event) {
                            if (event != null) {
                                for (Object o : event) {
                                    if (!(o instanceof JsonObject)) continue;
                                    JsonObject j = (JsonObject) o;
                                    String id = j.getString("id");
                                    recipientSet.add(id);
                                }
                            }
                            if (remaining.decrementAndGet() < 1 && !recipientSet.isEmpty()) {
                                sendNotification(request, user, bookingId, startDate, endDate, resourceName, eventType, notificationName, recipientSet);
                            }
                        }
                    });
                }
            }
            if (remaining.get() < 1 && !recipientSet.isEmpty()) {
                sendNotification(request, user, bookingId, startDate, endDate, resourceName, eventType, notificationName, recipientSet);
            }
        }

    }

    private void sendNotification(final HttpServerRequest request, final UserInfos user,
                                  final String bookingId, final String startDate, final String endDate, final String resourceName,
                                  final String eventType, final String notificationName, final Set<String> recipientSet) {

        List<String> recipients = new ArrayList<>(recipientSet);

        JsonObject params = new JsonObject();
        params.putString("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType());
        params.putString("bookingUri", "/rbs#/booking/" + bookingId + "/" + formatStringForRoute(startDate))
                .putString("username", user.getUsername())
                .putString("startdate", startDate)
                .putString("enddate", endDate)
                .putString("resourcename", resourceName);
        params.putString("resourceUri", params.getString("bookingUri"));

        notification.notifyTimeline(request, "rbs." + notificationName, user, recipients, bookingId, params);
    }


    @Put("/resource/:id/booking/:bookingId")
    @ApiDoc("Update booking")
    @SecuredAction(value = "rbs.contrib", type = ActionType.RESOURCE)
    @ResourceFilter(TypeAndResourceAppendPolicy.class)
    public void updateBooking(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    RequestUtils.bodyToJson(request, pathPrefix + "updateBooking",
                            getBookingHandler(user, request, false));
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @Put("/resource/:id/booking/:bookingId/periodic")
    @ApiDoc("Update periodic booking")
    @SecuredAction(value = "rbs.contrib", type = ActionType.RESOURCE)
    @ResourceFilter(TypeAndResourceAppendPolicy.class)
    public void updatePeriodicBooking(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    RequestUtils.bodyToJson(request, pathPrefix + "updatePeriodicBooking",
                            new CreateOrUpdatePeriodicHandler(user, request, false));
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @Put("/resource/:id/booking/:bookingId/process")
    @ApiDoc("Validate or refuse booking")
    @SecuredAction(value = "rbs.publish", type = ActionType.RESOURCE)
    @ResourceFilter(TypeAndResourceAppendPolicy.class)
    public void processBooking(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    RequestUtils.bodyToJson(request, pathPrefix + "processBooking", new Handler<JsonObject>() {
                        @Override
                        public void handle(JsonObject object) {
                            String resourceId = request.params().get("id");
                            final String bookingId = request.params().get("bookingId");

                            int newStatus = object.getInteger("status");
                            if (newStatus != VALIDATED.status()
                                    && newStatus != REFUSED.status()) {
                                badRequest(request, "Invalid status");
                                return;
                            }

                            object.putString("moderator_id", user.getUserId());

                            Handler<Either<String, JsonArray>> handler = new Handler<Either<String, JsonArray>>() {
                                @Override
                                public void handle(Either<String, JsonArray> event) {
                                    if (event.isRight()) {
                                        if (event.right().getValue() != null && event.right().getValue().size() >= 3) {
                                            final JsonArray results = event.right().getValue();

                                            try {
                                                final JsonObject processedBooking = ((JsonArray) results.get(2)).get(0);

                                                Handler<Either<String, JsonObject>> notifHandler = new Handler<Either<String, JsonObject>>() {
                                                    @Override
                                                    public void handle(Either<String, JsonObject> event) {
                                                        if (event.isRight() && event.right().getValue() != null
                                                                && event.right().getValue().size() > 0) {

                                                            final String resourceName = event.right().getValue().getString("resource_name");

                                                            notifyBookingProcessed(request, user, processedBooking, resourceName);

                                                            if (results.size() >= 4) {
                                                                JsonArray concurrentBookings = results.get(3);
                                                                for (Object o : concurrentBookings) {
                                                                    JsonObject booking = (JsonObject) o;
                                                                    notifyBookingProcessed(request, user, booking, resourceName);
                                                                }
                                                            }

                                                        } else {
                                                            log.error("Error when calling service getResourceName. Unable to send timeline notification.");
                                                        }
                                                    }
                                                };

                                                bookingService.getResourceName(bookingId, notifHandler);
                                                renderJson(request, processedBooking);
                                            } catch (Exception e) {
                                                log.error("Unable to send timeline notification. Error when processing response from worker mod-mysql-postgresql :");
                                                log.error(e.getMessage(), e);
                                                renderJson(request, event.right().getValue());
                                            }
                                        } else {
                                            log.error("Unable to send timeline notification. Response from worker mod-mysql-postgresql is null or has a size < 3");
                                            renderJson(request, new JsonObject());
                                        }
                                    } else {
                                        badRequest(request, event.left().getValue());
                                    }
                                }
                            };

                            bookingService.processBooking(resourceId,
                                    bookingId, newStatus, object, user, handler);
                        }
                    });
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    private void notifyBookingProcessed(final HttpServerRequest request, final UserInfos user,
                                        final JsonObject booking, final String resourceName) {

        final long id = booking.getLong("id", 0L);
        final int status = booking.getInteger("status", 0);
        final String owner = booking.getString("owner", null);
        final String startDate = booking.getString("start_date", null);
        final String endDate = booking.getString("end_date", null);

        final String notificationName;

        if (id == 0L || status == 0 || owner == null || owner.trim().length() == 0
                || startDate == null || endDate == null) {
            log.error("Could not get bookingId, status, owner, start_date or end_date from response. Unable to send timeline " +
                    BOOKING_VALIDATED_EVENT_TYPE + " or " + BOOKING_REFUSED_EVENT_TYPE + " notification.");
            return;
        }
        final String bookingId = Long.toString(id);

        if (VALIDATED.status() == status) {
            notificationName = "booking-validated";
        } else if (REFUSED.status() == status) {
            notificationName = "booking-refused";
        } else {
            log.error("Invalid status");
            return;
        }

        if (!owner.equals(user.getUserId())) {
            JsonObject params = new JsonObject();
            params.putString("username", user.getUsername())
                    .putString("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
                    .putString("startdate", startDate)
                    .putString("enddate", endDate)
                    .putString("resourcename", resourceName)
                    .putString("bookingUri", "/rbs#/booking/" + bookingId + "/" + formatStringForRoute(startDate));
            params.putString("resourceUri", params.getString("bookingUri"));

            List<String> recipients = new ArrayList<>();
            recipients.add(owner);

            notification.notifyTimeline(request, "rbs." + notificationName, user, recipients, bookingId, params);
        }
    }

    @Delete("/resource/:id/booking/:bookingId/:booleanThisAndAfter")
    @ApiDoc("Delete booking")
    @SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
    @ResourceFilter(TypeAndResourceAppendPolicy.class)
    public void deleteBooking(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    final String bookingId = request.params().get("bookingId");
                    bookingService.getBookingWithResourceName(bookingId, new Handler<Either<String, JsonObject>>() {
                        @Override
                        public void handle(Either<String, JsonObject> event) {
                            if (event.isRight()) {
                                if (event.right().getValue() != null && event.right().getValue().size() > 0) {
                                    final JsonObject booking = event.right().getValue();
                                    String thisAndAfterString = request.params().get("booleanThisAndAfter");
                                    boolean testString = false;
                                    if (thisAndAfterString.equals("true")) {
                                        testString = true;
                                    }
                                    final boolean thisAndAfter = testString;
                                    if (thisAndAfter) {
                                        if (booking.getLong("parent_booking_id") != null) {
                                            bookingService.getParentBooking(bookingId, new Handler<Either<String, JsonObject>>() {
                                                @Override
                                                public void handle(Either<String, JsonObject> event) {
                                                    try {
                                                        JsonObject parentBooking = event.right().getValue();
                                                        final long pId = parentBooking.getLong("id", 0L);
                                                        final String pStartDate = parentBooking.getString("start_date", null);
                                                        final String pEndDate = parentBooking.getString("end_date", null);
                                                        final Date endDate = parseDateFromDB(pEndDate);
                                                        long now = Calendar.getInstance().getTimeInMillis();
                                                        if (endDate.getTime() < now) {
                                                            String errorMessage = i18n.translate(
                                                                    "rbs.booking.bad.request.deletion.periodical.booking.already.terminated",
                                                                    Renders.getHost(request),
                                                                    I18n.acceptLanguage(request));
                                                            badRequest(request, errorMessage);
                                                            return;
                                                        }
                                                        final Date startDate = parseDateFromDB(booking.getString("start_date"));
                                                        try {
                                                            bookingService.deleteFuturePeriodicBooking(String.valueOf(pId), startDate,
                                                                    getHandlerForPeriodicNotification(user, request, false));
                                                        } catch (Exception e) {
                                                            log.error("Error during service deleteFuturePeriodicBooking", e);
                                                            renderError(request);
                                                        }
                                                        return;
                                                    } catch (ParseException e) {
                                                        log.error("Can't parse date form RBS DB", e);
                                                    }
                                                }
                                            });
                                        } else {
                                            String errorMessage = i18n.translate(
                                                    "rbs.booking.bad.request.deletion.booking.with.true",
                                                    Renders.getHost(request),
                                                    I18n.acceptLanguage(request));
                                            badRequest(request, errorMessage);
                                            return;
                                        }
                                    } else {
                                        try {
                                            long now = Calendar.getInstance().getTimeInMillis();
                                            final String endDate = booking.getString("end_date");
                                            if (booking.getLong("parent_booking_id") != null && parseDateFromDB(endDate).getTime() < now) {
                                                String errorMessage = i18n.translate(
                                                        "rbs.booking.bad.request.deletion.periodical.booking.already.terminated",
                                                        Renders.getHost(request),
                                                        I18n.acceptLanguage(request));
                                                badRequest(request, errorMessage);
                                                return;
                                            }
                                        } catch (ParseException e) {
                                            e.printStackTrace();
                                        }
                                        bookingService.delete(bookingId, user, new Handler<Either<String, JsonObject>>() {
                                            @Override
                                            public void handle(Either<String, JsonObject> event) {
                                                if (event.isRight()) {
                                                    if (event.right().getValue() != null && event.right().getValue().size() > 0) {
                                                        try {
                                                            notifyBookingDeleted(request, user, booking, bookingId);
                                                        } catch (Exception e) {
                                                            log.error("Unable to send timeline " + BOOKING_DELETED_EVENT_TYPE
                                                                    + " or " + PERIODIC_BOOKING_DELETED_EVENT_TYPE + " notification.");
                                                        }
                                                        Renders.renderJson(request, event.right().getValue(), 204);
                                                    } else {
                                                        notFound(request);
                                                    }
                                                } else {
                                                    badRequest(request, event.left().getValue());
                                                }
                                            }
                                        });
                                    }

                                } else {
                                    notFound(request);
                                }
                            } else {
                                badRequest(request, event.left().getValue());
                            }
                        }
                    });

                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    private void notifyBookingDeleted(final HttpServerRequest request, final UserInfos user,
                                      final JsonObject booking, final String bookingId) {

        final String owner = booking.getString("owner", null);
        final String startDate = booking.getString("start_date", null);
        final String endDate = booking.getString("end_date", null);
        final boolean isPeriodic = booking.getBoolean("is_periodic");
        final String resourceName = booking.getString("resource_name", null);

        final String notificationName;

        if (startDate == null || endDate == null ||
                owner == null || owner.trim().isEmpty() ||
                resourceName == null || resourceName.trim().isEmpty()) {
            log.error("Could not get start_date, end_date, owner or resource_name from response. Unable to send timeline " +
                    BOOKING_DELETED_EVENT_TYPE + " or " + PERIODIC_BOOKING_DELETED_EVENT_TYPE + " notification.");
            return;
        }

        // Notify only if current user is not the booking's owner
        if (!owner.equals(user.getUserId())) {
            if (isPeriodic) {
                notificationName = "periodic-booking-deleted";
            } else {
                notificationName = "booking-deleted";
            }

            JsonObject params = new JsonObject();
            params.putString("username", user.getUsername())
                    .putString("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
                    .putString("startdate", startDate)
                    .putString("enddate", endDate)
                    .putString("resourcename", resourceName);

            List<String> recipients = new ArrayList<>();
            recipients.add(owner);

            notification.notifyTimeline(request, "rbs." + notificationName, user, recipients, bookingId, params);
        }
    }

    /*
     * TODO review the ux functional with clients to allow paging in listing view.
     */
    @Get("/bookings/all")
    @ApiDoc("List all bookings")
    @SecuredAction("rbs.booking.list.all")
    public void listAllBookings(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    final List<String> groupsAndUserIds = new ArrayList<>();
                    groupsAndUserIds.add(user.getUserId());
                    if (user.getGroupsIds() != null) {
                        groupsAndUserIds.addAll(user.getGroupsIds());
                    }
                    bookingService.listAllBookings(user, groupsAndUserIds, arrayResponseHandler(request));
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/booking/:id")
    @ApiDoc("find start date of one booking")
    @SecuredAction(value = "rbs.booking.one.id", type = ActionType.AUTHENTICATED)
    public void getBooking(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    bookingService.getBooking(request.params().get("id"), new Handler<Either<String, JsonObject>>() {
                        @Override
                        public void handle(Either<String, JsonObject> event) {
                            if (event.isRight()) {
                                Renders.renderJson(request, event.right().getValue());
                            } else {
                                Renders.renderError(request, new JsonObject().putString("error", event.left().getValue()));
                            }
                        }
                    });
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/bookings/all/:startdate/:enddate")
    @ApiDoc("List all bookings by dates")
    @SecuredAction("rbs.booking.list.all.dates")
    public void listAllBookingsByDate(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    final String startDate = request.params().get("startdate");
                    final String endDate = request.params().get("enddate");
                    if (startDate != null && endDate != null &&
                            startDate.matches("\\d{4}-\\d{2}-\\d{2}") && endDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        final List<String> groupsAndUserIds = new ArrayList<>();
                        groupsAndUserIds.add(user.getUserId());
                        if (user.getGroupsIds() != null) {
                            groupsAndUserIds.addAll(user.getGroupsIds());
                        }

                        bookingService.listAllBookingsByDates(user, groupsAndUserIds, startDate, endDate, new Handler<Either<String, JsonArray>>() {
                            @Override
                            public void handle(Either<String, JsonArray> event) {
                                if (event.isRight()) {
                                    Renders.renderJson(request, event.right().getValue());
                                } else {
                                    JsonObject error = new JsonObject()
                                            .putString("error", event.left().getValue());
                                    Renders.renderJson(request, error, 400);
                                }
                            }
                        });
                    } else {
                        Renders.badRequest(request, "params start and end date must be defined with YYYY-MM-DD format !");
                    }
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/bookings/full/slots/:bookingId")
    @ApiDoc("List all booking slots of a periodic booking")
    @SecuredAction(value = "rbs.booking.list.slots")
    public void listFullSlotsBooking(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    final String bookingId = request.params().get("bookingId");
                    if (bookingId != null) {

                        bookingService.listFullSlotsBooking(bookingId, new Handler<Either<String, JsonArray>>() {
                            @Override
                            public void handle(Either<String, JsonArray> event) {
                                if (event.isRight()) {
                                    Renders.renderJson(request, event.right().getValue());
                                } else {
                                    JsonObject error = new JsonObject()
                                            .putString("error", event.left().getValue());
                                    Renders.renderJson(request, error, 400);
                                }
                            }
                        });
                    } else {
                        Renders.badRequest(request, "param booking id must be defined !");
                    }
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/bookings")
    @ApiDoc("List all bookings created by current user")
    @SecuredAction("rbs.booking.list")
    public void listUserBookings(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    bookingService.listUserBookings(user, arrayResponseHandler(request));
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/resource/:id/bookings")
    @ApiDoc("List all bookings for a given resource")
    @SecuredAction(value = "rbs.read", type = ActionType.RESOURCE)
    @ResourceFilter(TypeAndResourceAppendPolicy.class)
    public void listBookingsByResource(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    String resourceId = request.params().get("id");
                    bookingService.listBookingsByResource(resourceId, arrayResponseHandler(request));
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/bookings/unprocessed")
    @ApiDoc("List all bookings waiting to be processed by current user")
    @SecuredAction("rbs.booking.list.unprocessed")
    public void listUnprocessedBookings(final HttpServerRequest request) {

        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    final List<String> groupsAndUserIds = new ArrayList<>();
                    groupsAndUserIds.add(user.getUserId());
                    if (user.getGroupsIds() != null) {
                        groupsAndUserIds.addAll(user.getGroupsIds());
                    }

                    bookingService.listUnprocessedBookings(groupsAndUserIds, user, arrayResponseHandler(request));
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });

    }

    /**
     * Transforms an SQL formatted date to a java formatted date
     *
     * @param strDate formatted string to be transformed from SQL type
     * @return formatted string to java date
     */
    private String formatStringForRoute(String strDate) {
        Date target;
        try {
            target = parseDateFromDB(strDate);
        } catch (ParseException e) {
            log.error("Cannot parse startDate", e);
            return null;
        }
        return new SimpleDateFormat("yyyy-MM-dd").format(target);
    }

    /**
     * Transforms an SQL formatted date to a java date
     *
     * @param strDate formatted string to be transformed from SQL type
     * @return parsed java date
     */
    private Date parseDateFromDB(String strDate) throws ParseException {
        String format = DATE_FORMAT;
        // the format is adapted to SQL. So we have to parse and replace some fields.
        format = format.replace("MI", "mm");
        format = format.replace("HH24", "HH");
        format = format.replace("DD", "dd");
        format = format.replace("YY", "yy");
        SimpleDateFormat formatter = new SimpleDateFormat(format);
        return formatter.parse(strDate);
    }

    /**
     * Transforms an SQL formatted timestamp to a java date
     *
     * @param strDate formatted string to be transformed from SQL type
     * @return parsed java date
     */
    private Date parseTimestampFromDB(String strDate) throws ParseException {
        String format = TIMESTAMP_FORMAT;

        SimpleDateFormat formatter = new SimpleDateFormat(format);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.parse(strDate);
    }


}
