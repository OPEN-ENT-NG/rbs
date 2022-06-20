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
import fr.wseduc.webutils.http.Binding;
import fr.wseduc.webutils.http.HttpMethod;
import fr.wseduc.webutils.security.ActionType;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.Promise;
import net.atos.entng.rbs.controllers.BookingController;
import net.atos.entng.rbs.core.constants.Field;
import net.atos.entng.rbs.filters.TypeAndResourceAppendPolicy;
import net.atos.entng.rbs.helpers.FutureHelper;
import net.atos.entng.rbs.model.ExportBooking;
import net.atos.entng.rbs.model.ExportRequest;
import net.atos.entng.rbs.models.Slots;
import org.entcore.common.bus.BusResponseHandler;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.sql.SqlStatementsBuilder;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.DateUtils;
import org.entcore.common.utils.StringUtils;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.text.ParseException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.time.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.atos.entng.rbs.models.Booking;
import net.atos.entng.rbs.models.Slot;
import net.atos.entng.rbs.models.Slot.SlotIterable;
import static net.atos.entng.rbs.BookingStatus.*;
import static net.atos.entng.rbs.BookingUtils.*;
import static org.entcore.common.sql.Sql.parseId;
import static org.entcore.common.sql.SqlResult.*;

public class BookingServiceSqlImpl extends SqlCrudService implements BookingService {

	private final static String LOCK_BOOKING_QUERY = "LOCK TABLE rbs.booking IN SHARE ROW EXCLUSIVE MODE;";
	private final static String UPSERT_USER_QUERY = "SELECT rbs.merge_users(?,?)";
	public final static String DATE_FORMAT = "DD/MM/YY HH24:MI";
	private static final Logger log = LoggerFactory.getLogger(BookingServiceSqlImpl.class);
	private static DateTimeFormatter sqlFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
			.withZone(ZoneOffset.UTC);

	public BookingServiceSqlImpl() {
		super("rbs", "booking");
	}

	static String toSQLTimestamp(Long timestamp) {
		return timestamp == null ? null : sqlFormatter.format(Instant.ofEpochSecond(timestamp));
	}

	/**
	 * Iterates through an array of bookings to save them
	 * The index of the resource ids must be the same as the corresponding booking
	 * @param resourceIds {@link List<Integer>} the array of resource ids
	 * @param bookings {@link List<Booking>} the array of bookings
	 * @param user {@link UserInfos} the user
	 * @return {@link Future<JsonArray>} an array of saved bookings
	 */
	@Override
	public Future<JsonArray> createBookings(final List<Integer> resourceIds, final List<Booking> bookings, final UserInfos user) {
		Promise<JsonArray> promise = Promise.promise();

		List<Future<JsonArray>> bookingsFuture = new ArrayList<>();

		for (Booking booking : bookings) {
			JsonArray bookingSlotsArray = booking.getJson().getJsonArray(Field.SLOTS, new JsonArray());

			booking.setSlots(new Slots(bookingSlotsArray));
			Promise<JsonArray> bookingPromise = Promise.promise();
			String bookingResourceId = resourceIds.get(bookings.indexOf(booking)).toString();
			createBooking(bookingResourceId, booking, user, FutureHelper.handlerJsonArray(bookingPromise));
			bookingsFuture.add(bookingPromise.future());
		}

		FutureHelper.all(bookingsFuture)
				.onSuccess((res) -> {
					JsonArray savedBookings = new JsonArray();
					bookingsFuture.stream()
							.map((Future::result))
							.forEach((savedBooking) -> savedBookings.add(savedBooking.getValue(0)));

					promise.complete(savedBookings);
				})
				.onFailure((err) -> {
					String message = String.format("[Rbs@%s::createBookings]: an error has occurred while saving bookings: %s",
							this.getClass().getSimpleName(), err.getMessage());
					log.error(message);
					promise.fail(err.getMessage());
				});

		return promise.future();
	}

	@Override
	public void createBooking(final String resourceId, final Booking booking, final UserInfos user, final Handler<Either<String, JsonArray>> handler) {
		SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
		// Upsert current user
		statementsBuilder.prepared(UPSERT_USER_QUERY, new JsonArray().add(user.getUserId()).add(user.getUsername()));

		// Lock query to avoid race condition
		statementsBuilder.raw(LOCK_BOOKING_QUERY);

		booking.getSlots().forEach(slot-> {
			JsonObject statement = getCreationBooking(resourceId, booking, slot, user);
			statementsBuilder.prepared(statement.getString("query"), statement.getJsonArray("values"));
		});

		// Send queries to eventbus
		Sql.getInstance().transaction(statementsBuilder.build(), event -> {
			Either<String, JsonArray> result = SqlResult.validResults(event);
			if (result.isLeft()) {
				String message = "[RBS@BookingServiceSqlImpl::createBooking] An error has occured during statements transaction";
				log.error(message, result.left().getValue());
				handler.handle(new Either.Left<>(result.left().getValue()));
			} else {
				JsonArray statementsResults = result.right().getValue();
				// Removing the first (upsert current user) and second statements (lock query)
				statementsResults.remove(0);
				statementsResults.remove(0);

				JsonArray bookings = new JsonArray();
				// Looping our statementsResult as each JsonObject is inside a JsonArray then we clear data by creating
				// new JsonArray
				for (int i = 0; i < statementsResults.size(); i++) {
					if (Boolean.FALSE.equals(statementsResults.getJsonArray(i).isEmpty())) {
						bookings.add(statementsResults.getJsonArray(i).getJsonObject(0));
					}
				}
				handler.handle(new Either.Right<>(bookings));
			}
		});


	}

	private JsonObject getCreationBooking(final String resourceId, final Booking booking, final Slot slot, final UserInfos user) {


		Object rId = parseId(resourceId);

		// Insert query
		StringBuilder query = new StringBuilder();
		JsonArray values = new fr.wseduc.webutils.collections.JsonArray();

		query.append("INSERT INTO rbs.booking")
				.append("(resource_id, owner, booking_reason, quantity, status, start_date, end_date)")
				.append(" SELECT  ?, ?, ?, ?,");
		values.add(rId).add(user.getUserId()).add(booking.getBookingReason()).add(booking.getBookingQuantity());

		// If validation is activated, the booking is created with status "created".
		// Otherwise, it is created with status "validated".
		// TODO V2 : la reservation doit etre automatiquement validee si le demandeur
		// est valideur
		query.append(" (SELECT CASE WHEN (r.validation IS true) THEN ? ELSE ? END")
				.append(" FROM rbs.resource AS r")
				.append(" WHERE r.id = ?),");
		values.add(CREATED.status()).add(VALIDATED.status()).add(rId);

		// Unix timestamps are converted into postgresql timestamps.
		query.append(" ?, ?");
		values.add(toSQLTimestamp(slot.getStartUTC()))
				.add(toSQLTimestamp(slot.getEndUTC()));

		// Returning result
		query.append(" RETURNING id, quantity, status, to_char(start_date, '").append(DATE_FORMAT)
				.append("') AS start_date, to_char(end_date, '").append(DATE_FORMAT).append("') AS end_date");

		return new JsonObject().put("query", query).put("values", values);
	}

	/**
	 * @throws IllegalArgumentException, IndexOutOfBoundsException
	 */
	@Override
	public void createPeriodicBooking(final String resourceId, Booking booking, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler) {

		SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
		statementsBuilder.prepared(UPSERT_USER_QUERY,
				new fr.wseduc.webutils.collections.JsonArray().add(user.getUserId()).add(user.getUsername()));


		Slots slots = booking.getSlots() ;
		// create a periodic reservation dedicated to each slot
		for (Slot slot: slots) {
            StringBuilder query = new StringBuilder();
            JsonArray values = new fr.wseduc.webutils.collections.JsonArray();

            Object rId = parseId(resourceId);
            final long endDate = booking.getPeriodicEndDateAsUTCSeconds();
            final int occurrences = booking.getOccurrences(0);

            // 1. WITH clause to insert the "parent" booking (i.e. the periodic booking)
            query.append("WITH parent_booking AS (")
                    .append(" INSERT INTO rbs.booking (resource_id, owner, booking_reason, quantity, start_date, end_date,")
                    .append(" is_periodic, periodicity, occurrences, days)")
                    .append(" VALUES (?, ?, ?, ?, ?,");
            values.add(rId)
                    .add(user.getUserId())
                    .add(booking.getBookingReason())
					.add(booking.getBookingQuantity())
                    .add(toSQLTimestamp(slot.getStartUTC()));

            query.append(" ?,");
            values.add(endDate > 0L ? toSQLTimestamp(endDate) : null); // the null value will be replaced by the last slot's
            // end date, which
            // will be computed
            final int endDateIndex = values.size() - 1;

            query.append(" ?, ?, ?,");
            values.add(true).add(booking.getPeriodicity())
                    .add(occurrences != 0 ? occurrences : null);

            // NB : Bit string type cannot be used in a preparedStatement
            query.append(" B'").append(booking.getSelectedDaysBitString()).append("') RETURNING id) ");

            // 2. Insert clause for the child bookings
            final long lastSlotEndDate = appendInsertChildBookingsQuery(query, values, rId, booking,slot, user, null);

            // Update end_date value in JsonArray values
            if (endDate <= 0L) {
                values = getValuesWithProperEndDate(values, toSQLTimestamp(lastSlotEndDate), endDateIndex);
            }

            statementsBuilder.prepared(query.toString(), values);
        }
		Sql.getInstance().transaction(statementsBuilder.build(), validResultHandler(1, handler));

	}

	/**
	 * Appends the insert child bookings query to parameter query, and the
	 * associated values to parameter values
	 *
	 * @param bookingId : used when updating a periodic booking
	 * @return Unix timestamp of the last child booking's end date
	 */
	private long appendInsertChildBookingsQuery(StringBuilder query, JsonArray values, final Object resourceId,final Booking booking,
			final Slot slot, final UserInfos user, final Object bookingId) {

		final boolean isUpdate = (bookingId != null);
		final long firstSlotStartDate = slot.getStartUTC();
		final long firstSlotEndDate = slot.getEndUTC();
		final String bookingReason = booking.getBookingReason();
		long lastSlotEndDateUTC = firstSlotEndDate;

		// 1. INSERT clause for the first child booking
		query.append(
				" INSERT INTO rbs.booking (resource_id, owner, booking_reason, quantity, start_date, end_date, parent_booking_id, status, refusal_reason)")
				.append(" VALUES(?, ?, ?, ?, ?, ?,");
		values.add(resourceId)
				.add(user.getUserId())
				.add(bookingReason)
				.add(booking.getBookingQuantity())
				.add(toSQLTimestamp(firstSlotStartDate))
				.add(toSQLTimestamp(firstSlotEndDate));

		if (isUpdate) { // Update of a periodic booking
			query.append(" ?,");
			values.add(bookingId);
		} else { // Creation of a periodic booking
			query.append("(select id from parent_booking),");
		}

		// Subquery to insert proper status : created if validation is activated. Validated otherwise

		query.append(" (SELECT CASE WHEN (r.validation IS true) THEN ? ELSE ? END")
				.append(" FROM rbs.resource AS r")
				.append(" WHERE r.id = ?), ?)");
		values.add(CREATED.status()).add(VALIDATED.status()).add(resourceId).addNull();

		// 2. Additional VALUES to insert the other child bookings
		int nbOccurences = booking.getOccurrences(-1);

		if (nbOccurences == -1) {
			nbOccurences = booking.countOccurrences(slot);
		}

		SlotIterable it = new SlotIterable(booking,slot);
		for (Slot slotIt : it) {
			query.append(", (?, ?, ?, ?, ?, ?,");
			values.add(resourceId).add(user.getUserId()).add(bookingReason).add(booking.getBookingQuantity())
					.add(toSQLTimestamp(slotIt.getStartUTC())).add(toSQLTimestamp(slotIt.getEndUTC()));

			if (isUpdate) { // Update of a periodic booking
				query.append(" ?,");
				values.add(bookingId);
			} else { // Creation of a periodic booking
				query.append("(select id from parent_booking),");
			}

			query.append(" (SELECT CASE WHEN (r.validation IS true) THEN ? ELSE ? END")
					.append(" FROM rbs.resource AS r")
					.append(" WHERE r.id = ?), ?)");
			values.add(CREATED.status()).add(VALIDATED.status()).add(resourceId).addNull();
			//
			lastSlotEndDateUTC = lastSlotEndDateUTC < slotIt.getEndUTC() ? slotIt.getEndUTC() : lastSlotEndDateUTC;
		}
		query.append(" RETURNING id, status");

		return lastSlotEndDateUTC;
	}

	/**
	 * @param values
	 * @param lastSlotEndDate
	 * @param endDateIndex
	 * @return new fr.wseduc.webutils.collections.JsonArray, with proper end date
	 */
	private JsonArray getValuesWithProperEndDate(JsonArray values, String lastSlotEndDate, int endDateIndex) {
		JsonArray newValues = new fr.wseduc.webutils.collections.JsonArray();
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
	public void updateBooking(final String resourceId, final Booking booking,
			final Handler<Either<String, JsonObject>> handler) {

		SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
		Object rId = parseId(resourceId);
		Object bId = parseId(booking.getBookingId());
		Slot slot = booking.getSlots().get(0);
		// Lock query to avoid race condition
		statementsBuilder.raw(LOCK_BOOKING_QUERY);

		// Update query
		JsonArray values = new fr.wseduc.webutils.collections.JsonArray();
		StringBuilder sb = new StringBuilder();


		StringBuilder query = new StringBuilder();
		query.append("UPDATE rbs.booking").append(" SET ").append(sb).append("modified = NOW()")
				.append(", booking_reason = ?")
				.append(", quantity = ?")
				.append(", start_date = ?")
				.append(", end_date = ?" );
		values.add(booking.getBookingReason());
		values.add(booking.getBookingQuantity());
		values.add(toSQLTimestamp(slot.getStartUTC()));
		values.add(toSQLTimestamp(slot.getEndUTC()));

		// If validation is activated, the booking status is updated to "created" (the
		// booking must be validated anew).
		// Otherwise, it is updated to "validated".
		query.append(", status = (SELECT CASE ").append(" WHEN (t.validation IS true) THEN ?").append(" ELSE ?")
				.append(" END").append(" FROM rbs.resource_type AS t")
				.append(" INNER JOIN rbs.resource AS r ON r.type_id = t.id")
				.append(" INNER JOIN rbs.booking AS b on b.resource_id = r.id").append(" WHERE b.id = ?)");
		values.add(CREATED.status()).add(VALIDATED.status()).add(bId);

		query.append(" WHERE id = ?").append(" AND resource_id = ? RETURNING *");
		values.add(bId).add(rId);

		statementsBuilder.prepared(query.toString(), values);

		// Send queries to eventbus
		Sql.getInstance().transaction(statementsBuilder.build(), validUniqueResultHandler(1, handler));
	}

	@Override
	public void updatePeriodicBooking(final String resourceId, final Booking booking, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler) {

		final long endDate = booking.getPeriodicEndDateAsUTCSeconds();
		final int occurrences = booking.getOccurrences(0);
		final Slot slot = booking.getSlots().get(0);
		SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
		Object rId = parseId(resourceId);
		Object bId = parseId(booking.getBookingId());

		// 1. Lock query to avoid race condition
		statementsBuilder.raw(LOCK_BOOKING_QUERY);

		// 2. Update parent booking
		StringBuilder parentQuery = new StringBuilder();
		JsonArray parentValues = new fr.wseduc.webutils.collections.JsonArray();
		parentQuery.append("UPDATE rbs.booking").append(" SET booking_reason = ?, quantity = ?, start_date = ?, end_date = ?,");
		parentValues.add(booking.getBookingReason()).add(booking.getBookingQuantity()).add(toSQLTimestamp(slot.getStartUTC()))
				.add(endDate > 0L ? toSQLTimestamp(endDate) : null); // the null value will be replaced by the last slot's end date
		final int endDateIndex = parentValues.size() - 1;

		parentQuery.append(" periodicity = ?, occurrences = ?, modified = NOW(),");
		parentValues.add(booking.getPeriodicity()).add(occurrences != 0 ? occurrences : null);

		parentQuery.append(" days = B'").append(booking.getSelectedDaysBitString()).append("' WHERE resource_id = ?")
				.append(" AND id = ?").append(" AND is_periodic = ?");
		parentValues.add(rId).add(bId).add(true);

		// 3. Delete child bookings
		String deleteQuery = "DELETE FROM rbs.booking WHERE parent_booking_id = ?";

		// 4. Create new child bookings
		StringBuilder insertQuery = new StringBuilder();
		JsonArray insertValues = new fr.wseduc.webutils.collections.JsonArray();
		final long lastSlotEndDate = appendInsertChildBookingsQuery(insertQuery, insertValues, rId, booking,booking.getSlots().get(0), user, bId);

		// Update end_date value in JsonArray parentValues
		if (endDate <= 0L) {
			parentValues = getValuesWithProperEndDate(parentValues, toSQLTimestamp(lastSlotEndDate), endDateIndex);
		}

		// Add queries to SqlStatementsBuilder
		statementsBuilder.prepared(parentQuery.toString(), parentValues);
		statementsBuilder.prepared(deleteQuery, new fr.wseduc.webutils.collections.JsonArray().add(bId));
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
		JsonArray deleteValues = new fr.wseduc.webutils.collections.JsonArray();
		deleteQuery.append("DELETE FROM rbs.booking")
				.append(" WHERE parent_booking_id = ?")
				.append(" AND start_date >= to_timestamp(?)");
		deleteValues.add(bId);
		deleteValues.add(startDate.getTime() / 1000);


		// 3. Update parent booking with last end date slot
		StringBuilder parentQuery = new StringBuilder();
		JsonArray parentValues = new fr.wseduc.webutils.collections.JsonArray();
		parentQuery.append("UPDATE rbs.booking")
				.append(" SET end_date = (SELECT CASE WHEN MAX(end_date) IS NULL THEN (SELECT end_date FROM rbs.booking WHERE id = ?) ELSE MAX(end_date) END FROM rbs.booking WHERE parent_booking_id = ?)")
				.append(" , modified = NOW()")
				.append(" WHERE id = ?");
		parentValues.add(bId);
		parentValues.add(bId);
		parentValues.add(bId);
		// 4. Purge parent bookings
		StringBuilder deleteParentQuery = new StringBuilder();
		deleteParentQuery.append("DELETE FROM rbs.booking B1")
				.append(" WHERE B1.is_periodic = true")
				.append(" AND NOT EXISTS (SELECT * FROM rbs.booking B2 WHERE B2.parent_booking_id = B1.id)");

		// Add queries to SqlStatementsBuilder
		statementsBuilder.prepared(deleteQuery.toString(), deleteValues);
		statementsBuilder.prepared(parentQuery.toString(), parentValues);
		statementsBuilder.raw(deleteParentQuery.toString());

		// Send queries to event bus
		Sql.getInstance().transaction(statementsBuilder.build(), validResultHandler(2, handler));
	}

	@Override
	public void getBookingsForExport(final ExportRequest exportRequest, final Handler<Either<String, List<ExportBooking>>> handler) {
		final String startDate = exportRequest.getStartDate();
		final String endDate = exportRequest.getEndDate();
		final UserInfos user = exportRequest.getUserInfos();
		final List<String> groupsAndUserIds = getUserIdAndGroupIds(user);

		StringBuilder query = new StringBuilder();
		JsonArray values = new fr.wseduc.webutils.collections.JsonArray();

		// find all booking without periodic booking parents
		query.append("SELECT b.").append(ExportBooking.BOOKING_ID);
		query.append(", b.").append(ExportBooking.BOOKING_OWNER_ID);
		query.append(", b.").append(ExportBooking.BOOKING_START_DATE);
		query.append(", b.").append(ExportBooking.BOOKING_END_DATE);
		query.append(", b.").append(ExportBooking.BOOKING_REASON);
		query.append(", b.").append(ExportBooking.BOOKING_MODERATOR_ID);
		query.append(", b.").append(ExportBooking.RESOURCE_ID);
		query.append(", b.").append(ExportBooking.QUANTITY);
		query.append(", t.").append(ExportBooking.SCHOOL_ID);
		query.append(", t.id AS ").append(ExportBooking.RESOURCE_TYPE_ID);
		query.append(", t.color AS ").append(ExportBooking.RESOURCE_TYPE_COLOR);
		query.append(", r.name AS ").append(ExportBooking.RESOURCE_NAME);
		query.append(", r.color AS ").append(ExportBooking.RESOURCE_COLOR);
		query.append(", u.username AS ").append(ExportBooking.BOOKING_OWNER_NAME);
		query.append(", m.username AS ").append(ExportBooking.BOOKING_MODERATOR_NAME);
		query.append(" FROM rbs.booking AS b")
				.append(" LEFT JOIN rbs.resource AS r ON r.id = b.resource_id")
				.append(" LEFT JOIN rbs.resource_type AS t ON r.type_id = t.id")
				.append(" LEFT JOIN rbs.resource_type_shares AS rs ON rs.resource_id = r.type_id")
				.append(" LEFT JOIN rbs.users AS u ON u.id = b.owner")
				.append(" LEFT JOIN rbs.users AS m on b.moderator_id = m.id")
				.append(" WHERE b.status = ?") // only validated
				.append(" AND ((b.is_periodic=FALSE AND b.start_date::date >= ?::date AND b.end_date::date < (?::date + interval '1 day')) ")
				.append(" OR (b.occurrences IS NULL AND b.start_date::date >= ?::date AND b.end_date::date < (?::date + interval '1 day'))")
				// this part is for reservations across 2 weeks
				.append(" OR (b.is_periodic=FALSE AND b.start_date::date <= ?::date AND b.end_date::date > (?::date + interval '1 day'))")
				.append(" OR (b.is_periodic=FALSE AND b.start_date::date >= ?::date AND b.start_date::date < ?::date)")
				.append(" OR (b.is_periodic=FALSE AND b.end_date::date >= (?::date + interval '1 day') AND b.end_date::date < (?::date + interval '1 day')))")
				.append(" AND (rs.member_id IN ")
				//
				.append(Sql.listPrepared(groupsAndUserIds.toArray()))
				.append(" OR t.owner = ?");
		values.add(VALIDATED.status());
		values.add(startDate);
		values.add(endDate);
		values.add(startDate);
		values.add(endDate);
		values.add(startDate);
		values.add(endDate);
		values.add(startDate);
		values.add(endDate);
		values.add(startDate);
		values.add(endDate);

		for (String groupOruser : groupsAndUserIds) {
			values.add(groupOruser);
		}
		values.add(user.getUserId());

		// A local administrator of a given school can see all resources of the school's types, even if he is not owner or manager of these types or resources
		List<String> scope = getLocalAdminScope(user);
		if (scope != null && !scope.isEmpty()) {
			query.append(" OR t.school_id IN ").append(Sql.listPrepared(scope.toArray()));
			for (String schoolId : scope) {
				values.add(schoolId);
			}
		}

		// Add user restriction on resources if any
		final List<Long> resourceIds = exportRequest.getResourceIds();
		if (!resourceIds.isEmpty()) {
			query.append(") AND (r.id IN ")
					.append(Sql.listPrepared(resourceIds.toArray()));
			for (Long resourceId : resourceIds) {
				values.add(resourceId);
			}
		}

		query.append(") GROUP BY t.id, b.id, r.id, u.username, m.username ORDER BY b.start_date, b.end_date");

		Sql.getInstance().prepared(query.toString(), values, event -> {
			final Either<String, JsonArray> ei = validResult(event);
			if (ei.isRight()) {
				final List<ExportBooking> exportBookings = new ArrayList<>();
				final JsonArray jsonBookingResult = ei.right().getValue();
				for (final Object o : jsonBookingResult) {
					if (!(o instanceof JsonObject)) {
						continue;
					} else {
						// keep-it without understanding business rule here...
						final JsonObject jo = (JsonObject) o;
						Number parentBookingId = jo.getInteger("parent_booking_id");
						if (parentBookingId != null) {
							exportBookings.add(new ExportBooking(jo));
						} else if (jo.getInteger("occurrences") == null) {
							if (isSearchOverlapBookingDates(jo, startDate, endDate)) {
								exportBookings.add(new ExportBooking(jo));
							}
						} else {
							exportBookings.add(new ExportBooking(jo));
						}
					}
				}
				handler.handle(new Right<>(exportBookings));
			} else {
				String value = ei.left().getValue();
				handler.handle(new Either.Left<>(value));
			}
		});
	}

	private boolean searchDatesOverlapBookingDates(Date searchStartDate, Date searchEndDate, Date bookingStartDate, Date bookingEndDate) {
		return DateUtils.isBetween(searchStartDate, bookingStartDate, bookingEndDate) ||
				DateUtils.isBetween(searchEndDate, bookingStartDate, bookingEndDate) ||
				(DateUtils.isBetween(bookingStartDate, searchStartDate, searchEndDate) &&
						DateUtils.isBetween(bookingEndDate, searchStartDate, searchEndDate));
	}


	@Override
	public void processBooking(final String resourceId, final String bookingId, final int newStatus,
			final JsonObject data, final UserInfos user, final Handler<Either<String, JsonArray>> handler) {

		SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
		Object rId = parseId(resourceId);
		Object bId = parseId(bookingId);

		// 1. Upsert current user
		statementsBuilder.prepared(UPSERT_USER_QUERY, new JsonArray().add(user.getUserId()).add(user.getUsername()));

		// 2. Lock query to avoid race condition
		statementsBuilder.raw(LOCK_BOOKING_QUERY);

		// 3. Query to validate or refuse booking
		StringBuilder sb = new StringBuilder();
		JsonArray processValues = new JsonArray();
		for (String attr : data.fieldNames()) {
			sb.append(attr).append(" = ?, ");
			processValues.add(data.getValue(attr));
		}

		StringBuilder processQuery = new StringBuilder();
		processQuery.append("UPDATE rbs.booking").append(" SET ").append(sb).append("modified = NOW() ")
				.append("WHERE id = ?").append(" AND resource_id = ? ");
		processValues.add(bId).add(rId);

		StringBuilder returningClause = new StringBuilder(" RETURNING id, status, owner, ")
				.append(" to_char(start_date, '").append(DATE_FORMAT).append("') AS start_date,")
				.append(" to_char(end_date, '").append(DATE_FORMAT).append("') AS end_date");

		processQuery.append(returningClause);
		statementsBuilder.prepared(processQuery.toString(), processValues);

		// Send queries to event bus
		Sql.getInstance().transaction(statementsBuilder.build(), validResultsHandler(handler));
	}

	@Override
	public void listUserBookings(final UserInfos user, final Handler<Either<String, JsonArray>> handler) {
		StringBuilder query = new StringBuilder();
		query.append("SELECT b.*, u.username AS owner_name, m.username AS moderator_name")
				.append(" FROM rbs.booking AS b").append(" INNER JOIN rbs.users AS u ON b.owner = u.id")
				.append(" LEFT JOIN rbs.users AS m on b.moderator_id = m.id").append(" WHERE b.owner = ?")
				.append(" ORDER BY b.start_date, b.end_date");

		JsonArray values = new JsonArray().add(user.getUserId());

		Sql.getInstance().prepared(query.toString(), values, validResultHandler(handler));
	}

	@Override
	public void listAllBookings(final UserInfos user, final List<String> groupsAndUserIds,
			final Handler<Either<String, JsonArray>> handler) {
		StringBuilder query = new StringBuilder();
		JsonArray values = new fr.wseduc.webutils.collections.JsonArray();

		query.append("SELECT b.*, u.username AS owner_name, m.username AS moderator_name")
				.append(" FROM rbs.booking AS b").append(" LEFT JOIN rbs.resource AS r ON r.id = b.resource_id")
				.append(" LEFT JOIN rbs.resource_type AS t ON r.type_id = t.id")
				.append(" LEFT JOIN rbs.resource_type_shares AS rs ON rs.resource_id = r.type_id")
				.append(" LEFT JOIN rbs.users AS u ON u.id = b.owner")
				.append(" LEFT JOIN rbs.users AS m on b.moderator_id = m.id").append(" WHERE rs.member_id IN ")
				.append(Sql.listPrepared(groupsAndUserIds.toArray())).append(" OR t.owner = ?");
		for (String groupOruser : groupsAndUserIds) {
			values.add(groupOruser);
		}
		values.add(user.getUserId());

		// A local administrator of a given school can see all resources of the school's
		// types, even if he is not owner or manager of these types or resources
		List<String> scope = getLocalAdminScope(user);
		if (scope != null && !scope.isEmpty()) {
			query.append(" OR t.school_id IN ").append(Sql.listPrepared(scope.toArray()));
			for (String schoolId : scope) {
				values.add(schoolId);
			}
		}

		query.append(" GROUP BY b.id, u.username, m.username ORDER BY b.start_date, b.end_date");

		Sql.getInstance().prepared(query.toString(), values, validResultHandler(handler));
	}

	private String listBaseQuery(boolean withShares, String afterWhere){
		final StringBuilder baseQuery = new StringBuilder();
		baseQuery.append("SELECT b.*, u.username AS owner_name, m.username AS moderator_name FROM rbs.booking AS b ")
				.append(" LEFT JOIN rbs.resource AS r ON r.id = b.resource_id ")
				.append(" LEFT JOIN rbs.resource_type AS t ON r.type_id = t.id ")
				.append(withShares ? " LEFT JOIN rbs.resource_type_shares AS rs ON rs.resource_id = r.type_id " : "")
				.append(" LEFT JOIN rbs.users AS u ON u.id = b.owner ")
				.append(" LEFT JOIN rbs.users AS m on b.moderator_id = m.id ")
				.append(" WHERE ((b.is_periodic=FALSE AND b.start_date::date >= ?::date AND b.end_date::date < ?::date) ")
				.append(" OR (b.occurrences IS NULL AND b.start_date::date >= ?::date AND b.end_date::date < ?::date) ")
				// this part is for reservations across 2 weeks
				.append(" OR (b.is_periodic=FALSE AND b.start_date::date <= ?::date AND b.end_date::date > ?::date) ")
				.append(" OR (b.is_periodic=FALSE AND b.start_date::date >= ?::date AND b.start_date::date < ?::date) ")
				.append(" OR (b.is_periodic=FALSE AND b.end_date::date >= ?::date AND b.end_date::date < ?::date)) ")
				.append(afterWhere)
				.append(" GROUP BY b.id, u.username, m.username ");
		return baseQuery.toString();
	}

	@Override
	public void listAllBookingsByDates(final UserInfos user, final List<String> groupsAndUserIds,
			final String startDate, final String endDate, final Handler<Either<String, JsonArray>> handler) {
		//=== base values
		final JsonArray baseValue = new JsonArray();
		baseValue.add(startDate);
		baseValue.add(endDate);
		baseValue.add(startDate);
		baseValue.add(endDate);
		baseValue.add(startDate);
		baseValue.add(endDate);
		baseValue.add(startDate);
		baseValue.add(endDate);
		baseValue.add(startDate);
		baseValue.add(endDate);
		// A local administrator of a given school can see all resources of the school's
		// types, even if he is not owner or manager of these types or resources
		final List<String> scope = getLocalAdminScope(user);
		final boolean isLocalAdmin = scope != null && !scope.isEmpty();
		//===query find all booking without periodic booking
		final StringBuilder query = new StringBuilder();
		query.append("SELECT * FROM ( ");
		query.append(listBaseQuery(true, " AND rs.member_id IN " + Sql.listPrepared(groupsAndUserIds.toArray())));
		query.append(" UNION ").append(listBaseQuery(false, " AND t.owner = ? "));
		if(isLocalAdmin){
			query.append(" UNION ").append(listBaseQuery(false," AND t.school_id IN " + Sql.listPrepared(scope.toArray())));
		}
		query.append(" ) AS tmp ORDER BY tmp.start_date, tmp.end_date ");
		//===values
		final JsonArray values = new JsonArray();
		// by member
		values.addAll(baseValue);
		for (String groupOruser : groupsAndUserIds) {
			values.add(groupOruser);
		}
		// by owner
		values.addAll(baseValue).add(user.getUserId());
		// by school
		if (isLocalAdmin) {
			values.addAll(baseValue);
			for (String schoolId : scope) {
				values.add(schoolId);
			}
		}

		Sql.getInstance().prepared(query.toString(), values, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				final Either<String, JsonArray> ei = validResult(event);
				if (ei.isRight()) {
					final Set<Number> setIdsPeriodicBooking = new HashSet<Number>();
					final JsonArray jsonBookingResult = ei.right().getValue();
					final JsonArray jsonAllBookingResult = new fr.wseduc.webutils.collections.JsonArray();
					for (final Object o : jsonBookingResult) {
						if (!(o instanceof JsonObject)) {
							continue;
						} else {
							final JsonObject jo = (JsonObject) o;
							if (jo.getLong("parent_booking_id") != null) {
								setIdsPeriodicBooking.add(jo.getLong("parent_booking_id"));
								jsonAllBookingResult.add(jo);
							} else if (jo.getValue("occurrences") == null) {
								try {
									final Date currentStartDate = DateUtils
											.parseTimestampWithoutTimezone(jo.getString("start_date"));
									final Date currentEndDate = DateUtils
											.parseTimestampWithoutTimezone(jo.getString("end_date"));
									final List<String> startSearched = StringUtils.split(startDate, "-");
									final Date dateStartSearched = DateUtils.create(
											Integer.parseInt(startSearched.get(0)),
											Integer.parseInt(startSearched.get(1)) - 1,
											Integer.parseInt(startSearched.get(2)));
									final List<String> endSearched = StringUtils.split(endDate, "-");
									final Date dateEndSearched = DateUtils.create(Integer.parseInt(endSearched.get(0)),
											Integer.parseInt(endSearched.get(1)) - 1,
											Integer.parseInt(endSearched.get(2)));
									if (DateUtils.isBetween(dateStartSearched, currentStartDate, currentEndDate)
											|| DateUtils.isBetween(dateEndSearched, currentStartDate, currentEndDate)
											|| (DateUtils.isBetween(currentStartDate, dateStartSearched,
													dateEndSearched)
													&& DateUtils.isBetween(currentEndDate, dateStartSearched,
															dateEndSearched))) {
										jsonAllBookingResult.add(jo);
									}
								} catch (ParseException e) {
									log.error("Can't parse date form RBS DB", e);
								}
							} else {
								jsonAllBookingResult.add(jo);
							}
						}
					}
					if (!setIdsPeriodicBooking.isEmpty()) {
						// find periodicBooking according to ids of sons
						final StringBuilder queryPeriodicBooking = new StringBuilder();
						queryPeriodicBooking
								.append("SELECT b.*, u.username AS owner_name, m.username AS moderator_name")
								.append(" FROM rbs.booking AS b").append(" LEFT JOIN rbs.users AS u ON u.id = b.owner")
								.append(" LEFT JOIN rbs.users AS m on b.moderator_id = m.id").append(" WHERE b.id IN ")
								.append(Sql.listPrepared(setIdsPeriodicBooking.toArray()));
						final JsonArray values = new fr.wseduc.webutils.collections.JsonArray(
								new ArrayList<>(setIdsPeriodicBooking));

						Sql.getInstance().prepared(queryPeriodicBooking.toString(), values,
								event1 -> {
									final Either<String, JsonArray> ei1 = validResult(event1);
									if (ei1.isRight()) {
										final JsonArray jsonBookingPeriodicResult = ei1.right().getValue();

										if (jsonBookingPeriodicResult.size() != 0) {
											for (final Object o : jsonBookingPeriodicResult) {
												jsonAllBookingResult.add(o);
											}
										}
										handler.handle(new Right<>(jsonAllBookingResult));
									} else {
										handler.handle(ei1);
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

	private boolean isSearchOverlapBookingDates(JsonObject booking, String startDate, String endDate) {
		boolean overlapBookingDates = false;
		try {
			final Date currentStartDate = DateUtils.parseTimestampWithoutTimezone(booking.getString("start_date"));
			final Date currentEndDate = DateUtils.parseTimestampWithoutTimezone(booking.getString("end_date"));
			final List<String> startSearched = StringUtils.split(startDate, "-");
			final Date dateStartSearched = DateUtils.create(Integer.parseInt(startSearched.get(0)),
					Integer.parseInt(startSearched.get(1)) - 1, Integer.parseInt(startSearched.get(2)));
			final List<String> endSearched = StringUtils.split(endDate, "-");
			final Date dateEndSearched = DateUtils.create(Integer.parseInt(endSearched.get(0)),
					Integer.parseInt(endSearched.get(1)) - 1, Integer.parseInt(endSearched.get(2)) + 1);
			overlapBookingDates = searchDatesOverlapBookingDates(dateStartSearched, dateEndSearched, currentStartDate, currentEndDate);

		} catch (ParseException e) {
			log.error("Can't parse date form RBS DB", e);
		}
		return overlapBookingDates;
	}

	@Override
	public void listFullSlotsBooking(final String bookingId, final Handler<Either<String, JsonArray>> handler) {
		StringBuilder query = new StringBuilder();
		JsonArray values = new fr.wseduc.webutils.collections.JsonArray();

		// find all booking without periodic booking
		query.append("SELECT b.*, u.username AS owner_name, m.username AS moderator_name")
				.append(" FROM rbs.booking AS b").append(" LEFT JOIN rbs.users AS u ON u.id = b.owner")
				.append(" LEFT JOIN rbs.users AS m on b.moderator_id = m.id").append(" WHERE b.parent_booking_id=?");
		values.add(parseId(bookingId));

		query.append(" ORDER BY b.start_date, b.end_date");

		Sql.getInstance().prepared(query.toString(), values, validResultHandler(handler));
	}

	@Override
	public void listBookingsByResource(final String resourceId, final Handler<Either<String, JsonArray>> handler) {
		StringBuilder query = new StringBuilder();
		query.append("SELECT b.*, u.username AS owner_name, m.username AS moderator_name")
				.append(" FROM rbs.booking AS b").append(" INNER JOIN rbs.users AS u ON b.owner = u.id")
				.append(" LEFT JOIN rbs.users AS m on b.moderator_id = m.id").append(" WHERE b.resource_id = ?")
				.append(" ORDER BY b.start_date, b.end_date");

		JsonArray values = new fr.wseduc.webutils.collections.JsonArray();
		values.add(parseId(resourceId));

		Sql.getInstance().prepared(query.toString(), values, validResultHandler(handler));
	}

	@Override
	public void listUnprocessedBookings(final List<String> groupsAndUserIds, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler) {

		// Query
		StringBuilder query = new StringBuilder();
		JsonArray values = new fr.wseduc.webutils.collections.JsonArray();

		query.append("SELECT DISTINCT b.*, u.username AS owner_name").append(" FROM rbs.booking AS b")
				.append(" INNER JOIN rbs.users AS u ON b.owner = u.id")
				.append(" INNER JOIN rbs.resource AS r ON b.resource_id = r.id")
				.append(" INNER JOIN rbs.resource_type AS t ON t.id = r.type_id")
				.append(" LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
				.append(" LEFT JOIN rbs.resource_shares AS rs ON r.id = rs.resource_id");

		// Get child bookings with status "created" and parent bookings that have at
		// least one child booking with status "created"
		query.append(" WHERE (b.status = ?").append(" OR (b.is_periodic = ? AND EXISTS(")
				.append(" SELECT 1 FROM rbs.booking AS c").append(" WHERE c.parent_booking_id = b.id")
				.append(" AND c.status = ?)))");
		values.add(CREATED.status()).add(true).add(CREATED.status());

		// Restrict results to bookings visible by current user
		query.append(" AND (ts.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray()));
		for (String groupOruser : groupsAndUserIds) {
			values.add(groupOruser);
		}

		query.append(" OR rs.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray()));
		for (String groupOruser : groupsAndUserIds) {
			values.add(groupOruser);
		}

		query.append(" OR t.owner = ?");
		values.add(user.getUserId());

		query.append(" OR r.owner = ?)");
		values.add(user.getUserId());

		query.append(" ORDER BY b.start_date, b.end_date;");

		// Send query to event bus
		Sql.getInstance().prepared(query.toString(), values, validResultHandler(handler));
	}

	/**
	 * Get ids of groups and users that are moderators. user.getUserId() is
	 * excluded, because the booking owner does not need to be notified.
	 */
	@Override
	public void getModeratorsIds(final String bookingId, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler) {

		StringBuilder query = new StringBuilder();
		JsonArray values = new fr.wseduc.webutils.collections.JsonArray();
		Object bId = parseId(bookingId);

		// Resource owner is a moderator
		query.append("SELECT DISTINCT m.user_id, m.group_id FROM rbs.booking AS b")
				.append(" INNER JOIN rbs.resource AS r on r.id = b.resource_id")
				.append(" INNER JOIN rbs.members AS m on r.owner = m.id").append(" WHERE b.id = ? AND r.owner != ?");
		values.add(bId).add(user.getUserId());

		// Type owner is a moderator
		query.append(" UNION").append(" SELECT DISTINCT m.user_id, m.group_id FROM rbs.booking AS b")
				.append(" INNER JOIN rbs.resource AS r on r.id = b.resource_id")
				.append(" INNER JOIN rbs.resource_type AS t on t.id = r.type_id")
				.append(" INNER JOIN rbs.members AS m on t.owner = m.id ").append(" WHERE b.id = ? AND t.owner != ?");
		values.add(bId).add(user.getUserId());

		// Users with resource right "processBooking" are moderators
		query.append(" UNION").append(" SELECT DISTINCT m.user_id, m.group_id FROM rbs.booking AS b")
				.append(" INNER JOIN rbs.resource AS r on r.id = b.resource_id")
				.append(" INNER JOIN rbs.resource_shares AS rs ON r.id = rs.resource_id")
				.append(" INNER JOIN rbs.members AS m on rs.member_id = m.id")
				.append(" WHERE b.id = ? AND rs.member_id != ?")
				.append(" AND rs.action = 'net-atos-entng-rbs-controllers-BookingController|processBooking'");
		values.add(bId).add(user.getUserId());

		query.append(" UNION").append(" SELECT DISTINCT m.user_id, m.group_id FROM rbs.booking AS b")
				.append(" INNER JOIN rbs.resource AS r on r.id = b.resource_id")
				.append(" INNER JOIN rbs.resource_type AS t on t.id = r.type_id")
				.append(" INNER JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
				.append(" INNER JOIN rbs.members AS m on ts.member_id = m.id")
				.append(" WHERE b.id = ? AND ts.member_id != ?")
				.append(" AND ts.action = 'net-atos-entng-rbs-controllers-BookingController|processBooking'");
		values.add(bId).add(user.getUserId());

		Sql.getInstance().prepared(query.toString(), values, validResultHandler(handler));
	}

	@Override
	public void getResourceName(final String bookingId, final Handler<Either<String, JsonObject>> handler) {
		this.getResourceName(bookingId, handler, false);
	}

	@Override
	public void getBookingWithResourceName(final String bookingId, final Handler<Either<String, JsonObject>> handler) {
		this.getResourceName(bookingId, handler, true);
	}

	private void getResourceName(final String bookingId, final Handler<Either<String, JsonObject>> handler,
			boolean withBooking) {
		StringBuilder query = new StringBuilder("SELECT r.name AS resource_name");
		if (withBooking) {
			query.append(", b.owner, b.is_periodic,").append("to_char(b.start_date, '").append(DATE_FORMAT)
					.append("') as start_date,").append("to_char(b.end_date, '").append(DATE_FORMAT)
					.append("') as end_date").append(", b.parent_booking_id");
		}
		query.append(" FROM rbs.resource AS r").append(" INNER JOIN rbs.booking AS b on r.id = b.resource_id")
				.append(" WHERE b.id = ?");

		Sql.getInstance().prepared(query.toString(),
				new fr.wseduc.webutils.collections.JsonArray().add(parseId(bookingId)),
				validUniqueResultHandler(handler));
	}

	@Override
	public void getParentBooking(final String bookingId, final Handler<Either<String, JsonObject>> handler) {
		StringBuilder query = new StringBuilder();

		query.append("SELECT DISTINCT p.id, r.name as resource_name, ").append("to_char(p.start_date, '")
				.append(DATE_FORMAT).append("') as start_date,").append("to_char(p.end_date, '").append(DATE_FORMAT)
				.append("') as end_date").append(" FROM rbs.booking AS b")
				.append(" INNER JOIN rbs.booking AS p ON b.parent_booking_id = p.id")
				.append(" INNER JOIN rbs.resource AS r ON b.resource_id = r.id").append(" WHERE b.id = ?");

		Sql.getInstance().prepared(query.toString(),
				new fr.wseduc.webutils.collections.JsonArray().add(parseId(bookingId)),
				validUniqueResultHandler(handler));
	}

	public void getBooking(final String bookingId, final Handler<Either<String, JsonObject>> handler) {
		this.retrieve(bookingId, handler);
	}

	public Future<JsonObject> getBooking(final String bookingId) {
		Promise<JsonObject> promise = Promise.promise();
		retrieve(bookingId, FutureHelper.handlerJsonObject(promise));
		return promise.future();
	}

	public Future<JsonObject> deleteBooking(String id, UserInfos user) {
		Promise<JsonObject> promise = Promise.promise();
		delete(id, user, FutureHelper.handlerJsonObject(promise));
		return promise.future();
	}

	/**
	 * Iterates through an array of bookings to save them
	 * The index of the resource ids must be the same as the corresponding booking
	 * @param bookingIds {@link List<Integer>} the array of resource ids
	 * @param user {@link UserInfos} the user
	 * @return {@link Future<JsonObject>} an array of saved bookings
	 */
	@Override
	public Future<List<JsonObject>> checkRightsAndDeleteBookings(final List<Integer> bookingIds, final UserInfos user) {
		Promise<List<JsonObject>> promise = Promise.promise();

		List<Future<JsonObject>> bookingsFuture = new ArrayList<>();

		for (Integer bookingId : bookingIds) {
			String bookingStringId = String.valueOf(bookingId);
			bookingsFuture.add(checkRightsAndDeleteBooking(bookingStringId, user));
		}

		FutureHelper.all(bookingsFuture)
			.onSuccess((res) -> promise.complete(bookingsFuture.stream().map(Future::result).collect(Collectors.toList())))
			.onFailure((err) -> {
				String message = String.format("[Rbs@%s::createBookings]: an error has occurred while saving bookings: %s",
						this.getClass().getSimpleName(), err.getMessage());
				log.error(message);
				promise.fail(err.getMessage());
			});


		return promise.future();
	}


	/**
	 * This methods checks if the user has the right to delete the booking and if so deletes it
	 * All cases lead to completed promises in order for checkRightsAndDeleteBookings() to gather all the booking deletion info
	 * and send it back to Calendar Module using event bus response.
	 * Example of completed promise :
	 * {
	 *     "id": "000",
	 *     "status": "error",
	 *     "message": "Unauthorized"
	 * }
	 * @param bookingStringId {@link String} the id of the booking
	 * @param user {@link UserInfos} the user
	 * @return {@link Future<JsonObject>} (see example above)
	 */
	Future<JsonObject> checkRightsAndDeleteBooking(String bookingStringId, UserInfos user) {
		Promise<JsonObject> promise = Promise.promise();
		this.getBooking(bookingStringId)
				.compose(booking -> {
					String resourceId = booking.getLong(Field.RESOURCE_ID).toString();
					String method = BookingController.class.getName() + "|deleteBooking";
					Binding binding = new Binding(HttpMethod.DELETE, Pattern.compile(""), method, ActionType.RESOURCE);
					return new TypeAndResourceAppendPolicy().authorize(resourceId, bookingStringId, binding, user);
				})
				.onSuccess(hasRight -> {
					if (Boolean.TRUE.equals(hasRight)) {
						this.deleteBooking(bookingStringId, user)
								.onSuccess(res -> {
									promise.complete(new JsonObject()
											.put(Field.ID, bookingStringId)
											.put(Field.STATUS, Field.OK)
											.put(Field.MESSAGE, "Successfully deleted")
									);
								})
								.onFailure(err -> {
									promise.complete(new JsonObject()
											.put(Field.ID, bookingStringId)
											.put(Field.STATUS, Field.ERROR)
											.put(Field.MESSAGE, err.getMessage())
									);
								});
					} else {
						promise.complete(new JsonObject()
								.put(Field.ID, bookingStringId)
								.put(Field.STATUS, Field.ERROR)
								.put(Field.MESSAGE, Field.UNAUTHORIZED)
						);
					}
				})
				.onFailure(error -> {
					String message = String.format("[Rbs@%s::checkRightsAndDeleteBooking]: an error has occurred while deleting booking: %s",
							this.getClass().getSimpleName(), error.getMessage());
					log.error(message);
					promise.complete(new JsonObject()
							.put(Field.ID, bookingStringId)
							.put(Field.STATUS, Field.ERROR)
							.put(Field.MESSAGE, error.getMessage())
					);
				});

		return promise.future();
	}

}
