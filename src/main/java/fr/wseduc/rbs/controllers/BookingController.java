package fr.wseduc.rbs.controllers;

import static fr.wseduc.rbs.BookingStatus.*;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;
import static org.entcore.common.sql.SqlResult.validResultHandler;
import static org.entcore.common.sql.SqlResult.validRowsResultHandler;
import static org.entcore.common.sql.SqlResult.validUniqueResultHandler;

import java.util.ArrayList;
import java.util.List;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.service.VisibilityFilter;
import org.entcore.common.sql.Sql;
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

							/* Query :
							 * Unix timestamps are converted into postgresql timestamps.
							 * Check that there does not exist a validated booking that overlaps the new booking.
							 */
							StringBuilder query = new StringBuilder();
							query.append("INSERT INTO rbs.booking")
									.append("(resource_id, owner, booking_reason, status, start_date, end_date)")
									.append(" SELECT  ?, ?, ?, ?, to_timestamp(?), to_timestamp(?)");
							query.append(" WHERE NOT EXISTS (")
									.append("SELECT id FROM rbs.booking")
									.append(" WHERE resource_id = ?")
									.append(" AND status = ").append(VALIDATED.status())
									.append(" AND (")
									.append("( start_date >= to_timestamp(?) AND start_date < to_timestamp(?) )")
									.append(" OR ( end_date > to_timestamp(?) AND end_date <= to_timestamp(?) )")
									.append(")) RETURNING id;");

							Object startDate = object.getValue("start_date");
							Object endDate = object.getValue("end_date");
							
							JsonArray values = new JsonArray();
							// Values for the INSERT clause
							values.add(resourceId)
									.add(user.getUserId())
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
								
								// Query
								JsonArray values = new JsonArray();
								StringBuilder sb = new StringBuilder();
								for (String fieldname : object.getFieldNames()) {
									addFieldToUpdate(sb, fieldname, object, values);
								}
								
								StringBuilder query = new StringBuilder();
								query.append("UPDATE rbs.booking")
										.append(" SET ")
										.append(sb.toString())
										.append("modified = NOW()")
										.append(" WHERE id = ?;");
								values.add(bookingId);
								
								// Send query to eventbus
								Sql.getInstance()
										.prepared(
												query.toString(),
												values,
												validRowsResultHandler(notEmptyResponseHandler(request)));
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
						
						// Query
						StringBuilder query = new StringBuilder();
						JsonArray values = new JsonArray();
						query.append("SELECT DISTINCT b.* FROM rbs.booking AS b ")
								.append(" INNER JOIN rbs.resource AS r ON b.resource_id = r.id")
								.append(" INNER JOIN rbs.resource_type AS t ON t.id = r.type_id")
								.append(" LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
								.append(" LEFT JOIN rbs.resource_shares AS rs ON r.id = rs.resource_id")
								.append(" WHERE b.status = ").append(CREATED.status());
						
						query.append(" AND (ts.member_id IN ")
								.append(Sql.listPrepared(groupsAndUserIds.toArray()));
						for (String groupOruser : groupsAndUserIds) {
							values.add(groupOruser);
						}
						
						query.append(" OR rs.member_id IN ")
								.append(Sql.listPrepared(groupsAndUserIds.toArray()));
						for (String groupOruser : groupsAndUserIds) {
							values.add(groupOruser);
						}
						
						query.append(" OR t.owner = ?");
						values.add(user.getUserId());
						
						query.append(" OR r.owner = ?);");
						values.add(user.getUserId());
												
						// Send query to event bus
						Sql.getInstance().prepared(query.toString(), values, 
								validResultHandler(arrayResponseHandler(request)));
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
