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
package net.atos.entng.rbs.service;

import fr.wseduc.webutils.Either;
import net.atos.entng.rbs.BookingUtils;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static net.atos.entng.rbs.BookingStatus.*;
import static net.atos.entng.rbs.Rbs.RBS_NAME;

public class BookingNotificationService {
	private static final String BOOKING_CREATED_EVENT_TYPE = RBS_NAME + "_BOOKING_CREATED";
	private static final String BOOKING_UPDATED_EVENT_TYPE = RBS_NAME + "_BOOKING_UPDATED";
	private static final String BOOKING_DELETED_EVENT_TYPE = RBS_NAME + "_BOOKING_DELETED";
	private static final String BOOKING_VALIDATED_EVENT_TYPE = RBS_NAME + "_BOOKING_VALIDATED";
	private static final String BOOKING_REFUSED_EVENT_TYPE = RBS_NAME + "_BOOKING_REFUSED";
	private static final String PERIODIC_BOOKING_CREATED_EVENT_TYPE = RBS_NAME + "_PERIODIC_BOOKING_CREATED";
	private static final String PERIODIC_BOOKING_UPDATED_EVENT_TYPE = RBS_NAME + "_PERIODIC_BOOKING_UPDATED";
	private static final String PERIODIC_BOOKING_DELETED_EVENT_TYPE = RBS_NAME + "_PERIODIC_BOOKING_DELETED";

	private final TimelineHelper notification;
	private final Logger log;
	private BookingService bookingService;
	private EventBus eb;


	public BookingNotificationService(Logger log, EventBus eb, TimelineHelper notification, BookingService bookingService) {
		this.eb = eb;
		this.notification = notification;
		this.log = log;
		this.bookingService = bookingService;
	}

	public void notifyBookingDeleted(final ResourceService resourceService, final HttpServerRequest request, final UserInfos user,
	                                 final JsonObject booking, final String bookingId, final long resourceId) {
		try {
			final String owner = booking.getString("owner", null);
			final String startDate = booking.getString("start_date", null);
			final String endDate = booking.getString("end_date", null);
			final boolean isPeriodic = booking.getBoolean("is_periodic");
			final String resourceName = booking.getString("resource_name", null);


			if (startDate == null || endDate == null ||
					owner == null || owner.trim().isEmpty() ||
					resourceName == null || resourceName.trim().isEmpty()) {
				log.error("Could not get start_date, end_date, owner or resource_name from response. Unable to send timeline " +
						BOOKING_DELETED_EVENT_TYPE + " or " + PERIODIC_BOOKING_DELETED_EVENT_TYPE + " notification.");
				return;
			}

			final String notificationName;

			if (isPeriodic) {
				notificationName = "periodic-booking-deleted";
			} else {
				notificationName = "booking-deleted";
			}

			resourceService.getUserNotification(resourceId, user, new Handler<Either<String, JsonArray>>() {
				@Override
				public void handle(Either<String, JsonArray> event) {
					if (event.isRight()) {
						Set<String> recipientSet = new HashSet<>();
						for(Object o : event.right().getValue()){
							if(!(o instanceof JsonObject)){
								continue;
							}
							JsonObject jo = (JsonObject) o;
							recipientSet.add(jo.getString("user_id"));
						}

						if(!recipientSet.isEmpty()) {
							List<String> recipients = new ArrayList<>(recipientSet);
							if (!owner.equals(user.getUserId())) {
								recipients.add(owner);
							}
							JsonObject params = new JsonObject();
							params.putString("username", user.getUsername())
									.putString("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
									.putString("startdate", startDate)
									.putString("enddate", endDate)
									.putString("resourcename", resourceName);

							notification.notifyTimeline(request, "rbs." + notificationName, user, recipients, bookingId, params);
						}
					} else {
						log.error("Error when calling service getUserNotification. Unable to send timeline "
								+ " process notification.");
					}
				}
			});
		} catch (Exception e) {
			log.error("Unable to send timeline " + BOOKING_DELETED_EVENT_TYPE
					+ " or " + PERIODIC_BOOKING_DELETED_EVENT_TYPE + " notification.");
		}
	}

	/**
	 * Notify moderators that a booking has been created or updated
	 */
	public void notifyBookingCreatedOrUpdated(final ResourceService resourceService, final HttpServerRequest request, final UserInfos user,
	                                          final JsonObject message, final boolean isCreated, final long resourceId) {

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
											resourceName, notificationName);
								} else {
									log.error("Error when calling service getModeratorsIds. Unable to send timeline "
											+ eventType + " notification.");
								}
							}
						});

						resourceService.getUserNotification(resourceId, user, new Handler<Either<String, JsonArray>>() {
							@Override
							public void handle(Either<String, JsonArray> event) {
								if (event.isRight()) {
									Set<String> recipientSet = new HashSet<>();
									for(Object o : event.right().getValue()){
										if(!(o instanceof JsonObject)){
											continue;
										}
										JsonObject jo = (JsonObject) o;
										recipientSet.add(jo.getString("user_id"));
									}

									if(!recipientSet.isEmpty()) {
										sendNotification(request, user, bookingId, startDate, endDate, resourceName, notificationName, recipientSet);
									}
								} else {
									log.error("Error when calling service getUserNotification. Unable to send timeline "
											+ " process notification.");
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


	public void notifyPeriodicBookingCreatedOrUpdated(final ResourceService resourceService, final HttpServerRequest request, final UserInfos user,
	                                                  final JsonArray childBookings, final boolean isCreation, final long resourceId) {

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

		if (sendNotification && childBookings.get(0) != null) {
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
													resourceName, notificationName);
										} else {
											log.error("Error when calling service getModeratorsIds. Unable to send timeline "
													+ eventType + " notification.");
										}
									}
								});

						resourceService.getUserNotification(resourceId, user, new Handler<Either<String, JsonArray>>() {
							@Override
							public void handle(Either<String, JsonArray> event) {
								if (event.isRight()) {
									Set<String> recipientSet = new HashSet<>();
									for(Object o : event.right().getValue()){
										if(!(o instanceof JsonObject)){
											continue;
										}
										JsonObject jo = (JsonObject) o;
										recipientSet.add(jo.getString("user_id"));
									}

									if(!recipientSet.isEmpty()) {
										sendNotification(request, user, bookingId, startDate, endDate, resourceName, notificationName, recipientSet);
									}
								} else {
									log.error("Error when calling service getUserNotification. Unable to send timeline "
											+ " process notification.");
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
	                              final String notificationName) {

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
								sendNotification(request, user, bookingId, startDate, endDate, resourceName, notificationName, recipientSet);
							}
						}
					});
				}
			}
			if (remaining.get() < 1 && !recipientSet.isEmpty()) {
				sendNotification(request, user, bookingId, startDate, endDate, resourceName, notificationName, recipientSet);
			}
		}

	}

	private void sendNotification(final HttpServerRequest request, final UserInfos user,
	                              final String bookingId, final String startDate, final String endDate, final String resourceName,
	                              final String notificationName, final Set<String> recipientSet) {

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


	public void notifyBookingProcessed(final ResourceService resourceService, final HttpServerRequest request, final UserInfos user,
	                                   final JsonObject booking, final String resourceName, final long resourceId) {

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

		resourceService.getUserNotification(resourceId, user, new Handler<Either<String, JsonArray>>() {
			@Override
			public void handle(Either<String, JsonArray> event) {
				if (event.isRight()) {
					Set<String> recipientSet = new HashSet<>();
					for(Object o : event.right().getValue()){
						if(!(o instanceof JsonObject)){
							continue;
						}
						JsonObject jo = (JsonObject) o;
						recipientSet.add(jo.getString("user_id"));
					}

					if(!recipientSet.isEmpty()) {
						List<String> recipients = new ArrayList<>(recipientSet);
						if (!owner.equals(user.getUserId())) {
							recipients.add(owner);
						}
						JsonObject params = new JsonObject();
						params.putString("username", user.getUsername())
								.putString("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
								.putString("startdate", startDate)
								.putString("enddate", endDate)
								.putString("resourcename", resourceName)
								.putString("bookingUri", "/rbs#/booking/" + bookingId + "/" + formatStringForRoute(startDate));
						params.putString("resourceUri", params.getString("bookingUri"));

						notification.notifyTimeline(request, "rbs." + notificationName, user, recipients, bookingId, params);
					}
				} else {
					log.error("Error when calling service getUserNotification. Unable to send timeline "
							+ " process notification.");
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
			target = BookingUtils.parseDateFromDB(strDate);
		} catch (ParseException e) {
			log.error("Cannot parse startDate", e);
			return null;
		}
		return new SimpleDateFormat("yyyy-MM-dd").format(target);
	}

}
