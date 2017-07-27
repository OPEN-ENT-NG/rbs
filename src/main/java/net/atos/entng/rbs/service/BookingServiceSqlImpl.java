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
import fr.wseduc.webutils.Either.Right;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlStatementsBuilder;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.DateUtils;
import org.entcore.common.utils.StringUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static net.atos.entng.rbs.BookingStatus.*;
import static net.atos.entng.rbs.BookingUtils.*;
import static org.entcore.common.sql.Sql.parseId;
import static org.entcore.common.sql.SqlResult.*;

public class BookingServiceSqlImpl extends SqlCrudService implements BookingService {

	private final static String LOCK_BOOKING_QUERY = "LOCK TABLE rbs.booking IN SHARE ROW EXCLUSIVE MODE;";
	private final static String UPSERT_USER_QUERY = "SELECT rbs.merge_users(?,?)";
	public final static String DATE_FORMAT = "DD/MM/YY HH24:MI";
	private static final Logger log = LoggerFactory.getLogger(BookingServiceSqlImpl.class);

	public BookingServiceSqlImpl() {
		super("rbs", "booking");
	}

	@Override
	public void createBooking(final String resourceId, final JsonObject data, final UserInfos user,
	                          final Handler<Either<String, JsonArray>> handler) {

		SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
		Object rId = parseId(resourceId);

		// Upsert current user
		statementsBuilder.prepared(UPSERT_USER_QUERY,
				new JsonArray().add(user.getUserId()).add(user.getUsername()));

		// Lock query to avoid race condition
		statementsBuilder.raw(LOCK_BOOKING_QUERY);

		Object bookingReason = data.getValue("booking_reason");
		final JsonArray slots = data.getArray("slots");
		for (int i = 0; i < slots.size(); i++) {
			JsonObject slot = slots.get(i);
			Object newStartDate = slot.getValue("start_date");
			Object newEndDate = slot.getValue("end_date");
			statementsBuilder = prepareInsertBooking(user, rId, statementsBuilder, bookingReason, newStartDate, newEndDate);
		}
		// Send queries to eventbus
		Sql.getInstance().transaction(statementsBuilder.build(), validResultsHandler(handler, "id", "status", "start_date", "end_date"));
	}

	private SqlStatementsBuilder prepareInsertBooking(UserInfos user, Object resourceId, SqlStatementsBuilder statementsBuilder, Object bookingReason, Object startDate, Object endDate) {
		// Insert query
		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();


		query.append("INSERT INTO rbs.booking")
				.append("(resource_id, owner, booking_reason, status, start_date, end_date)")
				.append(" SELECT  ?, ?, ?,");
		values.add(resourceId)
				.add(user.getUserId())
				.add(bookingReason);

		// If validation is activated, the booking is created with status "created".
		// Otherwise, it is created with status "validated".
		// TODO V2 : la reservation doit etre automatiquement validee si le demandeur est valideur
		query.append(" (SELECT CASE ")
				.append(" WHEN (r.validation IS true) THEN ?")
				.append(" ELSE ?")
				.append(" END")
				.append(" FROM rbs.resource AS r")
				.append(" WHERE r.id = ?),");
		values.add(CREATED.status())
				.add(VALIDATED.status())
				.add(resourceId);

		// Unix timestamps are converted into postgresql timestamps.
		query.append(" to_timestamp(?) AT TIME ZONE 'UTC', to_timestamp(?) AT TIME ZONE 'UTC'");
		values.add(startDate)
				.add(endDate);

		// Check that there does not exist a validated booking that overlaps the new booking.
		query.append(" WHERE NOT EXISTS (")
				.append("SELECT 1 FROM rbs.booking")
				.append(" WHERE resource_id = ?")
				.append(" AND status = ?")
				.append(" AND (start_date, end_date) OVERLAPS (to_timestamp(?) AT TIME ZONE 'UTC', to_timestamp(?) AT TIME ZONE 'UTC')")
				.append(") RETURNING id, status,")
				.append(" to_char(start_date, '").append(DATE_FORMAT).append("') AS start_date,")
				.append(" to_char(end_date, '").append(DATE_FORMAT).append("') AS end_date");

		values.add(resourceId)
				.add(VALIDATED.status())
				.add(startDate)
				.add(endDate);

		return statementsBuilder.prepared(query.toString(), values);
	}

	/**
	 * @throws IllegalArgumentException, IndexOutOfBoundsException
	 */
	@Override
	public void createPeriodicBooking(final String resourceId, final String selectedDays,
	                                  final int firstSelectedDay, final JsonObject multiSlotBooking, final UserInfos user,
	                                  final Handler<Either<String, JsonArray>> handler) {

		SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
		statementsBuilder.prepared(UPSERT_USER_QUERY,
				new JsonArray().add(user.getUserId()).add(user.getUsername()));

		JsonArray slots = multiSlotBooking.getArray("slots", null);
		// case of creation during an update
		if (slots == null) {
			long startDate = multiSlotBooking.getLong("start_date", 0L);
			long endDate = multiSlotBooking.getLong("end_date", 0L);
			slots = new JsonArray();
			JsonObject uniqueSlot = new JsonObject();
			uniqueSlot.putValue("start_date", startDate);
			uniqueSlot.putValue("end_date", endDate);
			slots.add(uniqueSlot);
		}
		// create a periodic reservation dedicated to each slot
		for (int i = 0; i < slots.size(); i++) {
			JsonObject slot = slots.get(i);
			long slotStartDate = slot.getLong("start_date", 0L);
			long slotEndDate = slot.getLong("end_date", 0L);

			StringBuilder query = new StringBuilder();
			JsonArray values = new JsonArray();

			Object rId = parseId(resourceId);
			final long endDate = multiSlotBooking.getLong("periodic_end_date", 0L);
			final int occurrences = multiSlotBooking.getInteger("occurrences", 0);

			// 1. WITH clause to insert the "parent" booking (i.e. the periodic booking)
			query.append("WITH parent_booking AS (")
					.append(" INSERT INTO rbs.booking (resource_id, owner, booking_reason, start_date, end_date,")
					.append(" is_periodic, periodicity, occurrences, days)")
					.append(" VALUES (?, ?, ?, to_timestamp(?) AT TIME ZONE 'UTC',");
			values.add(rId)
					.add(user.getUserId())
					.add(multiSlotBooking.getString("booking_reason"))
					.add(slotStartDate);

			query.append(" to_timestamp(?) AT TIME ZONE 'UTC',");
			values.add(endDate > 0L ? endDate : null); // the null value will be replaced by the last slot's end date, which will be computed
			final int endDateIndex = values.size() - 1;

			query.append(" ?, ?, ?,");
			values.add(true)
					.add(multiSlotBooking.getInteger("periodicity"))
					.add(occurrences != 0 ? occurrences : null);

			// NB : Bit string type cannot be used in a preparedStatement
			query.append(" B'")
					.append(selectedDays)
					.append("') RETURNING id)");


			// 2. Insert clause for the child bookings
			final long lastSlotEndDate = appendInsertChildBookingsQuery(query, values, rId,
					selectedDays, firstSelectedDay, slotStartDate, slotEndDate, multiSlotBooking, user, null);

			// Update end_date value in JsonArray values
			if (endDate <= 0L) {
				values = getValuesWithProperEndDate(values, lastSlotEndDate, endDateIndex);
			}

			statementsBuilder.prepared(query.toString(), values);
		}
		Sql.getInstance().transaction(statementsBuilder.build(), getPeriodicCreationResultHandler(handler));

	}

	private Handler<Message<JsonObject>> getPeriodicCreationResultHandler(final Handler<Either<String, JsonArray>> handler) {
		return validResultsHandler(new Handler<Either<String, JsonArray>>() {
			@Override
			public void handle(Either<String, JsonArray> event) {
				if (event.isRight()) {
					JsonArray storageResponse = event.right().getValue();
					JsonArray creationResponses = extractCreationResponses(storageResponse);
					Right<String, JsonArray> arrayRight = new Right<>(creationResponses);
					handler.handle(arrayRight);
				} else {
					handler.handle(event);
				}
			}

			private JsonArray extractCreationResponses(JsonArray storageResponse) {
				JsonArray storageResponses = new JsonArray();
				if (storageResponse != null && storageResponse.size() > 0) {
					for (Object o : storageResponse) {
						if (!(o instanceof JsonArray)) continue;
						JsonArray response = (JsonArray) o;
						if (response.size() > 0) {
							for (Object o1 : response) {
								if (!(o1 instanceof JsonObject)) continue;
								JsonObject creationResult = (JsonObject) o1;
								if (creationResult.containsField("id")
										&& creationResult.containsField("status")) {
									storageResponses.add(creationResult);
								}
							}
						}
					}
				}
				return storageResponses;
			}
		}, "id", "status");
	}

	/**
	 * Appends the insert child bookings query to parameter query, and the associated values to parameter values
	 *
	 * @param bookingId : used when updating a periodic booking
	 * @return Unix timestamp of the last child booking's end date
	 */

	private long appendInsertChildBookingsQuery(StringBuilder query, JsonArray values, final Object resourceId,
	                                            final String selectedDays, final int firstSelectedDay,
	                                            final long firstSlotStartDate, final long firstSlotEndDate,
	                                            final JsonObject data,
	                                            final UserInfos user, final Object bookingId) {

		final boolean isUpdate = (bookingId != null);

		final long endDate = data.getLong("periodic_end_date", 0L);
		final String bookingReason = data.getString("booking_reason");
		final int periodicity = data.getInteger("periodicity");
		long lastSlotEndDate = firstSlotEndDate;

		// 1. INSERT clause for the first child booking
		query.append(" INSERT INTO rbs.booking (resource_id, owner, booking_reason, start_date, end_date, parent_booking_id, status, refusal_reason)")
				.append(" VALUES(?, ?, ?, to_timestamp(?) AT TIME ZONE 'UTC', to_timestamp(?) AT TIME ZONE 'UTC',");
		values.add(resourceId)
				.add(user.getUserId())
				.add(bookingReason)
				.add(firstSlotStartDate)
				.add(firstSlotEndDate);

		if (isUpdate) { // Update of a periodic booking
			query.append(" ?,");
			values.add(bookingId);
		} else { // Creation of a periodic booking
			query.append("(select id from parent_booking),");
		}

		/* Subquery to insert proper status :
		 * refused if there exist a concurrent validated booking.
		 * Created if validation is activated.
		 * Validated otherwise
		 */
		query.append(" (SELECT CASE")
				.append(" WHEN (")
				.append(" EXISTS(SELECT 1 FROM rbs.booking")
				.append(" WHERE status = ?")
				.append(" AND (start_date, end_date) OVERLAPS (to_timestamp(?) AT TIME ZONE 'UTC', to_timestamp(?) AT TIME ZONE 'UTC')")
				.append(" AND resource_id = ?")
				.append(" )) THEN ?");
		values.add(VALIDATED.status())
				.add(firstSlotStartDate)
				.add(firstSlotEndDate)
				.add(resourceId)
				.add(REFUSED.status());
		query.append(" WHEN (r.validation IS true) THEN ?")
				.append(" ELSE ? END")
				.append(" FROM rbs.resource_type AS t")
				.append(" INNER JOIN rbs.resource AS r ON r.type_id = t.id")
				.append(" WHERE r.id = ?")
				.append("),");
		values.add(CREATED.status())
				.add(VALIDATED.status())
				.add(resourceId);

		// refused because of concurrent in case of periodic reservation
		query.append(" (SELECT CASE")
				.append(" WHEN (")
				.append(" EXISTS(SELECT 1 FROM rbs.booking")
				.append(" WHERE status = ?")
				.append(" AND (start_date, end_date) OVERLAPS (to_timestamp(?) AT TIME ZONE 'UTC', to_timestamp(?) AT TIME ZONE 'UTC')")
				.append(" AND resource_id = ?")
				.append(" )) THEN ?");
		values.add(VALIDATED.status())
				.add(firstSlotStartDate)
				.add(firstSlotEndDate)
				.add(resourceId)
				.add("<i18n>rbs.booking.automatically.refused.reason</i18n>");

		// finding the conflicted booking id
		query.append(" || ( SELECT id FROM rbs.booking")
				.append(" WHERE status = ?")
				.append(" AND (start_date, end_date) OVERLAPS (to_timestamp(?) AT TIME ZONE 'UTC', to_timestamp(?) AT TIME ZONE 'UTC')")
				.append(" AND resource_id = ? LIMIT 1 )");
		values.add(VALIDATED.status())
				.add(firstSlotStartDate)
				.add(firstSlotEndDate)
				.add(resourceId);


		query.append(" ELSE ? END)) ");
		values.add(null);

		// 2. Additional VALUES to insert the other child bookings
		int nbOccurences = data.getInteger("occurrences", -1);

		if (nbOccurences == -1) {
			long durationInDays = TimeUnit.DAYS.convert(endDate - firstSlotEndDate, TimeUnit.SECONDS);
			nbOccurences = getOccurrences(firstSelectedDay, selectedDays, durationInDays, periodicity);
		}

		if (nbOccurences > 1) {
			int selectedDay = firstSelectedDay;
			int intervalFromFirstDay = 0;

			for (int i = 1; i <= (nbOccurences - 1); i++) {
				int interval = getNextInterval(selectedDay, selectedDays, periodicity);
				intervalFromFirstDay += interval;
				selectedDay = (selectedDay + interval) % 7;

				query.append(", (?, ?, ?, to_timestamp(?) AT TIME ZONE 'UTC' + interval '")
						.append(intervalFromFirstDay) // NB : "interval '? day'" or "interval '?'" is not properly computed by prepared statement
						.append(" day', ");
				query.append("to_timestamp(?) AT TIME ZONE 'UTC' + interval '")
						.append(intervalFromFirstDay)
						.append(" day',");
				values.add(resourceId)
						.add(user.getUserId())
						.add(bookingReason)
						.add(firstSlotStartDate)
						.add(firstSlotEndDate);

				if (isUpdate) { // Update of a periodic booking
					query.append(" ?,");
					values.add(bookingId);
				} else { // Creation of a periodic booking
					query.append("(select id from parent_booking),");
				}

				query.append(" (SELECT CASE")
						.append(" WHEN (")
						.append(" EXISTS(SELECT 1 FROM rbs.booking")
						.append(" WHERE status = ?");
				query.append(" AND (start_date, end_date) OVERLAPS (to_timestamp(?) AT TIME ZONE 'UTC' + interval '")
						.append(intervalFromFirstDay)
						.append(" day', ")
						.append("to_timestamp(?) AT TIME ZONE 'UTC' + interval '")
						.append(intervalFromFirstDay)
						.append(" day')");
				query.append(" AND resource_id = ?")
						.append(" )) THEN ?");
				values.add(VALIDATED.status())
						.add(firstSlotStartDate)
						.add(firstSlotEndDate)
						.add(resourceId)
						.add(REFUSED.status());
				query.append(" WHEN (r.validation IS true) THEN ?")
						.append(" ELSE ? END")
						.append(" FROM rbs.resource_type AS t")
						.append(" INNER JOIN rbs.resource AS r ON r.type_id = t.id")
						.append(" WHERE r.id = ?")
						.append("), ");
				values.add(CREATED.status())
						.add(VALIDATED.status())
						.add(resourceId);

				// refused because of concurrent in case of periodic reservation
				query.append(" (SELECT CASE")
						.append(" WHEN (")
						.append(" EXISTS(SELECT 1 FROM rbs.booking")
						.append(" WHERE status = ?")
						.append(" AND (start_date, end_date) OVERLAPS (to_timestamp(?) AT TIME ZONE 'UTC' + interval '")
						.append(intervalFromFirstDay)
						.append(" day', ")
						.append("to_timestamp(?) AT TIME ZONE 'UTC' + interval '")
						.append(intervalFromFirstDay)
						.append(" day')")
						.append(" AND resource_id = ?")
						.append(" )) THEN ?");
				values.add(VALIDATED.status())
						.add(firstSlotStartDate)
						.add(firstSlotEndDate)
						.add(resourceId)
						.add("<i18n>rbs.booking.automatically.refused.reason</i18n>");

				// finding the conflicted booking id
				query.append(" || (SELECT id FROM rbs.booking")
						.append(" WHERE status = ?")
						.append(" AND (start_date, end_date) OVERLAPS (to_timestamp(?) AT TIME ZONE 'UTC' + interval '")
						.append(intervalFromFirstDay)
						.append(" day', ")
						.append("to_timestamp(?) AT TIME ZONE 'UTC' + interval '")
						.append(intervalFromFirstDay)
						.append(" day')")
						.append(" AND resource_id = ? LIMIT 1 )");
				values.add(VALIDATED.status())
						.add(firstSlotStartDate)
						.add(firstSlotEndDate)
						.add(resourceId);

				query.append(" ELSE ? END)) ");
				values.add(null);
			}

			lastSlotEndDate += TimeUnit.SECONDS.convert(intervalFromFirstDay, TimeUnit.DAYS);
		}

		query.append(" RETURNING id, status");
		return lastSlotEndDate;
	}

	/**
	 * @param values
	 * @param lastSlotEndDate
	 * @param endDateIndex
	 * @return new JsonArray, with proper end date
	 */
	private JsonArray getValuesWithProperEndDate(JsonArray values, long lastSlotEndDate, int endDateIndex) {
		JsonArray newValues = new JsonArray();
		int i = 0;
		for (Object object : values) {
			if (i == endDateIndex) {
				newValues.add(lastSlotEndDate);
			} else {
				newValues.add(object);
			}
			i++;
		}

		return newValues;
	}

	@Override
	public void updateBooking(final String resourceId, final String bookingId, final JsonObject data,
	                          final Handler<Either<String, JsonObject>> handler) {

		SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
		Object rId = parseId(resourceId);
		Object bId = parseId(bookingId);
		JsonArray slots = data.getArray("slots");
		JsonObject slot = slots.get(0);
		// Lock query to avoid race condition
		statementsBuilder.raw(LOCK_BOOKING_QUERY);

		// Update query
		JsonArray values = new JsonArray();
		StringBuilder sb = new StringBuilder();
		for (String fieldname : data.getFieldNames()) {
			if (fieldname.equals("slots")) {
				addFieldToUpdate(sb, "start_date", slot, values);
				addFieldToUpdate(sb, "end_date", slot, values);
			} else {
				addFieldToUpdate(sb, fieldname, data, values);
			}
		}

		StringBuilder query = new StringBuilder();
		query.append("UPDATE rbs.booking")
				.append(" SET ")
				.append(sb.toString())
				.append("modified = NOW(),");

		// If validation is activated, the booking status is updated to "created" (the booking must be validated anew).
		// Otherwise, it is updated to "validated".
		query.append(" status = (SELECT CASE ")
				.append(" WHEN (t.validation IS true) THEN ?")
				.append(" ELSE ?")
				.append(" END")
				.append(" FROM rbs.resource_type AS t")
				.append(" INNER JOIN rbs.resource AS r ON r.type_id = t.id")
				.append(" INNER JOIN rbs.booking AS b on b.resource_id = r.id")
				.append(" WHERE b.id = ?)");
		values.add(CREATED.status())
				.add(VALIDATED.status())
				.add(bId);

		query.append(" WHERE id = ?")
				.append(" AND resource_id = ?");
		values.add(bId)
				.add(rId);


		// Check that there does not exist a validated booking that overlaps the updated booking.
		query.append(" AND NOT EXISTS (")
				.append("SELECT 1 FROM rbs.booking")
				.append(" WHERE resource_id = ?")
				.append(" AND id != ?")
				.append(" AND status = ?")
				.append(" AND (start_date, end_date) OVERLAPS (to_timestamp(?) AT TIME ZONE 'UTC', to_timestamp(?) AT TIME ZONE 'UTC')")
				.append(") RETURNING id, status,")
				.append(" to_char(start_date, '").append(DATE_FORMAT).append("') AS start_date,")
				.append(" to_char(end_date, '").append(DATE_FORMAT).append("') AS end_date");

		Object newStartDate = data.getValue("start_date");
		Object newEndDate = data.getValue("end_date");

		values.add(rId)
				.add(bId)
				.add(VALIDATED.status())
				.add(newStartDate)
				.add(newEndDate);

		statementsBuilder.prepared(query.toString(), values);

		// Send queries to eventbus
		Sql.getInstance().transaction(statementsBuilder.build(),
				validUniqueResultHandler(1, handler));
	}

	private void addFieldToUpdate(StringBuilder sb, String fieldname,
	                              JsonObject object, JsonArray values) {

		if ("start_date".equals(fieldname) || "end_date".equals(fieldname)) {
			sb.append(fieldname).append("= to_timestamp(?) AT TIME ZONE 'UTC', ");
		} else {
			sb.append(fieldname).append("= ?, ");
		}
		values.add(object.getValue(fieldname));
	}

	@Override
	public void updatePeriodicBooking(final String resourceId, final String bookingId, final String selectedDays,
	                                  final int firstSelectedDay, final JsonObject data, final UserInfos user,
	                                  final Handler<Either<String, JsonArray>> handler) {

		final long endDate = data.getLong("periodic_end_date", 0L);
		final int occurrences = data.getInteger("occurrences", 0);
		final JsonArray slots = data.getArray("slots");
		JsonObject slot = slots.get(0);

		SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
		Object rId = parseId(resourceId);
		Object bId = parseId(bookingId);

		// 1. Lock query to avoid race condition
		statementsBuilder.raw(LOCK_BOOKING_QUERY);

		// 2. Update parent booking
		StringBuilder parentQuery = new StringBuilder();
		JsonArray parentValues = new JsonArray();
		parentQuery.append("UPDATE rbs.booking")
				.append(" SET booking_reason = ?, start_date = to_timestamp(?) AT TIME ZONE 'UTC', end_date = to_timestamp(?) AT TIME ZONE 'UTC',");
		parentValues.add(data.getString("booking_reason"))
				.add(slot.getLong("start_date"))
				.add(endDate > 0L ? endDate : null); // the null value will be replaced by the last slot's end date
		final int endDateIndex = parentValues.size() - 1;

		parentQuery.append(" periodicity = ?, occurrences = ?, modified = NOW(),");
		parentValues.add(data.getInteger("periodicity"))
				.add(occurrences != 0 ? occurrences : null);

		parentQuery.append(" days = B'")
				.append(selectedDays)
				.append("' WHERE resource_id = ?")
				.append(" AND id = ?")
				.append(" AND is_periodic = ?");
		parentValues.add(rId)
				.add(bId)
				.add(true);

		// 3. Delete child bookings
		String deleteQuery = "DELETE FROM rbs.booking WHERE parent_booking_id = ?";

		// 4. Create new child bookings
		StringBuilder insertQuery = new StringBuilder();
		JsonArray insertValues = new JsonArray();
		final long firstSlotStartDate = slot.getLong("start_date");
		final long firstSlotEndDate = slot.getLong("end_date");
		final long lastSlotEndDate = appendInsertChildBookingsQuery(insertQuery, insertValues, rId,
				selectedDays, firstSelectedDay, firstSlotStartDate, firstSlotEndDate, data, user, bId);

		// Update end_date value in JsonArray parentValues
		if (endDate <= 0L) {
			parentValues = getValuesWithProperEndDate(parentValues, lastSlotEndDate, endDateIndex);
		}

		// Add queries to SqlStatementsBuilder
		statementsBuilder.prepared(parentQuery.toString(), parentValues);
		statementsBuilder.prepared(deleteQuery, new JsonArray().add(bId));
		statementsBuilder.prepared(insertQuery.toString(), insertValues);

		// Send queries to event bus
		Sql.getInstance().transaction(statementsBuilder.build(), validResultHandler(3, handler));
	}

	@Override
	public void deleteFuturePeriodicBooking(String bookingId, Date startDate, Handler<Either<String, JsonArray>> handler) {
		SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
		Object bId = parseId(bookingId);

		// 1. Lock query to avoid race condition
		statementsBuilder.raw(LOCK_BOOKING_QUERY);
		// 2. Delete child bookings
		StringBuilder deleteQuery = new StringBuilder();
		JsonArray deleteValues = new JsonArray();
		deleteQuery.append("DELETE FROM rbs.booking")
				.append(" WHERE parent_booking_id = ?")
				.append(" AND start_date >= to_timestamp(?) AT TIME ZONE 'UTC'");
		deleteValues.add(bId);
		deleteValues.add(startDate.getTime() / 1000);


		// 3. Update parent booking with last end date slot
		StringBuilder parentQuery = new StringBuilder();
		JsonArray parentValues = new JsonArray();
		parentQuery.append("UPDATE rbs.booking")
				.append(" SET modified = NOW()")
				.append(" WHERE id = ?");
		parentValues.add(bId);

		// Add queries to SqlStatementsBuilder
		statementsBuilder.prepared(deleteQuery.toString(), deleteValues);
		statementsBuilder.prepared(parentQuery.toString(), parentValues);

		// Send queries to event bus
		Sql.getInstance().transaction(statementsBuilder.build(), validResultHandler(2, handler));
	}


	@Override
	public void processBooking(final String resourceId, final String bookingId,
	                           final int newStatus, final JsonObject data,
	                           final UserInfos user, final Handler<Either<String, JsonArray>> handler) {

		SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
		Object rId = parseId(resourceId);
		Object bId = parseId(bookingId);

		// 1. Upsert current user
		statementsBuilder.prepared(UPSERT_USER_QUERY,
				new JsonArray().add(user.getUserId()).add(user.getUsername()));

		// 2. Lock query to avoid race condition
		statementsBuilder.raw(LOCK_BOOKING_QUERY);

		// 3. Query to validate or refuse booking
		StringBuilder sb = new StringBuilder();
		JsonArray processValues = new JsonArray();
		for (String attr : data.getFieldNames()) {
			sb.append(attr).append(" = ?, ");
			processValues.add(data.getValue(attr));
		}
		StringBuilder processQuery = new StringBuilder();
		processQuery.append("UPDATE rbs.booking")
				.append(" SET ")
				.append(sb.toString())
				.append("modified = NOW() ")
				.append("WHERE id = ?")
				.append(" AND resource_id = ? ");
		processValues.add(bId)
				.add(rId);

		StringBuilder returningClause = new StringBuilder(" RETURNING id, status, owner, ")
				.append(" to_char(start_date, '").append(DATE_FORMAT).append("') AS start_date,")
				.append(" to_char(end_date, '").append(DATE_FORMAT).append("') AS end_date");

		if (newStatus != VALIDATED.status()) {
			processQuery.append(returningClause);
			statementsBuilder.prepared(processQuery.toString(), processValues);
		} else {
			// 3b. Additional clauses when validating a booking
			StringBuilder validateQuery = new StringBuilder();
			validateQuery.append("WITH validated_booking AS (")
					.append(" SELECT start_date, end_date")
					.append(" FROM rbs.booking")
					.append(" WHERE id = ?) ");
			JsonArray validateValues = new JsonArray();
			validateValues.add(bId);

			validateQuery.append(processQuery.toString());
			for (Object pValue : processValues) {
				validateValues.add(pValue);
			}

			// Validate if and only if there does NOT exist a concurrent validated booking
			validateQuery.append(" AND NOT EXISTS (")
					.append("SELECT 1 FROM rbs.booking")
					.append(" WHERE resource_id = ?")
					.append(" AND status = ?")
					.append(" AND (start_date, end_date) OVERLAPS ((SELECT start_date from validated_booking), (SELECT end_date from validated_booking))")
					.append(")");
			;
			validateValues.add(rId)
					.add(VALIDATED.status());

			validateQuery.append(returningClause);

			statementsBuilder.prepared(validateQuery.toString(), validateValues);


			// 4. Query to refuse potential concurrent bookings of the validated booking
			StringBuilder rbQuery = new StringBuilder();
			JsonArray rbValues = new JsonArray();

			// Store start and end dates of validated booking in a temporary table
			rbQuery.append("WITH validated_booking AS (")
					.append(" SELECT start_date, end_date")
					.append(" FROM rbs.booking")
					.append(" WHERE id = ?)");
			rbValues.add(bId);

			rbQuery.append(" UPDATE rbs.booking")
					.append(" SET status = ?, moderator_id = ?, refusal_reason = ?, modified = NOW() ");
			rbValues.add(REFUSED.status())
					.add(user.getUserId())
					.add("<i18n>rbs.booking.automatically.refused.reason</i18n>" + bId);

			// Refuse concurrent bookings if and only if the previous query has validated the booking
			rbQuery.append(" WHERE EXISTS (")
					.append(" SELECT 1 FROM rbs.booking")
					.append(" WHERE id = ?")
					.append(" AND status = ?)");
			rbValues.add(bId)
					.add(VALIDATED.status());

			// Get concurrent bookings' ids that must be refused
			rbQuery.append(" AND id in (")
					.append(" SELECT id FROM rbs.booking")
					.append(" WHERE resource_id = ?")
					.append(" AND status = ?")
					.append(" AND (start_date, end_date) OVERLAPS ((SELECT start_date from validated_booking), (SELECT end_date from validated_booking))")
					.append(")");
			rbValues.add(rId)
					.add(CREATED.status());

			rbQuery.append(returningClause);

			statementsBuilder.prepared(rbQuery.toString(), rbValues);
		}

		// Send queries to event bus
		Sql.getInstance().transaction(statementsBuilder.build(), validResultsHandler(handler));
	}

	@Override
	public void listUserBookings(final UserInfos user, final Handler<Either<String, JsonArray>> handler) {
		StringBuilder query = new StringBuilder();
		query.append("SELECT b.*, u.username AS owner_name, m.username AS moderator_name")
				.append(" FROM rbs.booking AS b")
				.append(" INNER JOIN rbs.users AS u ON b.owner = u.id")
				.append(" LEFT JOIN rbs.users AS m on b.moderator_id = m.id")
				.append(" WHERE b.owner = ?")
				.append(" ORDER BY b.start_date, b.end_date");

		JsonArray values = new JsonArray();
		values.add(user.getUserId());

		Sql.getInstance().prepared(query.toString(), values,
				validResultHandler(handler));
	}

	@Override
	public void listAllBookings(final UserInfos user, final List<String> groupsAndUserIds, final Handler<Either<String, JsonArray>> handler) {
		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		query.append("SELECT b.*, u.username AS owner_name, m.username AS moderator_name")
				.append(" FROM rbs.booking AS b")
				.append(" LEFT JOIN rbs.resource AS r ON r.id = b.resource_id")
				.append(" LEFT JOIN rbs.resource_type AS t ON r.type_id = t.id")
				.append(" LEFT JOIN rbs.resource_type_shares AS rs ON rs.resource_id = r.type_id")
				.append(" LEFT JOIN rbs.users AS u ON u.id = b.owner")
				.append(" LEFT JOIN rbs.users AS m on b.moderator_id = m.id")
				.append(" WHERE rs.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray()))
				.append(" OR t.owner = ?");
		for (String groupOruser : groupsAndUserIds) {
			values.add(groupOruser);
		}
		values.addString(user.getUserId());

		// A local administrator of a given school can see all resources of the school's types, even if he is not owner or manager of these types or resources
		List<String> scope = getLocalAdminScope(user);
		if (scope != null && !scope.isEmpty()) {
			query.append(" OR t.school_id IN ").append(Sql.listPrepared(scope.toArray()));
			for (String schoolId : scope) {
				values.addString(schoolId);
			}
		}

		query.append(" GROUP BY b.id, u.username, m.username ORDER BY b.start_date, b.end_date");

		Sql.getInstance().prepared(query.toString(), values, validResultHandler(handler));
	}

	@Override
	public void listAllBookingsByDates(final UserInfos user, final List<String> groupsAndUserIds, final String startDate, final String endDate,
	                                   final Handler<Either<String, JsonArray>> handler) {
		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		//find all booking without periodic booking
		query.append("SELECT b.*, u.username AS owner_name, m.username AS moderator_name")
				.append(" FROM rbs.booking AS b")
				.append(" LEFT JOIN rbs.resource AS r ON r.id = b.resource_id")
				.append(" LEFT JOIN rbs.resource_type AS t ON r.type_id = t.id")
				.append(" LEFT JOIN rbs.resource_type_shares AS rs ON rs.resource_id = r.type_id")
				.append(" LEFT JOIN rbs.users AS u ON u.id = b.owner")
				.append(" LEFT JOIN rbs.users AS m on b.moderator_id = m.id")
				.append(" WHERE ((b.is_periodic=FALSE AND b.start_date::date >= ?::date AND b.end_date::date < ?::date) ")
			    .append(" OR (b.occurrences IS NULL AND b.start_date::date >= ?::date AND b.end_date::date < ?::date)")
			    // this part is for reservations across 2 weeks
			    .append(" OR (b.is_periodic=FALSE AND b.start_date::date <= ?::date AND b.end_date::date > ?::date)")
				.append(" OR (b.is_periodic=FALSE AND b.start_date::date >= ?::date AND b.start_date::date < ?::date)")
				.append(" OR (b.is_periodic=FALSE AND b.end_date::date >= ?::date AND b.end_date::date < ?::date))")
				.append(" AND (rs.member_id IN ")
				//
				.append(Sql.listPrepared(groupsAndUserIds.toArray()))
				.append(" OR t.owner = ?");
		values.addString(startDate);
		values.addString(endDate);
		values.addString(startDate);
		values.addString(endDate);
		values.addString(startDate);
		values.addString(endDate);
		values.addString(startDate);
		values.addString(endDate);
		values.addString(startDate);
		values.addString(endDate);

		for (String groupOruser : groupsAndUserIds) {
			values.add(groupOruser);
		}
		values.addString(user.getUserId());

		// A local administrator of a given school can see all resources of the school's types, even if he is not owner or manager of these types or resources
		List<String> scope = getLocalAdminScope(user);
		if (scope != null && !scope.isEmpty()) {
			query.append(" OR t.school_id IN ").append(Sql.listPrepared(scope.toArray()));
			for (String schoolId : scope) {
				values.addString(schoolId);
			}
		}
		query.append(")  GROUP BY b.id, u.username, m.username ORDER BY b.start_date, b.end_date");

		Sql.getInstance().prepared(query.toString(), values, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				final Either<String, JsonArray> ei = validResult(event);
				if (ei.isRight()) {
					final Set<Number> setIdsPeriodicBooking = new HashSet<Number>();
					final JsonArray jsonBookingResult = ei.right().getValue();
					final JsonArray jsonAllBookingResult = new JsonArray();
					for (final Object o : jsonBookingResult) {
						if (!(o instanceof JsonObject)) {
							continue;
						} else {
							final JsonObject jo = (JsonObject) o;
							if (jo.getNumber("parent_booking_id") != null) {
								setIdsPeriodicBooking.add(jo.getNumber("parent_booking_id"));
								jsonAllBookingResult.addObject(jo);
							} else if (jo.getNumber("occurrences") == null) {
								try {
									final Date currentStartDate = DateUtils.parseTimestampWithoutTimezone(jo.getString("start_date"));
									final Date currentEndDate = DateUtils.parseTimestampWithoutTimezone(jo.getString("end_date"));
									final List<String> startSearched = StringUtils.split(startDate, "-");
									final Date dateStartSearched = DateUtils.create(Integer.parseInt(startSearched.get(0)),
											Integer.parseInt(startSearched.get(1)) - 1, Integer.parseInt(startSearched.get(2)));
									final List<String> endSearched = StringUtils.split(endDate, "-");
									final Date dateEndSearched = DateUtils.create(Integer.parseInt(endSearched.get(0)),
											Integer.parseInt(endSearched.get(1)) - 1, Integer.parseInt(endSearched.get(2)));
									if (DateUtils.isBetween(dateStartSearched, currentStartDate, currentEndDate) ||
											DateUtils.isBetween(dateEndSearched, currentStartDate, currentEndDate) ||
											(DateUtils.isBetween(currentStartDate, dateStartSearched, dateEndSearched) &&
													DateUtils.isBetween(currentEndDate, dateStartSearched, dateEndSearched))) {
										jsonAllBookingResult.addObject(jo);
									}
								} catch (ParseException e) {
									log.error("Can't parse date form RBS DB", e);
								}
							} else {
								jsonAllBookingResult.addObject(jo);
							}
						}
					}
					if (!setIdsPeriodicBooking.isEmpty()) {
						//find periodicBooking according to ids of sons
						final StringBuilder queryPeriodicBooking = new StringBuilder();
						queryPeriodicBooking.append("SELECT b.*, u.username AS owner_name, m.username AS moderator_name")
								.append(" FROM rbs.booking AS b")
								.append(" LEFT JOIN rbs.users AS u ON u.id = b.owner")
								.append(" LEFT JOIN rbs.users AS m on b.moderator_id = m.id")
								.append(" WHERE b.id IN ").append(Sql.listPrepared(setIdsPeriodicBooking.toArray()));
						final JsonArray values = new JsonArray(setIdsPeriodicBooking.toArray());

						Sql.getInstance().prepared(queryPeriodicBooking.toString(), values, new Handler<Message<JsonObject>>() {
							@Override
							public void handle(Message<JsonObject> event) {
								final Either<String, JsonArray> ei = validResult(event);
								if (ei.isRight()) {
									final JsonArray jsonBookingPeriodicResult = ei.right().getValue();

									if (jsonBookingPeriodicResult.size() != 0) {
										for (final Object o : jsonBookingPeriodicResult) {
											jsonAllBookingResult.add(o);
										}
									}
									handler.handle(new Right<String, JsonArray>(jsonAllBookingResult));
								} else {
									handler.handle(ei);
								}
							}
						});
					} else {
						handler.handle(ei);
					}
				} else {
					handler.handle(ei);
				}
			}
		});
	}

	@Override
	public void listFullSlotsBooking(final String bookingId,
	                                 final Handler<Either<String, JsonArray>> handler) {
		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		//find all booking without periodic booking
		query.append("SELECT b.*, u.username AS owner_name, m.username AS moderator_name")
				.append(" FROM rbs.booking AS b")
				.append(" LEFT JOIN rbs.users AS u ON u.id = b.owner")
				.append(" LEFT JOIN rbs.users AS m on b.moderator_id = m.id")
				.append(" WHERE b.parent_booking_id=?");
		values.add(parseId(bookingId));

		query.append(" ORDER BY b.start_date, b.end_date");

		Sql.getInstance().prepared(query.toString(), values,
				validResultHandler(handler));
	}

	@Override
	public void listBookingsByResource(final String resourceId,
	                                   final Handler<Either<String, JsonArray>> handler) {
		StringBuilder query = new StringBuilder();
		query.append("SELECT b.*, u.username AS owner_name, m.username AS moderator_name")
				.append(" FROM rbs.booking AS b")
				.append(" INNER JOIN rbs.users AS u ON b.owner = u.id")
				.append(" LEFT JOIN rbs.users AS m on b.moderator_id = m.id")
				.append(" WHERE b.resource_id = ?")
				.append(" ORDER BY b.start_date, b.end_date");

		JsonArray values = new JsonArray();
		values.add(parseId(resourceId));

		Sql.getInstance().prepared(query.toString(), values,
				validResultHandler(handler));
	}

	@Override
	public void listUnprocessedBookings(final List<String> groupsAndUserIds, final UserInfos user,
	                                    final Handler<Either<String, JsonArray>> handler) {

		// Query
		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		query.append("SELECT DISTINCT b.*, u.username AS owner_name")
				.append(" FROM rbs.booking AS b")
				.append(" INNER JOIN rbs.users AS u ON b.owner = u.id")
				.append(" INNER JOIN rbs.resource AS r ON b.resource_id = r.id")
				.append(" INNER JOIN rbs.resource_type AS t ON t.id = r.type_id")
				.append(" LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
				.append(" LEFT JOIN rbs.resource_shares AS rs ON r.id = rs.resource_id");

		// Get child bookings with status "created" and parent bookings that have at least one child booking with status "created"
		query.append(" WHERE (b.status = ?")
				.append(" OR (b.is_periodic = ? AND EXISTS(")
				.append(" SELECT 1 FROM rbs.booking AS c")
				.append(" WHERE c.parent_booking_id = b.id")
				.append(" AND c.status = ?)))");
		values.add(CREATED.status())
				.add(true)
				.add(CREATED.status());

		// Restrict results to bookings visible by current user
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

		query.append(" OR r.owner = ?)");
		values.add(user.getUserId());

		query.append(" ORDER BY b.start_date, b.end_date;");

		// Send query to event bus
		Sql.getInstance().prepared(query.toString(), values,
				validResultHandler(handler));
	}

	/**
	 * Get ids of groups and users that are moderators.
	 * user.getUserId() is excluded, because the booking owner does not need to be notified.
	 */
	@Override
	public void getModeratorsIds(final String bookingId, final UserInfos user,
	                             final Handler<Either<String, JsonArray>> handler) {

		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();
		Object bId = parseId(bookingId);

		// Resource owner is a moderator
		query.append("SELECT DISTINCT m.user_id, m.group_id FROM rbs.booking AS b")
				.append(" INNER JOIN rbs.resource AS r on r.id = b.resource_id")
				.append(" INNER JOIN rbs.members AS m on r.owner = m.id")
				.append(" WHERE b.id = ? AND r.owner != ?");
		values.add(bId)
				.add(user.getUserId());

		// Type owner is a moderator
		query.append(" UNION")
				.append(" SELECT DISTINCT m.user_id, m.group_id FROM rbs.booking AS b")
				.append(" INNER JOIN rbs.resource AS r on r.id = b.resource_id")
				.append(" INNER JOIN rbs.resource_type AS t on t.id = r.type_id")
				.append(" INNER JOIN rbs.members AS m on t.owner = m.id ")
				.append(" WHERE b.id = ? AND t.owner != ?");
		values.add(bId)
				.add(user.getUserId());

		// Users with resource right "processBooking" are moderators
		query.append(" UNION")
				.append(" SELECT DISTINCT m.user_id, m.group_id FROM rbs.booking AS b")
				.append(" INNER JOIN rbs.resource AS r on r.id = b.resource_id")
				.append(" INNER JOIN rbs.resource_shares AS rs ON r.id = rs.resource_id")
				.append(" INNER JOIN rbs.members AS m on rs.member_id = m.id")
				.append(" WHERE b.id = ? AND rs.member_id != ?")
				.append(" AND rs.action = 'net-atos-entng-rbs-controllers-BookingController|processBooking'");
		values.add(bId)
				.add(user.getUserId());

		query.append(" UNION")
				.append(" SELECT DISTINCT m.user_id, m.group_id FROM rbs.booking AS b")
				.append(" INNER JOIN rbs.resource AS r on r.id = b.resource_id")
				.append(" INNER JOIN rbs.resource_type AS t on t.id = r.type_id")
				.append(" INNER JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
				.append(" INNER JOIN rbs.members AS m on ts.member_id = m.id")
				.append(" WHERE b.id = ? AND ts.member_id != ?")
				.append(" AND ts.action = 'net-atos-entng-rbs-controllers-BookingController|processBooking'");
		values.add(bId)
				.add(user.getUserId());

		Sql.getInstance().prepared(query.toString(), values,
				validResultHandler(handler));
	}

	@Override
	public void getResourceName(final String bookingId, final Handler<Either<String, JsonObject>> handler) {
		this.getResourceName(bookingId, handler, false);
	}

	@Override
	public void getBookingWithResourceName(final String bookingId, final Handler<Either<String, JsonObject>> handler) {
		this.getResourceName(bookingId, handler, true);
	}

	private void getResourceName(final String bookingId, final Handler<Either<String, JsonObject>> handler, boolean withBooking) {
		StringBuilder query = new StringBuilder("SELECT r.name AS resource_name");
		if (withBooking) {
			query.append(", b.owner, b.is_periodic,")
					.append("to_char(b.start_date, '").append(DATE_FORMAT).append("') as start_date,")
					.append("to_char(b.end_date, '").append(DATE_FORMAT).append("') as end_date,")
					.append("b.parent_booking_id");
		}
		query.append(" FROM rbs.resource AS r")
				.append(" INNER JOIN rbs.booking AS b on r.id = b.resource_id")
				.append(" WHERE b.id = ?");

		Sql.getInstance().prepared(query.toString(), new JsonArray().add(parseId(bookingId)),
				validUniqueResultHandler(handler));
	}

	@Override
	public void getParentBooking(final String bookingId, final Handler<Either<String, JsonObject>> handler) {
		StringBuilder query = new StringBuilder();

		query.append("SELECT DISTINCT p.id, r.name as resource_name, ")
				.append("to_char(p.start_date, '").append(DATE_FORMAT).append("') as start_date,")
				.append("to_char(p.end_date, '").append(DATE_FORMAT).append("') as end_date")
				.append(" FROM rbs.booking AS b")
				.append(" INNER JOIN rbs.booking AS p ON b.parent_booking_id = p.id")
				.append(" INNER JOIN rbs.resource AS r ON b.resource_id = r.id")
				.append(" WHERE b.id = ?");

		Sql.getInstance().prepared(query.toString(), new JsonArray().add(parseId(bookingId)),
				validUniqueResultHandler(handler));
	}

	public void getBooking(final String bookingId, final Handler<Either<String, JsonObject>> handler) {
		this.retrieve(bookingId, handler);
	}


}
