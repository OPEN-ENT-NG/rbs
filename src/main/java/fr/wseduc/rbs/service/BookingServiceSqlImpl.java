package fr.wseduc.rbs.service;

import static fr.wseduc.rbs.BookingStatus.CREATED;
import static fr.wseduc.rbs.BookingStatus.REFUSED;
import static fr.wseduc.rbs.BookingStatus.VALIDATED;
import static org.entcore.common.sql.SqlResult.*;

import java.util.List;

import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlStatementsBuilder;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public class BookingServiceSqlImpl extends SqlCrudService implements BookingService {

	private final static String LOCK_BOOKING_QUERY = "LOCK TABLE rbs.booking IN SHARE ROW EXCLUSIVE MODE;";
	private final static String UPSERT_USER_QUERY = "SELECT rbs.merge_users(?,?)";

	public BookingServiceSqlImpl() {
		super("rbs", "booking");
	}

	@Override
	public void createBooking(final Object resourceId, final JsonObject data, final UserInfos user,
			final Handler<Either<String, JsonObject>> handler) {

		SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();

		// Upsert current user
		statementsBuilder.prepared(UPSERT_USER_QUERY,
				new JsonArray().add(user.getUserId()).add(user.getUsername()));

		// Lock query to avoid race condition
		statementsBuilder.raw(LOCK_BOOKING_QUERY);

		// Insert query
		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();
		Object newStartDate = data.getValue("start_date");
		Object newEndDate = data.getValue("end_date");

		query.append("INSERT INTO rbs.booking")
				.append("(resource_id, owner, booking_reason, status, start_date, end_date)")
				.append(" SELECT  ?, ?, ?,");
		values.add(resourceId)
				.add(user.getUserId())
				.add(data.getValue("booking_reason"));

		// If validation is activated, the booking is created with status "created".
		// Otherwise, it is created with status "validated".
		// TODO V2 : la reservation doit etre automatiquement validee si le demandeur est valideur
		query.append(" (SELECT CASE ")
				.append(  " WHEN (t.validation IS true) THEN ?")
				.append(  " ELSE ?")
				.append(  " END")
				.append(  " FROM rbs.resource_type AS t")
				.append(  " INNER JOIN rbs.resource AS r ON r.type_id = t.id")
				.append(  " WHERE r.id = ?),");
		values.add(CREATED.status())
				.add(VALIDATED.status())
				.add(resourceId);

		// Unix timestamps are converted into postgresql timestamps.
		query.append(" to_timestamp(?), to_timestamp(?)");
		values.add(newStartDate)
				.add(newEndDate);

		// Check that there does not exist a validated booking that overlaps the new booking.
		query.append(" WHERE NOT EXISTS (")
				.append("SELECT 1 FROM rbs.booking")
				.append(" WHERE resource_id = ?")
				.append(" AND status = ?")
				.append(" AND (")
				.append("( start_date <= to_timestamp(?) AND to_timestamp(?) < end_date )")
				.append(" OR ( start_date < to_timestamp(?) AND to_timestamp(?) <= end_date )")
				.append(" OR ( to_timestamp(?) <= start_date AND start_date < to_timestamp(?) )")
				.append(" OR ( to_timestamp(?) < end_date AND end_date <= to_timestamp(?) )")
				.append(")) RETURNING id, status;");

		values.add(resourceId)
				.add(VALIDATED.status())
				.add(newStartDate)
				.add(newStartDate)
				.add(newEndDate)
				.add(newEndDate)
				.add(newStartDate)
				.add(newEndDate)
				.add(newStartDate)
				.add(newEndDate);

		statementsBuilder.prepared(query.toString(), values);

		// Send queries to eventbus
		Sql.getInstance().transaction(statementsBuilder.build(),
				validUniqueResultHandler(2, handler));
	}

	@Override
	public void updateBooking(final Object resourceId, final Object bookingId, final JsonObject data,
			final Handler<Either<String, JsonObject>> handler) {

		// Lock query to avoid race condition
		SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
		statementsBuilder.raw(LOCK_BOOKING_QUERY);

		// Update query
		JsonArray values = new JsonArray();
		StringBuilder sb = new StringBuilder();
		for (String fieldname : data.getFieldNames()) {
			addFieldToUpdate(sb, fieldname, data, values);
		}

		StringBuilder query = new StringBuilder();
		query.append("UPDATE rbs.booking")
				.append(" SET ")
				.append(sb.toString())
				.append("modified = NOW(),");

		// If validation is activated, the booking status is updated to "created" (the booking must be validated anew).
		// Otherwise, it is updated to "validated".
		query.append(" status = (SELECT CASE ")
				.append(  " WHEN (t.validation IS true) THEN ?")
				.append(  " ELSE ?")
				.append(  " END")
				.append(  " FROM rbs.resource_type AS t")
				.append(  " INNER JOIN rbs.resource AS r ON r.type_id = t.id")
				.append(  " INNER JOIN rbs.booking AS b on b.resource_id = r.id")
				.append(  " WHERE b.id = ?)");
		values.add(CREATED.status())
				.add(VALIDATED.status())
				.add(bookingId);

		query.append(" WHERE id = ?")
		.append(" AND resource_id = ?");
		values.add(bookingId)
			.add(resourceId);


		// Check that there does not exist a validated booking that overlaps the updated booking.
		query.append(" AND NOT EXISTS (")
				.append("SELECT 1 FROM rbs.booking")
				.append(" WHERE resource_id = ?")
				.append(" AND id != ?")
				.append(" AND status = ?")
				.append(" AND (")
				.append("( start_date <= to_timestamp(?) AND to_timestamp(?) < end_date )")
				.append(" OR ( start_date < to_timestamp(?) AND to_timestamp(?) <= end_date )")
				.append(" OR ( to_timestamp(?) <= start_date AND start_date < to_timestamp(?) )")
				.append(" OR ( to_timestamp(?) < end_date AND end_date <= to_timestamp(?) )")
				.append("))")
				.append(" RETURNING id, status;");

		Object newStartDate = data.getValue("start_date");
		Object newEndDate = data.getValue("end_date");

		values.add(resourceId)
				.add(bookingId)
				.add(VALIDATED.status())
				.add(newStartDate)
				.add(newStartDate)
				.add(newEndDate)
				.add(newEndDate)
				.add(newStartDate)
				.add(newEndDate)
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
			sb.append(fieldname).append("= to_timestamp(?), ");
		} else {
			sb.append(fieldname).append("= ?, ");
		}
		values.add(object.getValue(fieldname));
	}

	@Override
	public void processBooking(final Object resourceId, final Object bookingId,
			final int newStatus, final JsonObject data,
			final UserInfos user, final Handler<Either<String, JsonArray>> handler){




		SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();

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
		processValues.add(bookingId)
			.add(resourceId);

		String returningClause = " RETURNING id, status, owner";

		if (newStatus != VALIDATED.status()) {
			processQuery.append(returningClause);
			statementsBuilder.prepared(processQuery.toString(), processValues);
		}
		else {
			// 3b. Additional clauses when validating a booking
			StringBuilder validateQuery = new StringBuilder();
			validateQuery.append("WITH validated_booking AS (")
				.append(" SELECT start_date, end_date")
				.append(" FROM rbs.booking")
				.append(" WHERE id = ?) ");
			JsonArray validateValues = new JsonArray();
			validateValues.add(bookingId);

			validateQuery.append(processQuery.toString());
			for (Object pValue : processValues) {
				validateValues.add(pValue);
			}

			// Validate if and only if there does NOT exist a concurrent validated booking
			validateQuery.append(" AND NOT EXISTS (")
					.append("SELECT 1 FROM rbs.booking")
					.append(" WHERE resource_id = ?")
					.append(" AND status = ?")
					.append(" AND (")
					.append("( start_date <= (SELECT start_date from validated_booking) AND (SELECT start_date from validated_booking) < end_date )")
					.append(" OR ( start_date < (SELECT end_date from validated_booking) AND (SELECT end_date from validated_booking) <= end_date )")
					.append(" OR ( (SELECT start_date from validated_booking) <= start_date AND start_date < (SELECT end_date from validated_booking) )")
					.append(" OR ( (SELECT start_date from validated_booking) < end_date AND end_date <= (SELECT end_date from validated_booking) )")
					.append("))");
			validateValues.add(resourceId)
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
			rbValues.add(bookingId);

			rbQuery.append(" UPDATE rbs.booking")
				.append(" SET status = ?, moderator_id = ?, refusal_reason = ?, modified = NOW() ");
			rbValues.add(REFUSED.status())
				.add(user.getUserId())
				.add("La demande concurrente n°" + bookingId + " a été validée");

			// Refuse concurrent bookings if and only if the previous query has validated the booking
			rbQuery.append(" WHERE EXISTS (")
				.append(" SELECT 1 FROM rbs.booking")
				.append(" WHERE id = ?")
				.append(" AND status = ?)");
			rbValues.add(bookingId)
				.add(VALIDATED.status());

			// Get concurrent bookings' ids that must be refused
			rbQuery.append(" AND id in (")
				.append(" SELECT id FROM rbs.booking")
				.append(" WHERE resource_id = ?")
				.append(" AND status = ?")
				.append(" AND (")
				.append("( start_date <= (SELECT start_date from validated_booking) AND (SELECT start_date from validated_booking) < end_date )")
				.append(" OR ( start_date < (SELECT end_date from validated_booking) AND (SELECT end_date from validated_booking) <= end_date )")
				.append(" OR ( (SELECT start_date from validated_booking) <= start_date AND start_date < (SELECT end_date from validated_booking) )")
				.append(" OR ( (SELECT start_date from validated_booking) < end_date AND end_date <= (SELECT end_date from validated_booking) )")
				.append("))");
			rbValues.add(resourceId)
				.add(CREATED.status());

			rbQuery.append(" RETURNING id, status, owner");

			statementsBuilder.prepared(rbQuery.toString(), rbValues);
		}

		// Send queries to event bus
		Sql.getInstance().transaction(statementsBuilder.build(), validResultsHandler(handler));
	}

	@Override
	public void listUserBookings(final UserInfos user, final Handler<Either<String, JsonArray>> handler){
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
	public void listBookingsByResource(final Object resourceId,
			final Handler<Either<String, JsonArray>> handler){
		StringBuilder query = new StringBuilder();
		query.append("SELECT b.*, u.username AS owner_name, m.username AS moderator_name")
			.append(" FROM rbs.booking AS b")
			.append(" INNER JOIN rbs.users AS u ON b.owner = u.id")
			.append(" LEFT JOIN rbs.users AS m on b.moderator_id = m.id")
			.append(" WHERE b.resource_id = ?")
			.append(" ORDER BY b.start_date, b.end_date");

		JsonArray values = new JsonArray();
		values.add(resourceId);

		Sql.getInstance().prepared(query.toString(), values,
				validResultHandler(handler));
	}

	@Override
	public void listUnprocessedBookings(final List<String> groupsAndUserIds, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler){

		// Query
		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		query.append("SELECT DISTINCT b.*, u.username AS owner_name")
				.append(" FROM rbs.booking AS b")
				.append(" INNER JOIN rbs.users AS u ON b.owner = u.id")
				.append(" INNER JOIN rbs.resource AS r ON b.resource_id = r.id")
				.append(" INNER JOIN rbs.resource_type AS t ON t.id = r.type_id")
				.append(" LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
				.append(" LEFT JOIN rbs.resource_shares AS rs ON r.id = rs.resource_id")
				.append(" WHERE b.status = ?");
		values.add(CREATED.status());

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

	@Override
	public void getModeratorsIds(final String bookingId, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler){

		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		query.append("SELECT r.owner AS member_id FROM rbs.booking AS b")
			.append(" INNER JOIN rbs.resource AS r on r.id = b.resource_id")
			.append(" WHERE b.id = ? AND r.owner != ?");
		values.add(bookingId)
			.add(user.getUserId());

		query.append(" UNION")
			.append(" SELECT t.owner AS member_id FROM rbs.booking AS b")
			.append(" INNER JOIN rbs.resource AS r on r.id = b.resource_id")
			.append(" INNER JOIN rbs.resource_type AS t on t.id = r.type_id")
			.append(" WHERE b.id = ? AND t.owner != ?");
		values.add(bookingId)
			.add(user.getUserId());

		query.append(" UNION")
			.append(" SELECT DISTINCT rs.member_id FROM rbs.booking AS b")
			.append(" INNER JOIN rbs.resource AS r on r.id = b.resource_id")
			.append(" INNER JOIN rbs.resource_shares AS rs ON r.id = rs.resource_id")
			.append(" WHERE b.id = ? AND rs.member_id != ?");
		values.add(bookingId)
			.add(user.getUserId());

		query.append(" UNION")
			.append(" SELECT DISTINCT ts.member_id FROM rbs.booking AS b")
			.append(" INNER JOIN rbs.resource AS r on r.id = b.resource_id")
			.append(" INNER JOIN rbs.resource_type AS t on t.id = r.type_id")
			.append(" INNER JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
			.append(" WHERE b.id = ? AND ts.member_id != ?");
		values.add(bookingId)
			.add(user.getUserId());

		Sql.getInstance().prepared(query.toString(), values,
				validResultHandler(handler));
	}


}
