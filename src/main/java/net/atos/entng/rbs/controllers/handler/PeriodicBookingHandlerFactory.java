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
package net.atos.entng.rbs.controllers.handler;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.Renders;
import net.atos.entng.rbs.service.BookingNotificationService;
import net.atos.entng.rbs.service.BookingService;
import net.atos.entng.rbs.service.ResourceService;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import java.text.ParseException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static fr.wseduc.webutils.http.Renders.badRequest;
import static fr.wseduc.webutils.http.Renders.renderError;
import static net.atos.entng.rbs.BookingUtils.*;

public class PeriodicBookingHandlerFactory {

	private static final I18n i18n = I18n.getInstance();

	private final Logger log;
	private final BookingNotificationService bookingNotificationService;
	private final BookingService bookingService;
	private final ResourceService resourceService;

	public PeriodicBookingHandlerFactory(Logger log, BookingNotificationService bookingNotificationService, BookingService bookingService, ResourceService resourceService) {

		this.log = log;
		this.bookingNotificationService = bookingNotificationService;
		this.bookingService = bookingService;
		this.resourceService = resourceService;
	}

	public Handler<JsonObject> buildCreationBookingHandler(UserInfos user, HttpServerRequest request) {
		return new CreatePeriodicBookingHandler(user, request);
	}

	public Handler<JsonObject> buildUpdateBookingHandler(UserInfos user, HttpServerRequest request) {
		return new UpdatePeriodicBookingHandler(user, request);
	}

	public class CreatePeriodicBookingHandler implements Handler<JsonObject> {

		private final UserInfos user;
		private final HttpServerRequest request;

		public CreatePeriodicBookingHandler(final UserInfos user,
		                                    final HttpServerRequest request) {
			this.user = user;
			this.request = request;
		}

		@Override
		public void handle(final JsonObject booking) {
			final String resourceId = request.params().get("id");

			final long endDate = booking.getLong("periodic_end_date", 0L);
			final int occurrences = booking.getInteger("occurrences", 0);
			if (endDate == 0L && occurrences == 0) {
				badRequest(request, "rbs.booking.bad.request.enddate.or.occurrences");
				return;
			}
			JsonArray selectedDaysArray = null;
			int firstSlotDayTmp = 0;
			final JsonArray slots = booking.getArray("slots");
			for (int i = 0; i < slots.size(); i++) {
				JsonObject slot = slots.get(i);
				long firstSlotStartDate = slot.getLong("start_date", 0L);
				long firstSlotEndDate = slot.getLong("end_date", 0L);

				// cannot change the past
				if (firstSlotStartDate < getCurrentTimestamp()) {
					badRequest(request, "rbs.booking.bad.request.startdate.in.past.periodic.update");
					return;
				}

				firstSlotDayTmp = getDayFromTimestamp(firstSlotStartDate);
				boolean isMultiDayPeriod = getDayFromTimestamp(firstSlotEndDate) - firstSlotDayTmp >= 1;
				JsonArray days = booking.getArray("days", new JsonArray());
				selectedDaysArray = getSelectedDaysArray(days, firstSlotDayTmp, isMultiDayPeriod);

				if (selectedDaysArray.size() != 7) {
					badRequest(request, "rbs.booking.bad.request.invalid.days");
					return;
				}

				// The day of the first slot must be a selected day
				if (!(Boolean) selectedDaysArray.toList().get(firstSlotDayTmp)) {
					badRequest(request, "rbs.booking.bad.request.first.day.not.selected");
					return;
				}
			}
			// Store boolean array (selected days) as a bit string
			final String selectedDays = booleanArrayToBitString(selectedDaysArray);
			final int firstSlotDay = firstSlotDayTmp;
			resourceService.getDelaysAndTypeProperties(Long.parseLong(resourceId), new Handler<Either<String, JsonObject>>() {
				@Override
				public void handle(Either<String, JsonObject> event) {
					if (event.isRight() && event.right().getValue() != null) {
						JsonObject resourceDelayAndType = event.right().getValue();
						for (int i = 0; i < slots.size(); i++) {
							JsonObject slot = slots.get(i);
							final long firstSlotStartDate = slot.getLong("start_date", 0L);
							final long firstSlotEndDate = slot.getLong("end_date", 0L);
							Either<Boolean, String> checkResult = checkResourceConstraintsRespectedByPeriodicBooking(request, user, resourceId, resourceDelayAndType, booking, firstSlotStartDate, firstSlotEndDate);
							if (checkResult.isRight() && checkResult.right().getValue() != null) {
								badRequest(request, checkResult.right().getValue());
								return;
							} else if (checkResult.isLeft() && !checkResult.left().getValue()) {
								renderError(request);
								return;
							}
						}
						// Create or update booking
						try {
							bookingService.createPeriodicBooking(resourceId, selectedDays,
									firstSlotDay, booking, user,
									getHandlerForPeriodicNotification(user, request, true, resourceId));
						} catch (Exception e) {
							log.error("Error during service createPeriodicBooking", e);
							renderError(request);
						}

					} else {
						badRequest(request, event.left().getValue());
					}
				}

			});

		}

	}

	private JsonArray getSelectedDaysArray(JsonArray days, int firstSlotDay, boolean isMultiDayPeriod) {
		final JsonArray selectedDaysArray = new JsonArray();
		int i = 0;
		for (Object obj : days) {
			if (isMultiDayPeriod && i == firstSlotDay) {
				selectedDaysArray.addBoolean(new Boolean(true));
			} else {
				selectedDaysArray.addBoolean((Boolean) obj);
			}
			i++;
		}
		return selectedDaysArray;
	}


	private Either<Boolean, String> checkResourceConstraintsRespectedByPeriodicBooking(HttpServerRequest request, UserInfos user, String resourceId, JsonObject resourceDelayAndType,
	                                                                                   JsonObject booking, long firstSlotStartDate, long firstSlotEndDate) {

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

	private String booleanArrayToBitString(JsonArray selectedDaysArray) {
		StringBuilder selectedDays = new StringBuilder();
		for (Object day : selectedDaysArray) {
			int isSelectedDay = ((Boolean) day) ? 1 : 0;
			selectedDays.append(isSelectedDay);
		}
		return selectedDays.toString();
	}


	public class UpdatePeriodicBookingHandler implements Handler<JsonObject> {

		private final UserInfos user;
		private final HttpServerRequest request;

		public UpdatePeriodicBookingHandler(final UserInfos user,
		                                    final HttpServerRequest request) {
			this.user = user;
			this.request = request;
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

			final JsonArray slots = booking.getArray("slots");
			final JsonObject slot = slots.get(0);
			final long firstSlotStartDate = slot.getLong("start_date", 0L);
			final long firstSlotEndDate = slot.getLong("end_date", 0L);

			// cannot change the past
			if (firstSlotStartDate < getCurrentTimestamp()) {
				badRequest(request, "rbs.booking.bad.request.startdate.in.past.periodic.update");
				return;
			}

			// The first and last slot must end at the same hour
			if (endDate > 0L && !haveSameTime(endDate, firstSlotEndDate)) {
				badRequest(request, "rbs.booking.bad.request.invalid.enddates");
				return;
			}

			// The first slot must begin and end on the same day
			final int firstSlotDay = getDayFromTimestamp(firstSlotStartDate);
			boolean isMultiDayPeriod = getDayFromTimestamp(firstSlotEndDate) - getDayFromTimestamp(firstSlotStartDate) >= 1;
			JsonArray daysSelectedByUser = booking.getArray("days", new JsonArray());
			final JsonArray selectedDaysArray = getSelectedDaysArray(daysSelectedByUser, firstSlotDay, isMultiDayPeriod);

			if (selectedDaysArray.size() != 7) {
				badRequest(request, "rbs.booking.bad.request.invalid.days");
				return;
			}
			// The day of the first slot must be a selected day
			if (!(Boolean) selectedDaysArray.toList().get(firstSlotDay)) {
				badRequest(request, "rbs.booking.bad.request.first.day.not.selected");
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
						final long firstSlotStartDate = slot.getLong("start_date", 0L);
						final long firstSlotEndDate = slot.getLong("end_date", 0L);
						Either<Boolean, String> checkResult = checkResourceConstraintsRespectedByPeriodicBooking(request, user, resourceId, resourceDelayAndType, booking, firstSlotStartDate, firstSlotEndDate);
						if (checkResult.isRight() && checkResult.right().getValue() != null) {
							badRequest(request, checkResult.right().getValue());
							return;
						} else if (checkResult.isLeft() && !checkResult.left().getValue()) {
							renderError(request);
							return;
						}
						// get booking to update it
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
														if (event.isRight()) {
															bookingNotificationService.notifyPeriodicBookingCreatedOrUpdated(resourceService, request, user, event.right().getValue(), false, Long.parseLong(resourceId));
															bookingService.createPeriodicBooking(resourceId, selectedDays,
																	firstSlotDay, booking, user,
																	getHandlerForPeriodicNotification(user, request, true, resourceId));
														} else {
															badRequest(request, event.left().getValue());
														}
													} else {
														badRequest(request, event.left().getValue());
													}
												}
											});
										} else {
											bookingService.updatePeriodicBooking(resourceId, bookingId, selectedDays,
													firstSlotDay, booking, user,
													getHandlerForPeriodicNotification(user, request, false, resourceId));
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
					} else {
						badRequest(request, event.left().getValue());
					}
				}

			});

		}

	}


	private Handler<Either<String, JsonArray>> getHandlerForPeriodicNotification(final UserInfos user,
	                                                                             final HttpServerRequest request, final boolean isCreation, final String resourceId) {
		return new Handler<Either<String, JsonArray>>() {
			@Override
			public void handle(Either<String, JsonArray> event) {
				if (event.isRight()) {
					bookingNotificationService.notifyPeriodicBookingCreatedOrUpdated(resourceService, request, user, event.right().getValue(), isCreation, Long.parseLong(resourceId));
					Renders.renderJson(request, event.right().getValue());
				} else {
					badRequest(request, event.left().getValue());
				}
			}
		};
	}
}
