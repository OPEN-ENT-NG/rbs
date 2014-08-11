package fr.wseduc.rbs.service;

import java.util.List;

import org.entcore.common.service.CrudService;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public interface BookingService extends CrudService {

	public void createBooking(final Object resourceId, final JsonObject data, final UserInfos user,
			final Handler<Either<String, JsonObject>> handler);
	
	public void updateBooking(final Object resourceId, final Object bookingId, final JsonObject data,
			final Handler<Either<String, JsonObject>> handler);
	
	public void processBooking(final Object resourceId, final Object bookingId, 
			final int newStatus, final JsonObject data, 
			final UserInfos user, final Handler<Either<String, JsonObject>> handler);
	
	public void listUserBookings(final UserInfos user, 
			final Handler<Either<String, JsonArray>> handler);
	
	public void listBookingsByResource(final Object resourceId, 
			final Handler<Either<String, JsonArray>> handler);
	
	public void listUnprocessedBookings(final List<String> groupsAndUserIds, final UserInfos user, 
			final Handler<Either<String, JsonArray>> handler);
}
