package fr.wseduc.rbs.controllers;

import static fr.wseduc.rbs.BookingStatus.CREATED;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static org.entcore.common.sql.SqlResult.validUniqueResultHandler;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.rbs.BookingStatus;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;

public class BookingController extends ControllerHelper {

	@Post("/resource/:resourceId/booking")
	@ApiDoc("Create booking of a given resource")
	// @SecuredAction(value = "rbs.contrib", type= ActionType.RESOURCE)
	public void createBooking(final HttpServerRequest request) {

		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "createBooking", new Handler<JsonObject>() {
						@Override
						public void handle(JsonObject object) {
							final String resourceId = request.params().get("resourceId");

							object.putNumber("status", CREATED.status());
							SqlConf conf = SqlConfs.getConf(BookingController.class.getName());

							// Query - unix timestamps are converted into postgresql timestamps
							StringBuilder query = new StringBuilder();
							query.append("INSERT INTO ")
									.append(conf.getSchema())
									.append(conf.getTable())
									.append("(resource_id, owner, booking_reason, status, start_date, end_date)")
									.append(" VALUES ( ?, ?, ?, ?, to_timestamp(?), to_timestamp(?));");

							JsonArray values = new JsonArray();
							values.add(resourceId)
									.add(object.getValue("owner"))
									.add(object.getValue("booking_reason"))
									.add(object.getValue("status"))
									.add(object.getValue("start_date"))
									.add(object.getValue("end_date"));

							// Execute
							Sql.getInstance()
									.prepared(
											query.toString(),
											values,
											validUniqueResultHandler(defaultResponseHandler(request)));
						}
					});
				} else {
					log.debug("User not found in session.");
					Renders.unauthorized(request);
				}
			}
		});
	}

	// @Post("/resource/:resourceId/booking/periodic")
	// @ApiDoc("Create periodic booking of a given resource")
	// @SecuredAction(value = "rbs.contrib", type= ActionType.RESOURCE)
	// public void createPeriodicBooking() {
	//
	// }

	 @Put("/booking/:id")
	 @ApiDoc("Update booking")
	 // @SecuredAction(value = "rbs.contrib", type= ActionType.RESOURCE)
	 public void updateBooking(final HttpServerRequest request){
			UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
				@Override
				public void handle(final UserInfos user) {
					if (user != null) {
						RequestUtils.bodyToJson(request, pathPrefix + "updateBooking", new Handler<JsonObject>() {
							@Override
							public void handle(JsonObject object) {
								String id = request.params().get("id");
								crudService.update(id, object, user, defaultResponseHandler(request));
							}
						});
					} else {
						log.debug("User not found in session.");
						Renders.unauthorized(request);
					}
				}
			});
	 }
	 
	 @Put("/booking/:id/process")
	 @ApiDoc("Validate or refuse booking")
	// @SecuredAction(value = "rbs.publish", type= ActionType.RESOURCE)
	 public void processBooking(final HttpServerRequest request){
			UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
				@Override
				public void handle(final UserInfos user) {
					if (user != null) {
						RequestUtils.bodyToJson(request, pathPrefix + "processBooking", new Handler<JsonObject>() {
							@Override
							public void handle(JsonObject object) {
								String id = request.params().get("id");
								
								int newStatus = 0;
								try {
									newStatus = (int) object.getValue("status");									
								} catch (Exception e) {
									log.error(e.getMessage());
									Renders.renderError(request);
								}
								
								if (newStatus != BookingStatus.VALIDATED.status() 
										&& newStatus != BookingStatus.REFUSED.status()) {
									Renders.badRequest(request, "Invalid status");
								}
								
								crudService.update(id, object, user, defaultResponseHandler(request));
							}
						});
					} else {
						log.debug("User not found in session.");
						Renders.unauthorized(request);
					}
				}
			});		 
	 }

	 @Delete("/booking/:id")
	 @ApiDoc("Delete booking")
	 // @SecuredAction(value = "rbs.manager", type= ActionType.RESOURCE)
	 public void deleteBooking(final HttpServerRequest request){
			UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
				@Override
				public void handle(final UserInfos user) {
					if (user != null) {
						String id = request.params().get("id");
						crudService.delete(id, user, defaultResponseHandler(request, 204));
					} else {
						log.debug("User not found in session.");
						Renders.unauthorized(request);
					}
				}
			});
	 }

	 
	// @Get("/bookings")
	// @ApiDoc("List all bookings created by current user")
	// @SecuredAction("resa.booking.list")
	//
	// @Get("/bookings/unprocessed")
	// @ApiDoc("List all bookings waiting to be processed by current user")
	// @SecuredAction(value = "rbs.publish", type = ActionType.RESOURCE)
	//
	// @Get("/bookings/all")
	// @ApiDoc("List all bookings")
	// @SecuredAction(value = "rbs.manage", type = ActionType.RESOURCE)

}
