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
import io.vertx.core.Future;
import net.atos.entng.rbs.model.ExportBooking;
import net.atos.entng.rbs.model.ExportRequest;
import org.entcore.common.service.CrudService;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.rbs.models.Booking;

import java.util.Date;
import java.util.List;

public interface BookingService extends CrudService {

	/**
	 * Create multiple bookings for multiple types
	 * @param types
	 * @param bookings
	 * @param user
	 * @return
	 */
	Future<JsonArray> createBookings(final List<Integer> types, final List<Booking> bookings, final UserInfos user);


	/**
	 * Create a booking
	 *
	 * @param resourceId : id of current resource
	 * @param data       : object which contains information of booking
	 * @param user       : information of current user logged
	 * @param handler    : handler which contains the response
	 */
    void createBooking(final String resourceId, final Booking data, final UserInfos user, final Handler<Either<String, JsonArray>> handler);

    /**
     * Create a periodic booking
     *
     * @param resourceId       : id of current resource
     * @param booking          : object which contains information of booking
     * @param user             : information of current user logged
     * @param handler          : handler which contains the response
     */
    void createPeriodicBooking(final String resourceId, Booking booking, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler);

    /**
     * Update a simple booking
     * @param resourceId        : id of current resource
     * @param data              : object which contains information of booking
     * @param handler           : handler which contains the response
     */
    void updateBooking(final String resourceId, final Booking data,
			final Handler<Either<String, JsonObject>> handler);

    /**
     * Update a periodic booking
     * @param resourceId        : id of current resource
     * @param data              : object which contains information of booking
     * @param user              : information of current user logged
     * @param handler           : handler which contains the response
     */
	public void updatePeriodicBooking(final String resourceId, final Booking data, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler);

    /**
     *  Update the status (validated or refused) of a booking which is in Processing status
     *
     * @param resourceId        : id of current resource
     * @param bookingId         : id of current booking
     * @param newStatus         : new status for the booking
     * @param data              : object which contains information of periodic booking
     * @param user              : object which contains information of periodic booking
     * @param handler           : handler which contains the response
     */
	public void processBooking(final String resourceId, final String bookingId, final int newStatus,
			final JsonObject data, final UserInfos user, final Handler<Either<String, JsonArray>> handler);

    /**
     * Get the list of bookings which are owned by the current user
     *
     * @param user    : information of current user logged
     * @param handler : handler which contains the response
     */
	public void listUserBookings(final UserInfos user, final Handler<Either<String, JsonArray>> handler);

    /**
     * Get the list of all bookings
     *
     * @param user             : information of current user logged
     * @param groupsAndUserIds : list of groups and id of users who are authorized
     * @param handler          : handler which contains the response
     */
	public void listAllBookings(final UserInfos user, final List<String> groupsAndUserIds,
			final Handler<Either<String, JsonArray>> handler);
    /**
     * Get the list of all bookings sorted by date
     *
     * @param user             : information of current user logged
     * @param groupsAndUserIds : list of groups and id of users who are authorized
     * @param startDate        : the beginning of booking
     * @param endDate          : the end of booking
     * @param handler          : handler which contains the response
     */
	public void listAllBookingsByDates(final UserInfos user, final List<String> groupsAndUserIds,
			final String startDate, final String endDate, final Handler<Either<String, JsonArray>> handler);
    /**
     * Get the list of all slots of current booking
     *
     * @param bookingId : id of current booking
     * @param handler   : handler which contains the response
     */
	public void listFullSlotsBooking(final String bookingId, final Handler<Either<String, JsonArray>> handler);
    /**
     * Get the booking list for current resource
     *
     * @param resourceId : id of current resource
     * @param handler    : handler which contains the response
     */
	public void listBookingsByResource(final String resourceId, final Handler<Either<String, JsonArray>> handler);

	/**
	 * Get the list of bookings unprocessed
	 *
	 * @param groupsAndUserIds : list of groups and id of users who are authorized
	 * @param user             : information of current user logged
	 * @param handler          : handler which contains the response
	 */
	void listUnprocessedBookings(final List<String> groupsAndUserIds, final UserInfos user,
	                             final Handler<Either<String, JsonArray>> handler);

	/**
	 * Get the moderator list of current booking
	 *
	 * @param bookingId : id of current booking
	 * @param user      : information of current user logged
	 * @param handler   : handler which contains the response
	 */
	void getModeratorsIds(final String bookingId, final UserInfos user,
	                      final Handler<Either<String, JsonArray>> handler);

	/**
	 * Get the resource name of current booking
	 *
	 * @param bookingId : id of current booking
	 * @param handler   : handler which contains the response
	 */
	void getResourceName(final String bookingId, final Handler<Either<String, JsonObject>> handler);

	/**
	 * Get the information of booking + the resource name of current booking
	 *
	 * @param bookingId : id of current booking
	 * @param handler   : handler which contains the response
	 */
	void getBookingWithResourceName(final String bookingId, final Handler<Either<String, JsonObject>> handler);

	/**
	 * Get the parent booking of current booking
	 *
	 * @param bookingId : id of current booking
	 * @param handler   : handler which contains the response
	 */
	void getParentBooking(final String bookingId, final Handler<Either<String, JsonObject>> handler);

	/**
	 * Get the information of current booking
	 *
	 * @param bookingId : id of current booking
	 * @param handler   : handler which contains the response
	 */
	void getBooking(final String bookingId, final Handler<Either<String, JsonObject>> handler);

	/**
	 * Delete the future bookings for a periodic booking
	 *
	 * @param bookingId : id of current booking
	 * @param startDate : the beggining of booking
	 * @param handler   : handler which contains the response
	 */
	void deleteFuturePeriodicBooking(String bookingId, Date startDate, Handler<Either<String, JsonArray>> handler);

	/**
	 * Get the list of bookings for the export
	 *
	 * @param exportRequest : the export request for generate an export
	 * @param handler       : handler which contains the response
	 */
	void getBookingsForExport(ExportRequest exportRequest, Handler<Either<String, List<ExportBooking>>> handler);

	/**
	 * Iterates through an array of bookings to save them
	 * The index of the resource ids must be the same as the corresponding booking
	 * @param bookingIds {@link List<Integer>} the array of resource ids
	 * @param user {@link UserInfos} the user
	 * @return {@link Future<JsonObject>} an array of saved bookings
	 */
	Future<JsonObject> checkRightsAndDeleteBookings(final List<Integer> bookingIds, final Boolean isBookingOwner, final UserInfos user);
}
