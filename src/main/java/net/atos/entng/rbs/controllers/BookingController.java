package net.atos.entng.rbs.controllers;

import static net.atos.entng.rbs.BookingStatus.*;
import static net.atos.entng.rbs.BookingUtils.*;
import static net.atos.entng.rbs.Rbs.RBS_NAME;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;

public class BookingController extends ControllerHelper {

	private static final String BOOKING_CREATED_EVENT_TYPE = RBS_NAME + "_BOOKING_CREATED";
	private static final String BOOKING_UPDATED_EVENT_TYPE = RBS_NAME + "_BOOKING_UPDATED";
	private static final String BOOKING_DELETED_EVENT_TYPE = RBS_NAME + "_BOOKING_DELETED";
	private static final String BOOKING_VALIDATED_EVENT_TYPE = RBS_NAME + "_BOOKING_VALIDATED";
	private static final String BOOKING_REFUSED_EVENT_TYPE = RBS_NAME + "_BOOKING_REFUSED";
	private static final String PERIODIC_BOOKING_CREATED_EVENT_TYPE = RBS_NAME + "_PERIODIC_BOOKING_CREATED";
	private static final String PERIODIC_BOOKING_UPDATED_EVENT_TYPE = RBS_NAME + "_PERIODIC_BOOKING_UPDATED";
	private static final String PERIODIC_BOOKING_DELETED_EVENT_TYPE = RBS_NAME + "_PERIODIC_BOOKING_DELETED";

	private static final I18n i18n = I18n.getInstance();

	private final BookingService bookingService;
	private final ResourceService resourceService;

	public BookingController(){
		bookingService = new BookingServiceSqlImpl();
		resourceService = new ResourceServiceSqlImpl();
	}

	@Post("/resource/:id/booking")
	@ApiDoc("Create booking of a given resource")
	@SecuredAction(value = "rbs.contrib", type= ActionType.RESOURCE)
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

				if (!isValidDates(startDate, endDate, now)) {
					badRequest(request, "rbs.booking.bad.request.invalid.dates");
					return;
				}

				resourceService.getDelaysAndTypeProperties(Long.parseLong(resourceId), new Handler<Either<String, JsonObject>>() {
					@Override
					public void handle(Either<String, JsonObject> event) {
						if (event.isRight() && event.right().getValue()!=null) {

							JsonObject resource = event.right().getValue();
							String owner = resource.getString("owner", null);
							String schoolId = resource.getString("school_id", null);

							if(owner == null || schoolId == null) {
								log.warn("Could not get owner or school_id for type of resource "+resourceId);
							}

							if(!canBypassDelaysConstraints(owner, schoolId, user)) {
								// check that booking dates respect min and max delays
								if(isDelayLessThanMin(request, resource, startDate, now)) {
									long nbDays = TimeUnit.DAYS.convert(resource.getLong("min_delay"), TimeUnit.SECONDS);
									String errorMessage = i18n.translate(
											"rbs.booking.bad.request.minDelay.not.respected",
											I18n.acceptLanguage(request),
											Long.toString(nbDays));

									badRequest(request, errorMessage);
									return;
								}
								else if(isDelayGreaterThanMax(request, resource, endDate, now)) {
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
							}
							else {
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
	private boolean canBypassDelaysConstraints(String owner, String schoolId, UserInfos user) {
		if(user.getUserId().equals(owner)) {
			return true;
		}
		// TODO : type manager case

		List<String> scope = getLocalAdminScope(user);
		if (scope!=null && !scope.isEmpty() && scope.contains(schoolId)) {
			return true;
		}

		return false;
	}

	private boolean isDelayLessThanMin(HttpServerRequest request, JsonObject resource, long startDate, long now) {
		long minDelay = resource.getLong("min_delay", -1);
		long delay = startDate - now;

		return (minDelay > -1 && minDelay > delay);
	}

	private boolean isDelayGreaterThanMax(HttpServerRequest request, JsonObject resource, long endDate, long now) {
		long maxDelay = resource.getLong("max_delay", -1);
		if(maxDelay == -1) {
			return false;
		}

		// Authorize users to book a resource N days in advance, without taking hour/minute/seconds into account
		maxDelay = (getTomorrowTimestamp() - now) + maxDelay;
		long delay = endDate - now;

		return (delay > maxDelay);
	}


	/**
	 * Notify moderators that a booking has been created or updated
	 */
	private void notifyBookingCreatedOrUpdated(final HttpServerRequest request, final UserInfos user,
			final JsonObject message, final boolean isCreated){

		final long id = message.getLong("id", 0L);
		final int status = message.getInteger("status", 0);
		final String startDate = message.getString("start_date", null);
		final String endDate = message.getString("end_date", null);

		final String eventType;
		final String template;
		if (isCreated) {
			eventType = BOOKING_CREATED_EVENT_TYPE;
			template = "notify-booking-created.html";
		}
		else {
			eventType = BOOKING_UPDATED_EVENT_TYPE;
			template = "notify-booking-updated.html";
		}

		if (id == 0L || status == 0 || startDate == null || endDate == null) {
			log.error("Could not get bookingId, status, start_date or end_date from response. Unable to send timeline "+ eventType + " notification.");
			return;
		}
		final String bookingId = Long.toString(id);

		// Do NOT send a notification if the booking has been automatically validated
        if(CREATED.status() == status){

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
											resourceName, eventType, template);
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
	 @SecuredAction(value = "rbs.contrib", type= ActionType.RESOURCE)
	 @ResourceFilter(TypeAndResourceAppendPolicy.class)
	 public void createPeriodicBooking(final HttpServerRequest request) {
			UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
				@Override
				public void handle(final UserInfos user) {
					if (user != null) {
						RequestUtils.bodyToJson(request, pathPrefix + "createPeriodicBooking",
								getPeriodicBookingHandler(user, request, true));
					} else {
						log.debug("User not found in session.");
						unauthorized(request);
					}
				}
			});
	 }

	/**
	 * @return Handler to create or update a periodic booking
	 */
	private Handler<JsonObject> getPeriodicBookingHandler(final UserInfos user,
			final HttpServerRequest request, final boolean isCreation) {

		return new Handler<JsonObject>() {
			@Override
			public void handle(final JsonObject booking) {
				final String id = request.params().get("id");
				final String bookingId = request.params().get("bookingId");

				final int periodicity = booking.getInteger("periodicity");
				final long endDate = booking.getLong("periodic_end_date", 0L);
				final long now = getCurrentTimestamp();
				final int occurrences = booking.getInteger("occurrences", 0);
				if (endDate == 0L && occurrences == 0){
					badRequest(request, "rbs.booking.bad.request.enddate.or.occurrences");
					return;
				}

				final long firstSlotStartDate = booking.getLong("start_date", 0L);
				final long firstSlotEndDate = booking.getLong("end_date", 0L);
				if (!isValidDates(firstSlotStartDate, firstSlotEndDate, now)) {
					badRequest(request, "rbs.booking.bad.request.invalid.dates");
					return;
				}

				// The first slot must begin and end on the same day
				final int firstSlotDay = getDayFromTimestamp(firstSlotStartDate);
				if (firstSlotDay != getDayFromTimestamp(firstSlotEndDate)) {
					badRequest(request, "rbs.booking.bad.request.invalid.first.slot");
					return;
				}

				final JsonArray selectedDaysArray = booking.getArray("days", null);
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

				resourceService.getDelaysAndTypeProperties(Long.parseLong(id), new Handler<Either<String, JsonObject>>() {
					@Override
					public void handle(Either<String, JsonObject> event) {
						if (event.isRight() && event.right().getValue()!=null) {
							// Check that booking dates respect min and max delays
							JsonObject resource = event.right().getValue();

							if(isDelayLessThanMin(request, resource, firstSlotStartDate, now)) {
								long nbDays = TimeUnit.DAYS.convert(resource.getLong("min_delay"), TimeUnit.SECONDS);
								String errorMessage = i18n.translate(
										"rbs.booking.bad.request.minDelay.not.respected.by.firstSlot",
										I18n.acceptLanguage(request),
										Long.toString(nbDays));

								badRequest(request, errorMessage);
								return;
							}
							else {
								long lastSlotEndDate;
								if (endDate > 0L) { // Case when end_date is supplied
									try {
										int endDateDay = getDayFromTimestamp(endDate);
										Object endDateDayIsSelected = selectedDaysArray.toList().get(endDateDay);
										if((Boolean) endDateDayIsSelected) {
											lastSlotEndDate = endDate;
										}
										else {
											// If the endDateDay is not a selected day, compute the end date of the last slot
											long durationInDays = TimeUnit.DAYS.convert(endDate - firstSlotEndDate, TimeUnit.SECONDS);
											int nbOccurrences = getOccurrences(firstSlotDay, selectedDays, durationInDays, periodicity);
											lastSlotEndDate = getLastSlotDate(occurrences, periodicity, firstSlotEndDate, firstSlotDay, selectedDays);

											// Replace the end date with the last slot's end date
											booking.putNumber("periodic_end_date", lastSlotEndDate);
											// Put the computed value of occurrences
											booking.putNumber("occurrences", nbOccurrences);
										}
									} catch (Exception e) {
										log.error("Error when checking that the day of the end date is selected", e);
										renderError(request);
										return;
									}

								}
								else { // Case when occurrences is supplied
									lastSlotEndDate = getLastSlotDate(occurrences, periodicity, firstSlotEndDate, firstSlotDay, selectedDays);
								}

								if(isDelayGreaterThanMax(request, resource, lastSlotEndDate, now)) {
									long nbDays = TimeUnit.DAYS.convert(resource.getLong("max_delay"), TimeUnit.SECONDS);
									String errorMessage = i18n.translate(
											"rbs.booking.bad.request.maxDelay.not.respected.by.lastSlot",
											I18n.acceptLanguage(request),
											Long.toString(nbDays));

									badRequest(request, errorMessage);
									return;
								}
							}

							// Create or update booking
							if (isCreation) {
								try {
									bookingService.createPeriodicBooking(id, selectedDays,
											firstSlotDay, booking, user,
											getHandlerForPeriodicNotification(user, request, isCreation));
								} catch (Exception e) {
									log.error("Error during service createPeriodicBooking", e);
									renderError(request);
								}
							}
							else {
								try {
									bookingService.updatePeriodicBooking(id, bookingId, selectedDays,
											firstSlotDay, booking, user,
											getHandlerForPeriodicNotification(user, request, isCreation));
								} catch (Exception e) {
									log.error("Error during service updatePeriodicBooking", e);
									renderError(request);
								}
							}

						} else {
							badRequest(request, event.left().getValue());
						}
					}

				});

			}
		};
	}

	private boolean isValidDates(long startDate, long endDate, long now) {
		return (startDate > now && endDate > startDate);
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
				if(CREATED.status() == status) {
					sendNotification = true;
					break;
				}
			}
		} catch (Exception e) {
			log.error("Error in method notifyPeriodicBookingCreatedOrUpdated");
			return;
		}

		if(sendNotification && childBookings!=null && childBookings.get(0)!=null) {
			JsonObject firstBooking = (JsonObject) childBookings.get(0);
			final long id = firstBooking.getLong("id", 0L);

			final String eventType;
			final String template;
			if (isCreation) {
				eventType = PERIODIC_BOOKING_CREATED_EVENT_TYPE;
				template = "notify-periodic-booking-created.html";
			}
			else {
				eventType = PERIODIC_BOOKING_UPDATED_EVENT_TYPE;
				template = "notify-periodic-booking-updated.html";
			}

			if (id == 0L) {
				log.error("Could not get bookingId from response. Unable to send timeline "+ eventType + " notification.");
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
							log.error("Could not get bookingId, start_date, end_date or resource_name from response. Unable to send timeline "+
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
											resourceName, eventType, template);
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
			final String eventType, final String template) {

		final Set<String> recipientSet = new HashSet<>();
		final AtomicInteger remaining = new AtomicInteger(moderators.size());

		for(Object o : moderators){
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
								sendNotification(request, user, bookingId, startDate, endDate, resourceName, eventType, template, recipientSet);
							}
						}
					});
				}
			}
			if (remaining.get() < 1 && !recipientSet.isEmpty()) {
				sendNotification(request, user, bookingId, startDate, endDate, resourceName, eventType, template, recipientSet);
			}
		}

	}

	private void sendNotification(final HttpServerRequest request, final UserInfos user,
			final String bookingId, final String startDate, final String endDate, final String resourceName,
			final String eventType, final String template, final Set<String> recipientSet) {

		List<String> recipients = new ArrayList<>(recipientSet);

		JsonObject params = new JsonObject();
		params.putString("uri", container.config().getString("userbook-host") +
				"/userbook/annuaire#" + user.getUserId() + "#" + user.getType());
		params.putString("bookingUri", container.config().getString("host")
				+ "/rbs#/booking/" + bookingId)
			.putString("username", user.getUsername())
			.putString("startdate", startDate)
			.putString("enddate", endDate)
			.putString("resourcename", resourceName);

		notification.notifyTimeline(request, user, RBS_NAME, eventType,
				recipients, bookingId, template, params);
	}

	 private String booleanArrayToBitString(JsonArray selectedDaysArray) {
		 StringBuilder selectedDays = new StringBuilder();
			for (Object day : selectedDaysArray) {
				int isSelectedDay = ((Boolean)day) ? 1 : 0;
				selectedDays.append(isSelectedDay);
			}
		 return selectedDays.toString();
	 }

	 @Put("/resource/:id/booking/:bookingId")
	 @ApiDoc("Update booking")
	 @SecuredAction(value = "rbs.contrib", type= ActionType.RESOURCE)
	 @ResourceFilter(TypeAndResourceAppendPolicy.class)
	 public void updateBooking(final HttpServerRequest request){
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
	@SecuredAction(value = "rbs.contrib", type= ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	public void updatePeriodicBooking(final HttpServerRequest request){
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "updatePeriodicBooking",
							getPeriodicBookingHandler(user, request, false));
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	 @Put("/resource/:id/booking/:bookingId/process")
	 @ApiDoc("Validate or refuse booking")
	 @SecuredAction(value = "rbs.publish", type= ActionType.RESOURCE)
	 @ResourceFilter(TypeAndResourceAppendPolicy.class)
	 public void processBooking(final HttpServerRequest request){
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

													Handler<Either<String, JsonObject>> notifHandler = new Handler<Either<String,JsonObject>>() {
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
											}
											else {
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
			final JsonObject booking, final String resourceName){

		final long id = booking.getLong("id", 0L);
		final int status = booking.getInteger("status", 0);
		final String owner = booking.getString("owner", null);
		final String startDate = booking.getString("start_date", null);
		final String endDate = booking.getString("end_date", null);

		final String eventType;
		final String template;

		if (id == 0L || status == 0 || owner == null || owner.trim().length() == 0
				|| startDate == null || endDate == null) {
			log.error("Could not get bookingId, status, owner, start_date or end_date from response. Unable to send timeline "+
					BOOKING_VALIDATED_EVENT_TYPE + " or " + BOOKING_REFUSED_EVENT_TYPE + " notification.");
			return;
		}
		final String bookingId = Long.toString(id);

		if(VALIDATED.status() == status){
			eventType = BOOKING_VALIDATED_EVENT_TYPE;
			template = "notify-booking-validated.html";
		}
		else if(REFUSED.status() == status) {
			eventType = BOOKING_REFUSED_EVENT_TYPE;
			template = "notify-booking-refused.html";
		}
		else {
			log.error("Invalid status");
			return;
		}

		if(!owner.equals(user.getUserId())) {
			JsonObject params = new JsonObject();
			params.putString("username", user.getUsername())
				.putString("startdate", startDate)
				.putString("enddate", endDate)
				.putString("resourcename", resourceName)
				.putString("bookingUri", container.config().getString("host") + "/rbs#/booking/" + bookingId);

			List<String> recipients = new ArrayList<>();
			recipients.add(owner);

			notification.notifyTimeline(request, user, RBS_NAME, eventType,
					recipients, bookingId, template, params);
		}

	}

	 @Delete("/resource/:id/booking/:bookingId")
	 @ApiDoc("Delete booking")
	 @SecuredAction(value = "rbs.manager", type= ActionType.RESOURCE)
	 @ResourceFilter(TypeAndResourceAppendPolicy.class)
	 public void deleteBooking(final HttpServerRequest request){
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

									bookingService.delete(bookingId, user, new Handler<Either<String, JsonObject>>() {
										@Override
										public void handle(Either<String, JsonObject> event) {
											if (event.isRight()) {
												if (event.right().getValue() != null && event.right().getValue().size() > 0) {
													try {
														notifyBookingDeleted(request, user, booking, bookingId);
													} catch (Exception e) {
														log.error("Unable to send timeline "+ BOOKING_DELETED_EVENT_TYPE
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

		final String eventType;
		final String template;

		if (startDate == null || endDate == null ||
				owner == null || owner.trim().isEmpty() ||
				resourceName == null || resourceName.trim().isEmpty()) {
			log.error("Could not get start_date, end_date, owner or resource_name from response. Unable to send timeline "+
					BOOKING_DELETED_EVENT_TYPE + " or " + PERIODIC_BOOKING_DELETED_EVENT_TYPE + " notification.");
			return;
		}

		// Notify only if current user is not the booking's owner
		if(!owner.equals(user.getUserId())) {
			if(isPeriodic) {
				eventType = PERIODIC_BOOKING_DELETED_EVENT_TYPE;
				template = "notify-periodic-booking-deleted.html";
			}
			else {
				eventType = BOOKING_DELETED_EVENT_TYPE;
				template = "notify-booking-deleted.html";
			}

			JsonObject params = new JsonObject();
			params.putString("username", user.getUsername())
				.putString("startdate", startDate)
				.putString("enddate", endDate)
				.putString("resourcename", resourceName);

			List<String> recipients = new ArrayList<>();
			recipients.add(owner);

			notification.notifyTimeline(request, user, RBS_NAME, eventType,
					recipients, bookingId, template, params);

		}
	 }


	 /*
	  * TODO : limiter à un intervalle donné la liste des réservations retournées.
	  * Mettre en place un intervalle par défaut (la semaine ou le mois actuel par exemple)
	  * Permettre de préciser un intervalle
	  */

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
	 public void listUnprocessedBookings(final HttpServerRequest request){

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
					}
					else {
						log.debug("User not found in session.");
						unauthorized(request);
					}
				}
			});

	 }

}
