package net.atos.entng.rbs.service;

import java.util.List;

import org.entcore.common.service.CrudService;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public interface BookingService extends CrudService {

	public void createBooking(final String resourceId, final JsonObject data, final UserInfos user,
			final Handler<Either<String, JsonObject>> handler);

	public void createPeriodicBooking(final String resourceId, final String selectedDays, final int firstSelectedDay,
			final JsonObject data, final UserInfos user, final Handler<Either<String, JsonArray>> handler);

	public void updateBooking(final String resourceId, final String bookingId, final JsonObject data,
			final Handler<Either<String, JsonObject>> handler);

	public void updatePeriodicBooking(final String resourceId, final String bookingId, final String selectedDays,
			final int firstSelectedDay, final JsonObject data, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler);

	public void processBooking(final String resourceId, final String bookingId,
			final int newStatus, final JsonObject data,
			final UserInfos user, final Handler<Either<String, JsonArray>> handler);

	public void listUserBookings(final UserInfos user,
			final Handler<Either<String, JsonArray>> handler);

	public void listAllBookings(final UserInfos user, final List<String> groupsAndUserIds, final String startDate, final String endDate,
								final Handler<Either<String, JsonArray>> handler);

	public void listBookingsByResource(final String resourceId,
			final Handler<Either<String, JsonArray>> handler);

	public void listUnprocessedBookings(final List<String> groupsAndUserIds, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler);

	public void getModeratorsIds(final String bookingId, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler);

	public void getResourceName(final String bookingId, final Handler<Either<String, JsonObject>> handler);

	public void getBookingWithResourceName(final String bookingId, final Handler<Either<String, JsonObject>> handler);

	public void getParentBooking(final String bookingId, final Handler<Either<String, JsonObject>> handler);

}
