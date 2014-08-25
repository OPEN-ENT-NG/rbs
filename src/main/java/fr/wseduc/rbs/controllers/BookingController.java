package fr.wseduc.rbs.controllers;

import static fr.wseduc.rbs.Rbs.RBS_NAME;
import static fr.wseduc.rbs.BookingStatus.*;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;
import static org.entcore.common.sql.Sql.parseId;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.entcore.common.controller.ControllerHelper;
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
import fr.wseduc.security.ResourceFilter;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.request.RequestUtils;

public class BookingController extends ControllerHelper {

	private static final String BOOKING_CREATED_EVENT_TYPE = RBS_NAME + "_BOOKING_CREATED";
	private static final String BOOKING_UPDATED_EVENT_TYPE = RBS_NAME + "_BOOKING_UPDATED";
	private static final String BOOKING_VALIDATED_EVENT_TYPE = RBS_NAME + "_BOOKING_VALIDATED";
	private static final String BOOKING_REFUSED_EVENT_TYPE = RBS_NAME + "_BOOKING_REFUSED";

	private final BookingService bookingService;

	public BookingController(){
		bookingService = new BookingServiceSqlImpl();
	}

	/*
	 * TODO : contenu des notifications : ajouter un lien vers la demande de réservation ou le calendrier correspondant
	 */

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

							bookingService.createBooking(parseId(id), object, user, handler);
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

        	bookingService.getResourceName(bookingId, user, new Handler<Either<String, JsonObject>>() {
				@Override
				public void handle(Either<String, JsonObject> event) {
					if (event.isRight() && event.right().getValue() != null
							&& event.right().getValue().size() > 0) {

						final String resourceName = event.right().getValue().getString("resource_name");

						bookingService.getModeratorsIds(bookingId, user, new Handler<Either<String, JsonArray>>() {
							@Override
							public void handle(Either<String, JsonArray> event) {
								if (event.isRight()) {
									Set<String> recipientSet = new HashSet<>();
									for(Object o : event.right().getValue()){
										if(!(o instanceof JsonObject)){
											continue;
										}
										JsonObject jo = (JsonObject) o;
										recipientSet.add(jo.getString("member_id"));
									}
									List<String> recipients = new ArrayList<>(recipientSet);

									JsonObject params = new JsonObject();
									params.putString("uri", container.config().getString("userbook-host") +
											"/userbook/annuaire#" + user.getUserId() + "#" + user.getType());
									params.putString("id", bookingId)
										.putString("username", user.getUsername())
										.putString("startdate", startDate)
										.putString("enddate", endDate)
										.putString("resourcename", resourceName);

									notification.notifyTimeline(request, user, RBS_NAME, eventType,
											recipients, bookingId, template, params);
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
		// TODO : ajouter des messages d'erreur pour les bad request

		return new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject object) {
				final String id = request.params().get("id");
				final String bookingId = request.params().get("bookingId");

				long endDate = object.getLong("periodic_end_date", 0L);
				int occurrences = object.getInteger("occurrences", 0);
				if (endDate == 0L && occurrences == 0){
					badRequest(request);
					return;
				}

				long firstSlotStartDate = object.getLong("start_date", 0L);
				long firstSlotEndDate = object.getLong("end_date", 0L);
				if (firstSlotStartDate == 0L || firstSlotEndDate == 0L) {
					badRequest(request);
					return;
				}

				// The first slot must begin and end on the same day
				final int firstSlotStartDay = getDayFromTimestamp(firstSlotStartDate);
				if (firstSlotStartDay != getDayFromTimestamp(firstSlotEndDate)) {
					badRequest(request);
					return;
				}

				JsonArray selectedDaysArray = object.getArray("days", null);
				if (selectedDaysArray == null || selectedDaysArray.size() != 7) {
					badRequest(request);
					return;
				}
				try {
					Object firstSlotDayIsSelected = selectedDaysArray.toList().get(firstSlotStartDay);
					// The day of the first slot must be a selected day
					if(!(Boolean) firstSlotDayIsSelected) {
						badRequest(request);
						return;
					}
				} catch (Exception e) {
					log.error("Error when checking that the day of the first slot is selected", e);
					renderError(request);
					return;
				}

				// The first and last slot must end at the same hour
				if (endDate > 0L && !haveSameTime(endDate, firstSlotEndDate)) {
						badRequest(request);
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
						bookingService.createPeriodicBooking(parseId(id), selectedDays,
								firstSlotStartDay, object, user, arrayResponseHandler(request));
						// TODO : notifier les valideurs
					} catch (Exception e) {
						log.error("Error during service createPeriodicBooking", e);
						renderError(request);
					}
				}
				else {
					try {
						bookingService.updatePeriodicBooking(parseId(id), parseId(bookingId), selectedDays,
								firstSlotStartDay, object, user, arrayResponseHandler(request));
						// TODO notifier les valideurs
					} catch (Exception e) {
						log.error("Error during service updatePeriodicBooking", e);
						renderError(request);
					}
				}

			}
		};
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

		Calendar thisCal = Calendar.getInstance();
		thisCal.setTimeInMillis(
				TimeUnit.MILLISECONDS.convert(thisTimestamp, TimeUnit.SECONDS));

		Calendar thatCal = Calendar.getInstance();
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
								String sBookingId = request.params().get("bookingId");
								Object bookingId = parseId(sBookingId);
								if (!(bookingId instanceof Integer)) {
									badRequest(request);
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

								bookingService.updateBooking(parseId(resourceId),
										bookingId, object, handler);
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
								String sBookingId = request.params().get("bookingId");

								Object bookingId = parseId(sBookingId);
								if (!(bookingId instanceof Integer)) {
									badRequest(request);
									return;
								}

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
												JsonArray results = event.right().getValue();

												try {
													JsonObject processedBooking = ((JsonArray) results.get(2)).get(0);
													notifyBookingProcessed(request, user, processedBooking);

													if (results.size() >= 4) {
														JsonArray concurrentBookings = results.get(3);
														for (Object o : concurrentBookings) {
															JsonObject booking = (JsonObject) o;
															notifyBookingProcessed(request, user, booking);
														}
													}

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

								bookingService.processBooking(parseId(resourceId),
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
			final JsonObject booking){

		final long id = booking.getLong("id", 0L);
		final int status = booking.getInteger("status", 0);
		final String owner = booking.getString("owner", null);

		final String eventType;
		final String template;

		if (id == 0L || status == 0 || owner == null || owner.trim().length() == 0) {
			log.error("Could not get bookingId or status or owner from response. Unable to send timeline "+
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
		params.putString("id", bookingId)
			.putString("username", user.getUsername());

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
						if (!(parseId(bookingId) instanceof Integer)) {
							badRequest(request);
							return;
						}

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
