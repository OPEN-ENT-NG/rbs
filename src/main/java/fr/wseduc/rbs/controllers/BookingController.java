package fr.wseduc.rbs.controllers;

import static fr.wseduc.rbs.BookingStatus.*;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static org.entcore.common.sql.SqlResult.validResultHandler;
import static org.entcore.common.sql.SqlResult.validUniqueResultHandler;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.service.VisibilityFilter;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.rbs.filters.TypeAndResourceAppendPolicy;
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
							long resourceId = 0;
							try {
								resourceId = Long.parseLong(id);
							} catch (NumberFormatException e) {
								log.error("Invalid resourceId", e);
								Renders.badRequest(request, "Invalid resourceId");
							}

							object.putNumber("status", CREATED.status());
							SqlConf conf = SqlConfs.getConf(BookingController.class.getName());

							/* Query :
							 * Unix timestamps are converted into postgresql timestamps.
							 * Check that there does not exist a validated booking that overlaps the new booking.
							 */
							StringBuilder query = new StringBuilder();
							query.append("INSERT INTO ")
									.append(conf.getSchema())
									.append(conf.getTable())
									.append("(resource_id, owner, booking_reason, status, start_date, end_date)")
									.append(" SELECT  ?, ?, ?, ?, to_timestamp(?), to_timestamp(?)");
							query.append(" WHERE NOT EXISTS (")
									.append("SELECT id FROM ")
									.append(conf.getSchema())
									.append(conf.getTable())
									.append(" WHERE resource_id = ?")
									.append(" AND status = " + VALIDATED.status())
									.append(" AND (")
									.append("( start_date >= to_timestamp(?) AND start_date < to_timestamp(?) )")
									.append(" OR ( end_date > to_timestamp(?) AND end_date <= to_timestamp(?) )")
									.append(")) RETURNING id;");

							Object startDate = object.getValue("start_date");
							Object endDate = object.getValue("end_date");
							
							JsonArray values = new JsonArray();
							// Values for the INSERT clause
							values.add(resourceId)
									.add(object.getValue("owner"))
									.add(object.getValue("booking_reason"))
									.add(object.getValue("status"))
									.add(startDate)
									.add(endDate);
							// Values for the NOT EXISTS clause
							values.add(resourceId)
									.add(startDate)
									.add(endDate)
									.add(startDate)
									.add(endDate);

							
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
							
							// Send query to eventbus
							Sql.getInstance()
									.prepared(
											query.toString(),
											values,
											validUniqueResultHandler(handler));
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

	
	private void addFieldToUpdate(StringBuilder sb, String fieldname, JsonObject object, JsonArray values){
		if("start_date".equals(fieldname) || "end_date".equals(fieldname)){
			sb.append(fieldname).append("= to_timestamp(?), ");
		}
		else {
			sb.append(fieldname).append("= ?, ");
		}
		values.add(object.getValue(fieldname));
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
								String bookingId = request.params().get("bookingId");
								SqlConf conf = SqlConfs.getConf(BookingController.class.getName());
								
								// Query
								JsonArray values = new JsonArray();
								StringBuilder sb = new StringBuilder();
								for (String fieldname : object.getFieldNames()) {
									addFieldToUpdate(sb, fieldname, object, values);
								}
								
								StringBuilder query = new StringBuilder();
								query.append("UPDATE ")
										.append(conf.getSchema())
										.append(conf.getTable())
										.append(" SET " + sb.toString() + "modified = NOW()")
										.append(" WHERE id = ?;");
								values.add(bookingId);
								
								// Send query to eventbus
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
								
								crudService.update(bookingId, object, user, defaultResponseHandler(request));
								
								// TODO : en cas de validation, refuser les demandes concurrentes
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
						SqlConf conf = SqlConfs.getConf(BookingController.class.getName());

						// TODO : à revoir
						StringBuilder query = new StringBuilder();
						query.append("SELECT * FROM ")
								.append(conf.getSchema())
								.append(conf.getTable())
								.append(" WHERE status = " + CREATED.status())
								.append(";");
						
						Sql.getInstance().raw(query.toString(), 
								validResultHandler(arrayResponseHandler(request)));
					}
				}
			});
			
	 }
	
	// Pour afficher l'historique des reservations
	// @Get("/bookings/all")
	// @ApiDoc("List all bookings")
	// @SecuredAction(value = "rbs.manage", type = ActionType.RESOURCE)

}
