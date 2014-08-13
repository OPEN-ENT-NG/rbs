package fr.wseduc.rbs.controllers;

import static fr.wseduc.rbs.Rbs.RBS_NAME;
import static fr.wseduc.rbs.BookingStatus.*;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;
import static org.entcore.common.sql.Sql.parseId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

	private final BookingService bookingService;

	public BookingController(){
		bookingService = new BookingServiceSqlImpl();
	}

	// TODO : use i18n for messages

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
												.putString("error", "A validated booking overlaps the booking you tried to create.");
											renderError(request, error);
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

		Number id = message.getNumber("id");
		final String bookingId = id.toString();

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

		int status = message.getInteger("status");
		// Do NOT send a notification if the booking has been automatically validated
        if(CREATED.status() == status){

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
							.putString("username", user.getUsername());

						notification.notifyTimeline(request, user, RBS_NAME, eventType,
								recipients, bookingId, template, params);

					} else {
						log.error("Error when calling service getModeratorsIds. Unable to send timeline "
								+ eventType + " notification.");
					}
				}
    		});
        }

	}

	// @Post("/resource/:id/booking/periodic")
	// @ApiDoc("Create periodic booking of a given resource")
	// @SecuredAction(value = "rbs.contrib", type= ActionType.RESOURCE)
	// @ResourceFilter(TypeAndResourceAppendPolicy.class)
	// public void createPeriodicBooking() {
	//
	// }


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
													.putString("error",
															"No rows were updated. Either a validated booking overlaps the booking you tried to create, or the specified bookingId does not exist.");
												renderError(request, error);
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
								// TODO : envoyer une notification au demandeur
								bookingService.processBooking(parseId(resourceId),
										bookingId, newStatus, object, user, notEmptyResponseHandler(request));
							}
						});
					} else {
						log.debug("User not found in session.");
						unauthorized(request);
					}
				}
			});
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

						crudService.delete(bookingId, user, notEmptyResponseHandler(request, 204));
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
