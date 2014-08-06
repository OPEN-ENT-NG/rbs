package fr.wseduc.rbs.controllers;

import static fr.wseduc.rbs.BookingStatus.*;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;

import java.util.ArrayList;
import java.util.List;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.service.VisibilityFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.rbs.filters.TypeAndResourceAppendPolicy;
import fr.wseduc.rbs.service.BookingService;
import fr.wseduc.rbs.service.BookingServiceSqlImpl;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.ResourceFilter;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;

public class BookingController extends ControllerHelper {

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
							Long resourceId = new Long(0);
							try {
								resourceId = Long.valueOf(id);
							} catch (NumberFormatException e) {
								log.error("Invalid resourceId", e);
								Renders.badRequest(request, "Invalid resourceId");
							}

							Handler<Either<String, JsonObject>> handler = new Handler<Either<String, JsonObject>>() {
								@Override
								public void handle(Either<String, JsonObject> event) {
									if (event.isRight()) {
										if (event.right().getValue() != null && event.right().getValue().size() > 0) {
											Renders.renderJson(request, event.right().getValue(), 200);
										} else {
											JsonObject error = new JsonObject()
												.putString("error", "A validated booking overlaps the booking you tried to create.");
											Renders.renderError(request, error);
										}
									} else {
										JsonObject error = new JsonObject()
												.putString("error", event.left().getValue());
										Renders.renderJson(request, error, 400);
									}
								}
							};
							
							// TODO : envoyer une notification aux valideurs
							bookingService.createBooking(resourceId, object, user, handler);
						}
					});
				} else {
					log.debug("User not found in session.");
					Renders.unauthorized(request);
				}
			}
		});
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
								String bookingId = request.params().get("bookingId");
								bookingService.updateBooking(bookingId, object, notEmptyResponseHandler(request));
							}
						});
					} else {
						log.debug("User not found in session.");
						Renders.unauthorized(request);
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
								String bookingId = request.params().get("bookingId");
								
								int newStatus = 0;
								try {
									newStatus = (int) object.getValue("status");									
								} catch (Exception e) {
									log.error(e.getMessage());
									Renders.renderError(request);
								}
								if (newStatus != VALIDATED.status() 
										&& newStatus != REFUSED.status()) {
									Renders.badRequest(request, "Invalid status");
								}
								
								object.putString("moderator_id", user.getUserId());
								// TODO : interdire la validation, s'il existe deja une demande validee
								
								// TODO : envoyer une notification au demandeur
								crudService.update(bookingId, object, user, notEmptyResponseHandler(request));
								
								// TODO : en cas de validation, mettre les demandes concurrentes à l'etat refuse
							}
						});
					} else {
						log.debug("User not found in session.");
						Renders.unauthorized(request);
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
						crudService.delete(bookingId, user, defaultResponseHandler(request, 204));
					} else {
						log.debug("User not found in session.");
						Renders.unauthorized(request);
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
					crudService.list(VisibilityFilter.OWNER, user, arrayResponseHandler(request));
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
						Renders.unauthorized(request);
					}
				}
			});
			
	 }
	
	// Pour afficher l'historique des reservations
	// @Get("/bookings/all")
	// @ApiDoc("List all bookings")
	// @SecuredAction(value = "rbs.manage", type = ActionType.RESOURCE)

}
