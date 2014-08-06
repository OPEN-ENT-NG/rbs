package fr.wseduc.rbs.service;

import static fr.wseduc.rbs.BookingStatus.CREATED;
import static fr.wseduc.rbs.BookingStatus.VALIDATED;
import static org.entcore.common.sql.SqlResult.validResultHandler;
import static org.entcore.common.sql.SqlResult.validRowsResultHandler;
import static org.entcore.common.sql.SqlResult.validUniqueResultHandler;

import java.util.List;

import org.entcore.common.sql.Sql;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public class BookingServiceSqlImpl implements BookingService {

	@Override
	public void createBooking(final Long resourceId, final JsonObject data, final UserInfos user,
			final Handler<Either<String, JsonObject>> handler) {

		// Query
		StringBuilder query = new StringBuilder();
		query.append("INSERT INTO rbs.booking")
				.append("(resource_id, owner, booking_reason, status, start_date, end_date)")
				.append(" SELECT  ?, ?, ?,")
		
				// If validation is activated, the booking is created with status "created".
				// Otherwise, it is created with status "validated".
				// TODO V2 : la reservation doit etre automatiquement validee si le demandeur est valideur
				.append(" (SELECT CASE ")
				.append(  " WHEN (t.validation IS true) THEN ").append(CREATED.status())
				.append(  " ELSE ").append(VALIDATED.status())
				.append(  " END")
				.append(  " FROM rbs.resource_type AS t")
				.append(  " INNER JOIN rbs.resource AS r ON r.type_id = t.id")
				.append(  " WHERE r.id = ?),")
				
				// Unix timestamps are converted into postgresql timestamps.
				.append(" to_timestamp(?), to_timestamp(?)");
		
		// Check that there does not exist a validated booking that overlaps the new booking.
		query.append(" WHERE NOT EXISTS (")
				.append("SELECT id FROM rbs.booking")
				.append(" WHERE resource_id = ?")
				.append(" AND status = ").append(VALIDATED.status())
				.append(" AND (")
				.append("( start_date >= to_timestamp(?) AND start_date < to_timestamp(?) )")
				.append(" OR ( end_date > to_timestamp(?) AND end_date <= to_timestamp(?) )")
				.append(")) RETURNING id;");

		JsonArray values = new JsonArray();
		Object startDate = data.getValue("start_date");
		Object endDate = data.getValue("end_date");

		// Values for the INSERT clause
		values.add(resourceId)
				.add(user.getUserId())
				.add(data.getValue("booking_reason"))
				.add(resourceId)
				.add(startDate)
				.add(endDate);
		// Values for the NOT EXISTS clause
		values.add(resourceId)
				.add(startDate)
				.add(endDate)
				.add(startDate)
				.add(endDate);

		// Send query to eventbus
		Sql.getInstance().prepared(query.toString(), values,
				validUniqueResultHandler(handler));
	}

	@Override
	public void updateBooking(final String bookingId, final JsonObject data,
			final Handler<Either<String, JsonObject>> handler) {

		/*
		 * TODO : Après modification, une réservation (soumise à un circuit validation) doit à nouveau être validée.
		 * NB : on peut modifier une demande même si elle est validée ou refusée
		 */
		
		// Query
		JsonArray values = new JsonArray();
		StringBuilder sb = new StringBuilder();
		for (String fieldname : data.getFieldNames()) {
			addFieldToUpdate(sb, fieldname, data, values);
		}

		StringBuilder query = new StringBuilder();
		query.append("UPDATE rbs.booking")
				.append(" SET ")
				.append(sb.toString())
				.append("modified = NOW()")
				.append(" WHERE id = ?;");
		values.add(bookingId);

		// Send query to eventbus
		Sql.getInstance().prepared(query.toString(), values,
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
				validResultHandler(handler));
	}

}
