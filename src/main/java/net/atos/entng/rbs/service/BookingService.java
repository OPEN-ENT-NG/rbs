/*
 * Copyright © Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package net.atos.entng.rbs.service;

import fr.wseduc.webutils.Either;
import org.entcore.common.service.CrudService;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.Date;
import java.util.List;

public interface BookingService extends CrudService {

	void createBooking(final String resourceId, final JsonObject data, final UserInfos user,
	                   final Handler<Either<String, JsonArray>> handler);

	void createPeriodicBooking(final String resourceId, final String selectedDays, final int firstSelectedDay,
	                           final JsonObject data, final UserInfos user, final Handler<Either<String, JsonArray>> handler);

	void updateBooking(final String resourceId, final String bookingId, final JsonObject data,
	                   final Handler<Either<String, JsonObject>> handler);

	void updatePeriodicBooking(final String resourceId, final String bookingId, final String selectedDays,
	                           final int firstSelectedDay, final JsonObject data, final UserInfos user,
	                           final Handler<Either<String, JsonArray>> handler);

	void processBooking(final String resourceId, final String bookingId,
	                    final int newStatus, final JsonObject data,
	                    final UserInfos user, final Handler<Either<String, JsonArray>> handler);

	void listUserBookings(final UserInfos user,
	                      final Handler<Either<String, JsonArray>> handler);

	void listAllBookings(final UserInfos user, final List<String> groupsAndUserIds, final Handler<Either<String, JsonArray>> handler);

	void listAllBookingsByDates(final UserInfos user, final List<String> groupsAndUserIds, final String startDate, final String endDate,
	                            final Handler<Either<String, JsonArray>> handler);

	void listFullSlotsBooking(final String bookingId,
	                          final Handler<Either<String, JsonArray>> handler);

	void listBookingsByResource(final String resourceId,
	                            final Handler<Either<String, JsonArray>> handler);

	void listUnprocessedBookings(final List<String> groupsAndUserIds, final UserInfos user,
	                             final Handler<Either<String, JsonArray>> handler);

	void getModeratorsIds(final String bookingId, final UserInfos user,
	                      final Handler<Either<String, JsonArray>> handler);

	void getResourceName(final String bookingId, final Handler<Either<String, JsonObject>> handler);

	void getBookingWithResourceName(final String bookingId, final Handler<Either<String, JsonObject>> handler);

	void getParentBooking(final String bookingId, final Handler<Either<String, JsonObject>> handler);

	void getBooking(final String bookingId, final Handler<Either<String, JsonObject>> handler);

	void deleteFuturePeriodicBooking(String bookingId, Date startDate, Handler<Either<String, JsonArray>> handler);
}
