package fr.wseduc.rbs.controllers;

import static fr.wseduc.rbs.Rbs.RBS_NAME;
import static fr.wseduc.rbs.BookingStatus.*;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.rbs.filters.TypeAndResourceAppendPolicy;
import fr.wseduc.rbs.service.BookingService;
import fr.wseduc.rbs.service.BookingServiceSqlImpl;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;

public class BookingController extends ControllerHelper {

	private static final String BOOKING_CREATED_EVENT_TYPE = RBS_NAME + "_BOOKING_CREATED";
	private static final String BOOKING_UPDATED_EVENT_TYPE = RBS_NAME + "_BOOKING_UPDATED";
	private static final String BOOKING_VALIDATED_EVENT_TYPE = RBS_NAME + "_BOOKING_VALIDATED";
	private static final String BOOKING_REFUSED_EVENT_TYPE = RBS_NAME + "_BOOKING_REFUSED";
	private static final String PERIODIC_BOOKING_CREATED_EVENT_TYPE = RBS_NAME + "_PERIODIC_BOOKING_CREATED";
	private static final String PERIODIC_BOOKING_UPDATED_EVENT_TYPE = RBS_NAME + "_PEROIDIC_BOOKING_UPDATED";


	private final BookingService bookingService;

	public BookingController(){
		bookingService = new BookingServiceSqlImpl();
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
					RequestUtils.bodyToJson(request, pathPrefix + "createBooking", new Handler<JsonObject>() {
						@Override
						public void handle(JsonObject object) {
							final String id = request.params().get("id");

							long startDate = object.getLong("start_date", 0L);
							long endDate = object.getLong("end_date", 0L);
							if (!isValidDates(startDate, endDate)) {
								badRequest(request, "rbs.booking.bad.request.invalid.dates");
								return;
							}

							Handler<Either<String, JsonObject>> handler = new Handler<Either<String, JsonObject>>() {
								@Override
								public void handle(Either<String, JsonObject> event) {
									if (event.isRight()) {
										if (event.right().getValue() != null && event.right().getValue().size() > 0) {
											notifyBookingCreatedOrUpdated(request, user, event.right().getValue(), true);
											renderJson(request, event.right().getValue(), 200);
										} else {
											JsonObject error = new JsonObject()
												.putString("error", "rbs.booking.create.conflict");
											renderJson(request, error, 409);
										}
									} else {
										JsonObject error = new JsonObject()
												.putString("error", event.left().getValue());
										renderJson(request, error, 400);
									}
								}
							};

							bookingService.createBooking(id, object, user, handler);
						}
					});
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
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
									List<String> recipients = getModeratorsList(event.right().getValue());

									if(!recipients.isEmpty()) {
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

	private Handler<JsonObject> getPeriodicBookingHandler(final UserInfos user,
			final HttpServerRequest request, final boolean isCreation) {

		return new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject object) {
				final String id = request.params().get("id");
				final String bookingId = request.params().get("bookingId");

				long endDate = object.getLong("periodic_end_date", 0L);
				int occurrences = object.getInteger("occurrences", 0);
				if (endDate == 0L && occurrences == 0){
					badRequest(request, "rbs.booking.bad.request.enddate.or.occurrences");
					return;
				}

				long firstSlotStartDate = object.getLong("start_date", 0L);
				long firstSlotEndDate = object.getLong("end_date", 0L);
				if (!isValidDates(firstSlotStartDate, firstSlotEndDate)) {
					badRequest(request, "rbs.booking.bad.request.invalid.dates");
					return;
				}

				// The first slot must begin and end on the same day
				final int firstSlotStartDay = getDayFromTimestamp(firstSlotStartDate);
				if (firstSlotStartDay != getDayFromTimestamp(firstSlotEndDate)) {
					badRequest(request, "rbs.booking.bad.request.invalid.first.slot");
					return;
				}

				JsonArray selectedDaysArray = object.getArray("days", null);
				if (selectedDaysArray == null || selectedDaysArray.size() != 7) {
					badRequest(request, "rbs.booking.bad.request.invalid.days");
					return;
				}
				try {
					Object firstSlotDayIsSelected = selectedDaysArray.toList().get(firstSlotStartDay);
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
				String selectedDays;
				try {
					selectedDays = booleanArrayToBitString(selectedDaysArray);
				} catch (Exception e) {
					log.error("Error during processing of array 'days'", e);
					renderError(request);
					return;
				}

				if (isCreation) {
					try {
						bookingService.createPeriodicBooking(id, selectedDays,
								firstSlotStartDay, object, user,
								getHandlerForPeriodicNotification(user, request, isCreation));

					} catch (Exception e) {
						log.error("Error during service createPeriodicBooking", e);
						renderError(request);
					}
				}
				else {
					try {
						bookingService.updatePeriodicBooking(id, bookingId, selectedDays,
								firstSlotStartDay, object, user,
								getHandlerForPeriodicNotification(user, request, isCreation));

					} catch (Exception e) {
						log.error("Error during service updatePeriodicBooking", e);
						renderError(request);
					}
				}

			}
		};
	}

	private boolean isValidDates(long startDate, long endDate) {
		long now = Calendar.getInstance().getTimeInMillis();
		now = TimeUnit.SECONDS.convert(now, TimeUnit.MILLISECONDS);

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
					JsonObject error = new JsonObject()
							.putString("error", event.left().getValue());
					Renders.renderJson(request, error, 400);
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
									List<String> recipients = getModeratorsList(event.right().getValue());

									if(!recipients.isEmpty()) {
										JsonObject params = new JsonObject();
										params.putString("uri", container.config().getString("userbook-host") +
												"/userbook/annuaire#" + user.getUserId() + "#" + user.getType());
										params.putString("bookingUri", container.config().getString("host")
						                        + "/rbs#/booking/" + periodicBookingId)
											.putString("username", user.getUsername())
											.putString("startdate", startDate)
											.putString("enddate", endDate)
											.putString("resourcename", resourceName);

										notification.notifyTimeline(request, user, RBS_NAME, eventType,
												recipients, periodicBookingId, template, params);
									}

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

	private List<String> getModeratorsList(JsonArray moderators) {
		Set<String> recipientSet = new HashSet<>();
		for(Object o : moderators){
			if(!(o instanceof JsonObject)){
				continue;
			}
			JsonObject jo = (JsonObject) o;
			recipientSet.add(jo.getString("member_id"));
		}

		return new ArrayList<String>(recipientSet);
	}

	 /**
	  *
	  * @param unixTimestamp in seconds
	  * @return
	  */
	 private int getDayFromTimestamp(final long unixTimestamp) {
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(
					TimeUnit.MILLISECONDS.convert(unixTimestamp, TimeUnit.SECONDS));
			// "- 1", so that sunday is 0, monday is 1, etc
			int day = cal.get(Calendar.DAY_OF_WEEK) - 1;

			return day;
	 }

	 private String booleanArrayToBitString(JsonArray selectedDaysArray) {
		 StringBuilder selectedDays = new StringBuilder();
			for (Object day : selectedDaysArray) {
				int isSelectedDay = ((Boolean)day) ? 1 : 0;
				selectedDays.append(isSelectedDay);
			}
		 return selectedDays.toString();
	 }

	 private boolean haveSameTime(final long thisTimestamp, final long thatTimestamp) {
		TimeZone gmt = TimeZone.getTimeZone("GMT");

		Calendar thisCal = Calendar.getInstance(gmt);
		thisCal.setTimeInMillis(
				TimeUnit.MILLISECONDS.convert(thisTimestamp, TimeUnit.SECONDS));

		Calendar thatCal = Calendar.getInstance(gmt);
		thatCal.setTimeInMillis(
				TimeUnit.MILLISECONDS.convert(thatTimestamp, TimeUnit.SECONDS));

		 return (thisCal.get(Calendar.HOUR_OF_DAY) == thatCal.get(Calendar.HOUR_OF_DAY)
				 && thisCal.get(Calendar.MINUTE) == thatCal.get(Calendar.MINUTE)
				 && thisCal.get(Calendar.SECOND) == thatCal.get(Calendar.SECOND));
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
						RequestUtils.bodyToJson(request, pathPrefix + "updateBooking", new Handler<JsonObject>() {
							@Override
							public void handle(JsonObject object) {
								String resourceId = request.params().get("id");
								String bookingId = request.params().get("bookingId");

								long startDate = object.getLong("start_date", 0L);
								long endDate = object.getLong("end_date", 0L);
								if (!isValidDates(startDate, endDate)) {
									badRequest(request, "rbs.booking.bad.request.invalid.dates");
									return;
								}

								Handler<Either<String, JsonObject>> handler = new Handler<Either<String, JsonObject>>() {
									@Override
									public void handle(Either<String, JsonObject> event) {
										if (event.isRight()) {
											if (event.right().getValue() != null && event.right().getValue().size() > 0) {
												notifyBookingCreatedOrUpdated(request, user, event.right().getValue(), false);
												renderJson(request, event.right().getValue(), 200);
											} else {
												JsonObject error = new JsonObject()
													.putString("error", "rbs.booking.update.conflict");
												renderJson(request, error, 409);
											}
										} else {
											JsonObject error = new JsonObject()
													.putString("error", event.left().getValue());
											renderJson(request, error, 400);
										}
									}
								};

								bookingService.updateBooking(resourceId, bookingId, object, handler);
							}
						});
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
											JsonObject error = new JsonObject()
													.putString("error", event.left().getValue());
											renderJson(request, error, 400);
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

	 @Delete("/resource/:id/booking/:bookingId")
	 @ApiDoc("Delete booking")
	 @SecuredAction(value = "rbs.manager", type= ActionType.RESOURCE)
	 @ResourceFilter(TypeAndResourceAppendPolicy.class)
	 public void deleteBooking(final HttpServerRequest request){
			UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
				@Override
				public void handle(final UserInfos user) {
					if (user != null) {
						String bookingId = request.params().get("bookingId");
						bookingService.delete(bookingId, user, notEmptyResponseHandler(request, 204));
					} else {
						log.debug("User not found in session.");
						unauthorized(request);
					}
				}
			});
	 }


	 /*
	  * TODO : limiter à un créneau horaire donné la liste des réservations retournées.
	  * Créneau horaire par défaut : la semaine ou le mois actuel
	  * Permettre de préciser un créneau
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
						if (user.getProfilGroupsIds() != null) {
							groupsAndUserIds.addAll(user.getProfilGroupsIds());
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

	// Pour afficher l'historique des reservations
	// @Get("/bookings/all")
	// @ApiDoc("List all bookings")
	// @SecuredAction(value = "rbs.manage", type = ActionType.RESOURCE)

}
