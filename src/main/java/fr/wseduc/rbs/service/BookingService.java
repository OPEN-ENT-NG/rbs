package fr.wseduc.rbs.service;

import java.util.List;

import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public interface BookingService {

	public void createBooking(final Long resourceId, final JsonObject data, final UserInfos user,
			final Handler<Either<String, JsonObject>> handler);
	
	public void updateBooking(final String bookingId, final JsonObject data,
			final Handler<Either<String, JsonObject>> handler);
	
	public void listUnprocessedBookings(final List<String> groupsAndUserIds, final UserInfos user, 
			final Handler<Either<String, JsonArray>> handler);
}
