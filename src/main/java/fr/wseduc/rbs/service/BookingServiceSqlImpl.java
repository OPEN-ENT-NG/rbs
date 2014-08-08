package fr.wseduc.rbs.service;

import static fr.wseduc.rbs.BookingStatus.CREATED;
import static fr.wseduc.rbs.BookingStatus.REFUSED;
import static fr.wseduc.rbs.BookingStatus.VALIDATED;
import static org.entcore.common.sql.SqlResult.validResultHandler;
import static org.entcore.common.sql.SqlResult.validRowsResultHandler;
import static org.entcore.common.sql.SqlResult.validUniqueResultHandler;

import java.util.List;

import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlStatementsBuilder;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public class BookingServiceSqlImpl implements BookingService {

	private final static String LOCK_BOOKING_QUERY = "LOCK TABLE rbs.booking IN SHARE ROW EXCLUSIVE MODE;";
	
	@Override
	public void createBooking(final Long resourceId, final JsonObject data, final UserInfos user,
			final Handler<Either<String, JsonObject>> handler) {

		// Lock query to avoid race condition
		SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
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
				.append(")) RETURNING id;");

		values.add(resourceId)
				.add(VALIDATED.status())
				.add(newStartDate)
				.add(newStartDate)
				.add(newEndDate)
				.add(newEndDate)
				.add(newStartDate)
				.add(newEndDate)
				.add(newStartDate)
				.add(newEndDate)
				;

		statementsBuilder.prepared(query.toString(), values);
		
		// Send queries to eventbus
		Sql.getInstance().transaction(statementsBuilder.build(), 
				validUniqueResultHandler(handler));
	}

	@Override
	public void updateBooking(final String resourceId, final String bookingId, final JsonObject data,
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
		
		query.append(" WHERE id = ?");
		values.add(bookingId);
				
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
				.append("));");

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
				validRowsResultHandler(handler));
		
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
	public void processBooking(final String resourceId, final String bookingId, 
			final int newStatus, final JsonObject data, 
			final UserInfos user, final Handler<Either<String, JsonObject>> handler){
		
		// Query to validate or refuse booking
		StringBuilder sb = new StringBuilder();
		JsonArray values = new JsonArray();
		for (String attr : data.getFieldNames()) {
			sb.append(attr).append(" = ?, ");
			values.add(data.getValue(attr));
		}
		StringBuilder query = new StringBuilder();
		query.append("UPDATE rbs.booking")
				.append(" SET ")
				.append(sb.toString())
				.append("modified = NOW() ")
				.append("WHERE id = ?;");
		values.add(bookingId);
		
		if(newStatus != VALIDATED.status()){
			Sql.getInstance().prepared(query.toString(), values, 
					validRowsResultHandler(handler));
		}
		else {
			SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
			statementsBuilder.prepared(query.toString(), values);
			
			// Query to refuse concurrent bookings, since the booking has been validated
			StringBuilder rbQuery = new StringBuilder();
			JsonArray rbValues = new JsonArray();

			// Store start and end dates of validated booking in a temporary table
			rbQuery.append("WITH validated_booking AS (")
				.append(" SELECT start_date, end_date")
				.append(" FROM rbs.booking")
				.append(" WHERE id = ?)");
			rbValues.add(bookingId);
			
			rbQuery.append(" UPDATE rbs.booking")
				.append(" SET status = ?, modified = NOW() ");
			rbValues.add(REFUSED.status());
			
			// Check that the previous query has validated the booking
			rbQuery.append(" WHERE EXISTS (")
				.append(" SELECT 1 FROM rbs.booking")
				.append(" WHERE id = ?")
				.append(" AND status = ?)");
			rbValues.add(bookingId)
				.add(VALIDATED.status());
						
			// Get concurrent bookings that must be refused
			rbQuery.append(" AND id in (")
				.append(" SELECT id FROM rbs.booking")
				.append(" WHERE resource_id = ?")
				.append(" AND status = ?")
				.append(" AND (")
				.append("( start_date <= (SELECT start_date from validated_booking) AND (SELECT start_date from validated_booking) < end_date )")
				.append(" OR ( start_date < (SELECT end_date from validated_booking) AND (SELECT end_date from validated_booking) <= end_date )")
				.append(" OR ( (SELECT start_date from validated_booking) <= start_date AND start_date < (SELECT end_date from validated_booking) )")
				.append(" OR ( (SELECT start_date from validated_booking) < end_date AND end_date <= (SELECT end_date from validated_booking) )")
				.append("));");
			rbValues.add(resourceId)
				.add(CREATED.status());
			
			statementsBuilder.prepared(rbQuery.toString(), rbValues);
			
			Sql.getInstance().transaction(statementsBuilder.build(), validRowsResultHandler(handler));
		}
	}

	@Override
	public void listBookingsByResource(final String resourceId, 
			final Handler<Either<String, JsonArray>> handler){
		String query = "SELECT * FROM rbs.booking WHERE resource_id = ?;";
		JsonArray values = new JsonArray();
		values.add(resourceId);
		
		Sql.getInstance().prepared(query, values, 
				validResultHandler(handler));
	}
	
	@Override
	public void listUnprocessedBookings(final List<String> groupsAndUserIds, final UserInfos user, 
			final Handler<Either<String, JsonArray>> handler){
		
		// Query
		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();
		query.append("SELECT DISTINCT b.* FROM rbs.booking AS b ")
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
		
		query.append(" OR r.owner = ?);");
		values.add(user.getUserId());
								
		// Send query to event bus
		Sql.getInstance().prepared(query.toString(), values, 
				validResultHandler(handler));
	}

}
