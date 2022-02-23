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

import static net.atos.entng.rbs.BookingStatus.*;
import static net.atos.entng.rbs.Rbs.RBS_NAME;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.vertx.core.buffer.Buffer;
import net.atos.entng.rbs.BookingUtils;
import net.atos.entng.rbs.Rbs;
import net.atos.entng.rbs.models.Slots;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.events.EventHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.rbs.filters.TypeAndResourceAppendPolicy;
import net.atos.entng.rbs.model.ExportBooking;
import net.atos.entng.rbs.model.ExportRequest;
import net.atos.entng.rbs.model.ExportResponse;
import net.atos.entng.rbs.service.*;
import net.atos.entng.rbs.service.pdf.PdfExportService;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import org.vertx.java.core.http.RouteMatcher;


import static net.atos.entng.rbs.BookingUtils.*;

import net.atos.entng.rbs.models.Booking;
import net.atos.entng.rbs.models.Resource;
import net.atos.entng.rbs.service.BookingService;
import net.atos.entng.rbs.service.BookingServiceSqlImpl;
import net.atos.entng.rbs.service.ResourceService;
import net.atos.entng.rbs.service.ResourceServiceSqlImpl;

public class BookingController extends ControllerHelper {
	static final String RESOURCE_NAME = "resource_booking";

	private static final String BOOKING_CREATED_EVENT_TYPE = RBS_NAME + "_BOOKING_CREATED";
	private static final String BOOKING_UPDATED_EVENT_TYPE = RBS_NAME + "_BOOKING_UPDATED";
	private static final String BOOKING_DELETED_EVENT_TYPE = RBS_NAME + "_BOOKING_DELETED";
	private static final String BOOKING_VALIDATED_EVENT_TYPE = RBS_NAME + "_BOOKING_VALIDATED";
	private static final String BOOKING_REFUSED_EVENT_TYPE = RBS_NAME + "_BOOKING_REFUSED";
	private static final String PERIODIC_BOOKING_CREATED_EVENT_TYPE = RBS_NAME + "_PERIODIC_BOOKING_CREATED";
	private static final String PERIODIC_BOOKING_UPDATED_EVENT_TYPE = RBS_NAME + "_PERIODIC_BOOKING_UPDATED";
	private static final String PERIODIC_BOOKING_DELETED_EVENT_TYPE = RBS_NAME + "_PERIODIC_BOOKING_DELETED";

	private static final I18n i18n = I18n.getInstance();

	private final SchoolService schoolService;
	private final BookingService bookingService;
	private final ResourceService resourceService;
	private BookingNotificationService bookingNotificationService;
	private final EventHelper eventHelper;


	public BookingController(EventBus eb) {
		super();
		bookingService = new BookingServiceSqlImpl();
		resourceService = new ResourceServiceSqlImpl();
		schoolService = new SchoolService(eb);
		final EventStore eventStore = EventStoreFactory.getFactory().getEventStore(Rbs.class.getSimpleName());
		eventHelper = new EventHelper(eventStore);
	}

	@Override
	public void init(Vertx vertx, JsonObject config, RouteMatcher rm, Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, config, rm, securedActions);
		bookingNotificationService = new BookingNotificationService(BookingController.log, eb, notification, bookingService);
	}

	@Post("/resource/:id/booking")
	@ApiDoc("Create booking of a given resource")
	@SecuredAction(value = "rbs.contrib", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	public void createBooking(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, user -> {
			if (user != null) {
				RequestUtils.bodyToJson(request, pathPrefix + "createBooking", getBookingHandler(user, request, true));
			} else {
				log.debug("User not found in session.");
				unauthorized(request);
			}
		});
	}

	/**
	 * @param isCreation : true when creating a booking, false when updating a booking.
	 *
	 * @return Handler to create or update a booking
	 */
	private Handler<JsonObject> getBookingHandler(final UserInfos user, final HttpServerRequest request, final boolean isCreation) {
		return json -> {
			final String resourceId = request.params().get("id");
			final String bookingId = request.params().get("bookingId");
			Slots slots = new Slots(json.getJsonArray("slots")) ;
			Booking booking = new Booking(json, bookingId, slots);

			resourceService.getDelaysAndTypeProperties(Long.parseLong(resourceId), event -> {
				if (event.isRight() && event.right().getValue() != null) {
					JsonObject json1 = event.right().getValue();
					Resource resource = new Resource(json1);
					booking.setResource(resource);
					if (resource.hasNotOwnerOrSchoolId()) {
						log.warn("Could not get owner or school_id for type of resource " + resourceId);
					}

					if (!resource.canBypassDelaysConstraints(user)) {
						// check that booking dates respect min and max delays
						if (booking.hasMinDelay() && booking.slotsNotRespectingMinDelay()) {
							long nbDays = booking.minDelayAsDay();
							String errorMessage = i18n.translate("rbs.booking.bad.request.minDelay.not.respected",
									Renders.getHost(request), I18n.acceptLanguage(request), Long.toString(nbDays));
							badRequest(request, errorMessage);
							return;
						} else if (booking.hasMaxDelay() && booking.slotsNotRespectingMaxDelay()) {
							long nbDays = booking.maxDelayAsDay();
							String errorMessage = i18n.translate(
									"rbs.booking.bad.request.maxDelay.not.respected",
									Renders.getHost(request), I18n.acceptLanguage(request),
									Long.toString(nbDays));

							badRequest(request, errorMessage);
							return;
						}
					}

					if (isCreation) {
						bookingService.createBooking(resourceId, booking, user, getBookingCreationResponse(user, request, isCreation));
					} else {
						bookingService.updateBooking(resourceId, booking, getBookingUpdateResponse(user, request, isCreation));
					}

				} else {
					badRequest(request, event.left().getValue());
				}
			});
		};
	}

	private Handler<Either<String, JsonArray>> getBookingCreationResponse(UserInfos user, HttpServerRequest request, boolean isCreation) {
		return event -> {
			if (event.isRight()) {
				if (event.right().getValue() != null && event.right().getValue().size() > 0) {
					List<JsonObject> bookings = event.right().getValue().stream().map(JsonObject.class::cast).collect(Collectors.toList());
					bookings.forEach(booking -> notifyBookingCreatedOrUpdated(request, user, booking, isCreation));
					renderJson(request, event.right().getValue().getJsonObject(0), 200);
					eventHelper.onCreateResource(request, RESOURCE_NAME);
				} else {
					JsonObject error = new JsonObject().put("error", event.left().getValue());
					renderJson(request, error, 409);
				}
			} else {
				badRequest(request, event.left().getValue());
			}
		};
	}

	private Handler<Either<String, JsonObject>> getBookingUpdateResponse(UserInfos user, HttpServerRequest request, boolean isCreation) {
		return event -> {
			if (event.isRight()) {
				if (event.right().getValue() != null && event.right().getValue().size() > 0) {
					notifyBookingCreatedOrUpdated(request, user, event.right().getValue(), isCreation);
					renderJson(request, event.right().getValue(), 200);
					eventHelper.onCreateResource(request, RESOURCE_NAME);
				} else {
					JsonObject error = new JsonObject().put("error", event.left().getValue());
					renderJson(request, error, 409);
				}
			} else {
				badRequest(request, event.left().getValue());
			}
		};
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
			log.error("Could not get bookingId, status, start_date or end_date from response. Unable to send timeline "
					+ eventType + " notification.");
			return;
		}
		final String bookingId = Long.toString(id);

		// Do NOT send a notification if the booking has been automatically validated
		if (CREATED.status() == status) {

			bookingService.getResourceName(bookingId, getResourceEvent -> {
				if (getResourceEvent.isRight() && getResourceEvent.right().getValue() != null && getResourceEvent.right().getValue().size() > 0) {

					final String resourceName = getResourceEvent.right().getValue().getString("resource_name");

					bookingService.getModeratorsIds(bookingId, user, getModeratorsEvent -> {
						if (getModeratorsEvent.isRight() && getModeratorsEvent.right() != null) {
							notifyModerators(request, user, getModeratorsEvent.right().getValue(), bookingId, startDate,
									endDate, resourceName, eventType, notificationName);
						} else {
							log.error("Error when calling service getModeratorsIds. Unable to send timeline "
									+ eventType + " notification.");
						}
					});

				} else {
					log.error("Error when calling service getResourceName. Unable to send timeline " + eventType
							+ " notification.");
				}
			});
		}

	}

	@Post("/resource/:id/booking/periodic")
	@ApiDoc("Create periodic booking of a given resource")
	@SecuredAction(value = "rbs.contrib", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	public void createPeriodicBooking(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, user -> {
			if (user != null) {
				RequestUtils.bodyToJson(request, pathPrefix + "createPeriodicBooking",
						getPeriodicBookingHandler(user, request, true));
			} else {
				log.debug("User not found in session.");
				unauthorized(request);
			}
		});
	}

	/**
	 * @return Handler to create or update a periodic booking
	 */
	private Handler<JsonObject> getPeriodicBookingHandler(final UserInfos user, final HttpServerRequest request,
			final boolean isCreation) {

		return json -> {
			final String resourceId = request.params().get("id");
			final String bookingId = request.params().get("bookingId");

			Booking booking = new Booking(json, null, bookingId);

			if (booking.isNotPeriodic()) {
				badRequest(request, "rbs.booking.bad.request.enddate.or.occurrences");
				return;
			}

			// The first slot must begin and end on the same day
			if (booking.getSlots().areNotStartingAndEndingSameDay()) {
				badRequest(request, "rbs.booking.bad.request.invalid.first.slot");
				return;
			}

			if (booking.hasNotSelectedDays()) {
				badRequest(request, "rbs.booking.bad.request.invalid.days");
				return;
			}
			try {
				// The day of the first slot must be a selected day
				if (booking.hasNotSelectedStartDayOfWeek()) {
					badRequest(request, "rbs.booking.bad.request.first.day.not.selected");
					return;
				}
			} catch (Exception e) {
				log.error("Error when checking that the day of the first slot is selected", e);
				renderError(request);
				return;
			}



			// Store boolean array (selected days) as a bit string
			try {
				booking.computeSelectedDaysAsBitString();
			} catch (Exception e) {
				log.error("Error during processing of array 'days'", e);
				renderError(request);
				return;
			}

			resourceService.getDelaysAndTypeProperties(Long.parseLong(resourceId), event -> {
				if (event.isRight() && event.right().getValue() != null) {

					JsonObject json1 = event.right().getValue();
					Resource resource = new Resource(json1);
					booking.setResource(resource);

					if (resource.hasNotOwnerOrSchoolId()) {
						log.warn("Could not get owner or school_id for type of resource " + resourceId);
					}

					if (!resource.canBypassDelaysConstraints(user)) {
						// Check that booking dates respect min and max delays
						if (booking.hasMinDelay() && booking.slotsNotRespectingMinDelay()) {
							long nbDays = booking.minDelayAsDay();
							String errorMessage = i18n.translate(
									"rbs.booking.bad.request.minDelay.not.respected.by.firstSlot",
									Renders.getHost(request), I18n.acceptLanguage(request),
									Long.toString(nbDays));

							badRequest(request, errorMessage);
							return;
						} else if (booking.hasMaxDelay()) {
							try {
								if (booking.slotsNotRespectingMaxDelay()) {
									long nbDays = booking.maxDelayAsDay();
									String errorMessage = i18n.translate(
											"rbs.booking.bad.request.maxDelay.not.respected.by.lastSlot",
											Renders.getHost(request), I18n.acceptLanguage(request),
											Long.toString(nbDays));

									badRequest(request, errorMessage);
									return;
								}
							} catch (Exception e) {
								log.error("Error when checking that the day of the end date is selected", e);
								renderError(request);
								return;
							}

						}
					}

					// Create or update booking
					if (isCreation) {
						try {
							bookingService.createPeriodicBooking(resourceId, booking, user,
									getHandlerForPeriodicNotification(user, request, isCreation));
						} catch (Exception e) {
							log.error("Error during service createPeriodicBooking", e);
							renderError(request);
						}
					} else {
						try {
							bookingService.updatePeriodicBooking(resourceId, booking, user,
									getHandlerForPeriodicNotification(user, request, isCreation));
						} catch (Exception e) {
							log.error("Error during service updatePeriodicBooking", e);
							renderError(request);
						}
					}

				} else {
					badRequest(request, event.left().getValue());
				}
			});
		};
	}

	private Handler<Either<String, JsonArray>> getHandlerForPeriodicNotification(final UserInfos user,
			final HttpServerRequest request, final boolean isCreation) {
		return event -> {
			if (event.isRight()) {
				notifyPeriodicBookingCreatedOrUpdated(request, user, event.right().getValue(), isCreation);
				Renders.renderJson(request, event.right().getValue());
				eventHelper.onCreateResource(request, RESOURCE_NAME);
			} else {
				badRequest(request, event.left().getValue());
			}
		};
	}

	private void notifyPeriodicBookingCreatedOrUpdated(final HttpServerRequest request, final UserInfos user,
			final JsonArray childBookings, final boolean isCreation) {

		// Send a notification if there is at least one child booking with status
		// "created"
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

		if (sendNotification && childBookings != null && childBookings.getJsonObject(0) != null) {
			JsonObject firstBooking = childBookings.getJsonObject(0);
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
				log.error("Could not get bookingId from response. Unable to send timeline " + eventType
						+ " notification.");
				return;
			}
			final String bookingId = Long.toString(id);

			bookingService.getParentBooking(bookingId, getParentEvent -> {
				if (getParentEvent.isRight() && getParentEvent.right().getValue() != null && getParentEvent.right().getValue().size() > 0) {

					JsonObject parentBooking = getParentEvent.right().getValue();
					final long pId = parentBooking.getLong("id", 0L);
					final String startDate = parentBooking.getString("start_date", null);
					final String endDate = parentBooking.getString("end_date", null);
					final String resourceName = parentBooking.getString("resource_name", null);

					if (pId == 0L || startDate == null || endDate == null || resourceName == null) {
						log.error(
								"Could not get bookingId, start_date, end_date or resource_name from response. Unable to send timeline "
										+ PERIODIC_BOOKING_CREATED_EVENT_TYPE + " or "
										+ PERIODIC_BOOKING_UPDATED_EVENT_TYPE + " notification.");
						return;
					}
					final String periodicBookingId = Long.toString(pId);

					bookingService.getModeratorsIds(periodicBookingId, user, getModeratorsEvent -> {
						if (getModeratorsEvent.isRight() && getModeratorsEvent.right() != null) {
							notifyModerators(request, user, getModeratorsEvent.right().getValue(), bookingId,
									startDate, endDate, resourceName, eventType, notificationName);
						} else {
							log.error("Error when calling service getModeratorsIds. Unable to send timeline " + eventType + " notification.");
						}
					});
				} else {
					log.error("Error when calling service getParentBooking. Unable to send timeline " + eventType
							+ " notification.");
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
									if (!(o instanceof JsonObject))
										continue;
									JsonObject j = (JsonObject) o;
									String id = j.getString("id");
									recipientSet.add(id);
								}
							}
							if (remaining.decrementAndGet() < 1 && !recipientSet.isEmpty()) {
								sendNotification(request, user, bookingId, startDate, endDate, resourceName, eventType,
										notificationName, recipientSet);
							}
						}
					});
				}
			}
			if (remaining.get() < 1 && !recipientSet.isEmpty()) {
				sendNotification(request, user, bookingId, startDate, endDate, resourceName, eventType,
						notificationName, recipientSet);
			}
		}

	}

	private void sendNotification(final HttpServerRequest request, final UserInfos user, final String bookingId,
			final String startDate, final String endDate, final String resourceName, final String eventType,
			final String notificationName, final Set<String> recipientSet) {

		List<String> recipients = new ArrayList<>(recipientSet);

        request.bodyHandler(event -> {
			String iana = event.toJsonObject().getString("iana");
			if(null == iana){
				iana = event.toJsonObject().getJsonArray("slots").getJsonObject(0).getString("iana");
			}
			String startDateTZ = ZonedDateTime.of(LocalDateTime.parse(startDate,DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")), ZoneOffset.UTC).withZoneSameInstant(ZoneId.of(iana)).format(DateTimeFormatter.ofPattern("dd/MM/yy HH:mm"));
			String endDateTZ = ZonedDateTime.of(LocalDateTime.parse(endDate,DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")), ZoneOffset.UTC).withZoneSameInstant(ZoneId.of(iana)).format(DateTimeFormatter.ofPattern("dd/MM/yy HH:mm"));

			JsonObject params = new JsonObject();
			params.put("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType());
			params.put("bookingUri", "/rbs#/booking/" + bookingId + "/" + formatStringForRoute(startDate))
					.put("username", user.getUsername()).put("startdate", startDateTZ).put("enddate", endDateTZ)
					.put("resourcename", resourceName);
			params.put("resourceUri", params.getString("bookingUri"));
			params.put("pushNotif", getPushNotification(request, notificationName, user, resourceName, startDateTZ, endDateTZ));

			notification.notifyTimeline(request, "rbs." + notificationName, user, recipients, resourceName, bookingId, params, true);
		});
	}

	private JsonObject getPushNotification(HttpServerRequest request, String notificationName, UserInfos user,
			String resourceName, String startDate, String endDate) {
		JsonObject notification = new JsonObject().put("title", "rbs.push.notif." + notificationName);
		String body = i18n.translate("rbs.push.notif." + notificationName + ".body", getHost(request),
				I18n.acceptLanguage(request), user.getUsername(), resourceName, startDate, endDate);
		notification.put("body", body);
		return notification;
	}

	@Put("/resource/:id/booking/:bookingId")
	@ApiDoc("Update booking")
	@SecuredAction(value = "rbs.contrib", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	public void updateBooking(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, user -> {
			if (user != null) {
				RequestUtils.bodyToJson(request, pathPrefix + "updateBooking",
						getBookingHandler(user, request, false));
			} else {
				log.debug("User not found in session.");
				unauthorized(request);
			}
		});
	}

	@Put("/resource/:id/booking/:bookingId/periodic")
	@ApiDoc("Update periodic booking")
	@SecuredAction(value = "rbs.contrib", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	public void updatePeriodicBooking(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, user -> {
			if (user != null) {
				RequestUtils.bodyToJson(request, pathPrefix + "updatePeriodicBooking",
						getPeriodicBookingHandler(user, request, false));
			} else {
				log.debug("User not found in session.");
				unauthorized(request);
			}
		});
	}

	@Put("/resource/:id/booking/:bookingId/process")
	@ApiDoc("Validate or refuse booking")
	@SecuredAction(value = "rbs.publish", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	public void processBooking(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, user -> {
			if (user != null) {
				RequestUtils.bodyToJson(request, pathPrefix + "processBooking", json -> {
					String resourceId = request.params().get("id");
					final String bookingId = request.params().get("bookingId");

					int newStatus = json.getInteger("status");

					if (newStatus != VALIDATED.status() && newStatus != REFUSED.status() && newStatus != SUSPENDED.status() && newStatus != CREATED.status()) {
						badRequest(request, "Invalid status");
						return;
					}

					json.put("moderator_id", user.getUserId());

					Handler<Either<String, JsonArray>> handler = processEvent -> {
						if (processEvent.isRight()) {
							if (processEvent.right().getValue() != null && processEvent.right().getValue().size() >= 3) {
								final JsonArray results = processEvent.right().getValue();

								try {
									final JsonObject processedBooking = results.getJsonArray(2).getJsonObject(0);

									Handler<Either<String, JsonObject>> notifHandler = notifyEvent -> {
										if (notifyEvent.isRight() && notifyEvent.right().getValue() != null
												&& notifyEvent.right().getValue().size() > 0) {

											final String resourceName = notifyEvent.right().getValue().getString("resource_name");

											notifyBookingProcessed(request, user, processedBooking, resourceName);

											if (results.size() >= 4) {
												JsonArray concurrentBookings = results.getJsonArray(3);
												for (Object o : concurrentBookings) {
													JsonObject booking = (JsonObject) o;
													notifyBookingProcessed(request, user, booking, resourceName);
												}
											}

										} else {
											log.error("Error when calling service getResourceName. Unable to send timeline notification.");
										}
									};

									bookingService.getResourceName(bookingId, notifHandler);
									renderJson(request, processedBooking);
								} catch (Exception e) {
									log.error("Unable to send timeline notification. Error when processing response from worker mod-mysql-postgresql :");
									log.error(e.getMessage(), e);
									renderJson(request, processEvent.right().getValue());
								}
							} else {
								log.error(
										"Unable to send timeline notification. Response from worker mod-mysql-postgresql is null or has a size < 3");
								renderJson(request, new JsonObject());
							}
						} else {
							badRequest(request, processEvent.left().getValue());
						}
					};

					bookingService.processBooking(resourceId, bookingId, newStatus, json, user, handler);
				});
			} else {
				log.debug("User not found in session.");
				unauthorized(request);
			}
		});
	}

	private void notifyBookingProcessed(final HttpServerRequest request, final UserInfos user, final JsonObject booking,
			final String resourceName) {

		final long id = booking.getLong("id", 0L);
		final int status = booking.getInteger("status", 0);
		final String owner = booking.getString("owner", null);
		final String startDate = booking.getString("start_date", null);
		final String endDate = booking.getString("end_date", null);

		final String notificationName;

		if (id == 0L || status == 0 || owner == null || owner.trim().length() == 0 || startDate == null || endDate == null) {
			log.error("Could not get bookingId, status, owner, start_date or end_date from response. Unable to send timeline "
					+ BOOKING_VALIDATED_EVENT_TYPE + " or " + BOOKING_REFUSED_EVENT_TYPE + " notification.");
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
			params.put("username", user.getUsername())
					.put("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
					.put("startdate", startDate).put("enddate", endDate).put("resourcename", resourceName)
					.put("bookingUri", "/rbs#/booking/" + bookingId + "/" + formatStringForRoute(startDate));
			params.put("resourceUri", params.getString("bookingUri"));
			params.put("pushNotif",
					getPushNotification(request, notificationName, user, resourceName, startDate, endDate));

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
		UserUtils.getUserInfos(eb, request, user -> {
			if (user != null) {
				final String bookingId = request.params().get("bookingId");
				final String resourceId = request.params().get("id");
				bookingService.getBookingWithResourceName(bookingId, event -> {
					if (event.isRight() && event.right().getValue() != null && event.right().getValue().size() > 0) {
						final JsonObject booking = event.right().getValue();
						String thisAndAfterString = request.params().get("booleanThisAndAfter");
						if (thisAndAfterString.equals("true")) {
							if (booking.getLong("parent_booking_id") != null) {
								bookingService.getParentBooking(bookingId, event1 -> {
									try {
										JsonObject parentBooking = event1.right().getValue();
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
											bookingService.deleteFuturePeriodicBooking(String.valueOf(pId), startDate, deleteEvent -> {
												if (deleteEvent.isRight()) {
													bookingNotificationService.notifyPeriodicBookingCreatedOrUpdated(resourceService, request, user, deleteEvent.right().getValue(), false, Long.parseLong(resourceId));
													Renders.renderJson(request, deleteEvent.right().getValue());
												} else {
													badRequest(request, deleteEvent.left().getValue());
												}
											});
											} catch (Exception e) {
												log.error("Error during service deleteFuturePeriodicBooking", e);
												renderError(request);
											}
											return;
										} catch (ParseException e) {
											log.error("Can't parse date form RBS DB", e);
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
									long now = BookingUtils.getCurrentTimestamp();
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
								bookingService.delete(bookingId, user, event12 -> {
									if (event12.isRight() && event12.right().getValue() != null && event12.right().getValue().size() > 0) {
											bookingNotificationService.notifyBookingDeleted(resourceService, request, user, booking, bookingId, Long.parseLong(resourceId));
											Renders.renderJson(request, event12.right().getValue(), 204);
									} else {
										badRequest(request, event12.left().getValue());
									}
								});
							}

					} else {
						badRequest(request, event.left().getValue());
					}
				});

			} else {
				log.debug("User not found in session.");
				unauthorized(request);
			}
		});
	}

	private void notifyBookingDeleted(final HttpServerRequest request, final UserInfos user, final JsonObject booking,
			final String bookingId) {

		final String owner = booking.getString("owner", null);
		final String startDate = booking.getString("start_date", null);
		final String endDate = booking.getString("end_date", null);
		final boolean isPeriodic = booking.getBoolean("is_periodic");
		final String resourceName = booking.getString("resource_name", null);

		final String notificationName;

		if (startDate == null || endDate == null || owner == null || owner.trim().isEmpty() || resourceName == null
				|| resourceName.trim().isEmpty()) {
			log.error(
					"Could not get start_date, end_date, owner or resource_name from response. Unable to send timeline "
							+ BOOKING_DELETED_EVENT_TYPE + " or " + PERIODIC_BOOKING_DELETED_EVENT_TYPE
							+ " notification.");
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
			params.put("username", user.getUsername())
					.put("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
					.put("startdate", startDate).put("enddate", endDate).put("resourcename", resourceName);
			params.put("pushNotif",
					getPushNotification(request, notificationName, user, resourceName, startDate, endDate));

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
		UserUtils.getUserInfos(eb, request, user -> {
			if (user != null) {
				bookingService.getBooking(request.params().get("id"), getEvent -> {
					if (getEvent.isRight()) {
						Renders.renderJson(request, getEvent.right().getValue());
					} else {
						Renders.renderError(request, new JsonObject().put("error", getEvent.left().getValue()));
					}
				});
			} else {
				log.debug("User not found in session.");
				unauthorized(request);
			}
		});
	}

	@Get("/bookings/all/:startdate/:enddate")
	@ApiDoc("List all bookings by dates")
	@SecuredAction("rbs.booking.list.all.dates")
	public void listAllBookingsByDate(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, user -> {
			if (user != null) {
				final String startDate = request.params().get("startdate");
				final String endDate = request.params().get("enddate");
				if (startDate != null && endDate != null && startDate.matches("\\d{4}-\\d{2}-\\d{2}")
						&& endDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
					final List<String> groupsAndUserIds = new ArrayList<>();
					groupsAndUserIds.add(user.getUserId());
					if (user.getGroupsIds() != null) {
						groupsAndUserIds.addAll(user.getGroupsIds());
					}

					bookingService.listAllBookingsByDates(user, groupsAndUserIds, startDate, endDate, listEvent -> {
						if (listEvent.isRight()) {
							Renders.renderJson(request, listEvent.right().getValue());
						} else {
							JsonObject error = new JsonObject().put("error", listEvent.left().getValue());
							Renders.renderJson(request, error, 400);
						}
					});
				} else {
					Renders.badRequest(request,
							"params start and end date must be defined with YYYY-MM-DD format !");
				}
			} else {
				log.debug("User not found in session.");
				unauthorized(request);
			}
		});
	}

	@Get("/bookings/full/slots/:bookingId")
	@ApiDoc("List all booking slots of a periodic booking")
	@SecuredAction(value = "rbs.booking.list.slots")
	public void listFullSlotsBooking(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, user -> {
			if (user != null) {
				final String bookingId = request.params().get("bookingId");
				if (bookingId != null) {

					bookingService.listFullSlotsBooking(bookingId, listEvent -> {
						if (listEvent.isRight()) {
							Renders.renderJson(request, listEvent.right().getValue());
						} else {
							JsonObject error = new JsonObject().put("error", listEvent.left().getValue());
							Renders.renderJson(request, error, 400);
						}
					});
				} else {
					Renders.badRequest(request, "param booking id must be defined !");
				}
			} else {
				log.debug("User not found in session.");
				unauthorized(request);
			}
		});
	}

	@Get("/bookings")
	@ApiDoc("List all bookings created by current user")
	@SecuredAction("rbs.booking.list")
	public void listUserBookings(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, user -> {
			if (user != null) {
				bookingService.listUserBookings(user, arrayResponseHandler(request));
			} else {
				log.debug("User not found in session.");
				unauthorized(request);
			}
		});
	}

	@Get("/resource/:id/bookings")
	@ApiDoc("List all bookings for a given resource")
	@SecuredAction(value = "rbs.read", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	public void listBookingsByResource(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, user -> {
			if (user != null) {
				String resourceId = request.params().get("id");
				bookingService.listBookingsByResource(resourceId, arrayResponseHandler(request));
			} else {
				log.debug("User not found in session.");
				unauthorized(request);
			}
		});
	}

	@Get("/bookings/unprocessed")
	@ApiDoc("List all bookings waiting to be processed by current user")
	@SecuredAction("rbs.booking.list.unprocessed")
	public void listUnprocessedBookings(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, user -> {
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
		});

	}

	/**
	 * Transforms an SQL formatted date to a java formatted date
	 *
	 * @param strDate formatted string to be transformed from SQL type
	 * @return formatted string to java date
	 */
	private String formatStringForRoute(String strDate) {
		Date target = null;
		try {
			target = BookingUtils.parseDateFromDB(strDate);
		} catch (ParseException e) {
			log.error("Cannot parse startDate", e);
			return null;
		}
		return new SimpleDateFormat("yyyy-MM-dd").format(target);
	}
	@Post("/bookings/export")
	@ApiDoc("Export bookings in requested format")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void exportICal(final HttpServerRequest request) {
		RequestUtils.bodyToJson(request, pathPrefix + "exportBookings", json -> {
			UserUtils.getUserInfos(eb, request, user -> {
				if (user != null) {
					ExportRequest exportRequest;
					try {
						exportRequest = new ExportRequest(json, user);
					} catch (IllegalArgumentException e) {
						Renders.badRequest(request, e.getMessage());
						return;
					}
					final ExportResponse exportResponse = new ExportResponse(exportRequest);
					bookingService.getBookingsForExport(exportRequest, getEvent -> {
						if (getEvent.isRight()) {
							final List<ExportBooking> exportBookings = getEvent.right().getValue();
							if (exportBookings.isEmpty()) {
								JsonObject error = new JsonObject().put("error", "No booking in search period");
								Renders.renderJson(request, error, 204);
								return;
							}
							exportResponse.setBookings(exportBookings);

							schoolService.getSchoolNames(event -> {
								if (event.isRight()) {
									exportResponse.setSchoolNames(event.right().getValue());

									if (exportResponse.getRequest().getFormat().equals(ExportRequest.Format.PDF)) {
										generatePDF(request, exportResponse);
									} else {
										generateICal(request, exportResponse);
									}
								} else {
									JsonObject error = new JsonObject()
											.put("error", event.left().getValue());
									Renders.renderJson(request, error, 400);
								}
							});
						} else {
							JsonObject error = new JsonObject()
									.put("error", getEvent.left().getValue());
							Renders.renderJson(request, error, 400);
						}
					});

				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			});
		});
	}

	private void generateICal(final HttpServerRequest request, final ExportResponse exportResponse) {
		final JsonObject conversionRequest = new JsonObject();
		conversionRequest.put("action", "convert");
		final JsonObject dataToExport = exportResponse.toJson();
		conversionRequest.put("data", dataToExport);

		eb.send(IcalExportService.ICAL_HANDLER_ADDRESS, conversionRequest, (Handler<AsyncResult<Message<JsonObject>>>) event -> {
			JsonObject body = event.result().body();
			Integer status = body.getInteger("status");
			if (status == 200) {
				String icsContent = body.getString("content");
				request.response().putHeader("Content-Type", "text/calendar");
				request.response().putHeader("Content-Disposition",
						"attachment; filename=export.ics");
				request.response().end(Buffer.buffer(icsContent));
			} else {
				String errorMessage = body.getString("message");
				log.warn("An error occurred during ICal generation of " + exportResponse + " : " + errorMessage);
				JsonObject error = new JsonObject()
						.put("error", "ICal generation: " + errorMessage)
						.put("dataToExport", dataToExport);
				Renders.renderError(request, error);
			}
        });
	}

	private void generatePDF(final HttpServerRequest request, final ExportResponse exportResponse) {
		final JsonObject conversionRequest = new JsonObject();
		conversionRequest.put("action", "convert");
		final JsonObject dataToExport = exportResponse.toJson();
		conversionRequest.put("data", dataToExport);
		conversionRequest.put("scheme", getScheme(request));
		conversionRequest.put("host", Renders.getHost(request));
		conversionRequest.put("acceptLanguage", I18n.acceptLanguage(request));
		conversionRequest.put("userTimeZone", exportResponse.getRequest().getUserTz() );

		eb.request(PdfExportService.PDF_HANDLER_ADDRESS, conversionRequest, (Handler<AsyncResult<Message<JsonObject>>>) event -> {
			JsonObject body = event.result().body();
			Integer status = body.getInteger("status");
			if (status == 200) {
				byte[] pdfContent = body.getBinary("content");
				request.response().putHeader("Content-Type", "application/pdf");
				request.response().putHeader("Content-Disposition",
						"attachment; filename=export.pdf");
				request.response().end(Buffer.buffer(pdfContent));
			} else {
				String errorMessage = body.getString("message");
				log.warn("An error occurred during PDF generation of " + exportResponse + " : " + errorMessage);
				JsonObject error = new JsonObject()
						.put("error", "PDF generation: " + errorMessage)
						.put("dataToExport", dataToExport);
				Renders.renderError(request, error);
			}
        });
	}


}
