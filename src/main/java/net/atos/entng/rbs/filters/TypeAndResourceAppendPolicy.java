package net.atos.entng.rbs.filters;

import static org.entcore.common.sql.Sql.parseId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.atos.entng.rbs.controllers.ResourceController;

import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserInfos.Function;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Binding;
import fr.wseduc.webutils.http.HttpMethod;

/* Authorize if user has rights (owner or shared) on resourceType or on resource,
 * or if he is a local administrator for the resourceType's school_id
 */
public class TypeAndResourceAppendPolicy implements ResourcesProvider {

	private static final Logger log = LoggerFactory.getLogger(TypeAndResourceAppendPolicy.class);

	@Override
	public void authorize(final HttpServerRequest request, final Binding binding, final UserInfos user, final Handler<Boolean> handler) {
		SqlConf conf = SqlConfs.getConf(ResourceController.class.getName());
		String resourceId = request.params().get(conf.getResourceIdLabel());
		String bookingId = request.params().get("bookingId");
		boolean hasBooking = (bookingId != null && !bookingId.trim().isEmpty());

		if (resourceId != null && !resourceId.trim().isEmpty() && (parseId(resourceId) instanceof Integer)) {
			request.pause();
			// Replace character '.' by '-' in the called method's name
			String sharedMethod = binding.getServiceMethod().replaceAll("\\.", "-");

			// Groups and users
			final List<String> groupsAndUserIds = new ArrayList<>();
			groupsAndUserIds.add(user.getUserId());
			if (user.getGroupsIds() != null) {
				groupsAndUserIds.addAll(user.getGroupsIds());
			}

			// Query
			StringBuilder query = new StringBuilder();
			JsonArray values = new JsonArray();
			query.append("SELECT count(*) AS count,");

			// Subquery to return properties even if count = 0 (A query with GROUP BY returns nothing if count = 0)
			query.append("(SELECT row_to_json(props) AS properties FROM (")
					.append(" SELECT ty.school_id, re.periodic_booking, re.is_available") // trick to create JSON : selecting from a select clause avoids declaring a type and casting to this type
					.append(" FROM rbs.resource_type AS ty")
					.append(" INNER JOIN rbs.resource AS re ON ty.id = re.type_id")
					.append(" WHERE re.id = ?")
				.append(") props)");

			query.append(" FROM rbs.resource AS r")
					.append(" INNER JOIN rbs.resource_type AS t ON r.type_id = t.id");
			values.add(parseId(resourceId));

			if (hasBooking) {
				// Additional join when parameter bookingId is used
				query.append(" INNER JOIN rbs.booking AS b ON b.resource_id = r.id");
			}

			query.append(" LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
					.append(" LEFT JOIN rbs.resource_shares AS rs ON r.id = rs.resource_id")
					.append(" WHERE ((ts.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray())).append(" AND ts.action = ?)");
			for (String groupOruser : groupsAndUserIds) {
				values.add(groupOruser);
			}
			values.add(sharedMethod);

			query.append(" OR (rs.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray())).append(" AND rs.action = ?)");
			for (String groupOruser : groupsAndUserIds) {
				values.add(groupOruser);
			}
			values.add(sharedMethod);

			query.append(" OR t.owner = ? OR r.owner = ?");
			values.add(user.getUserId()).add(user.getUserId());
			if(isDeleteBooking(binding)) {
				query.append(" OR b.owner = ?"); // Owner can delete his booking
				values.add(user.getUserId());
			}

			query.append(") AND r.id = ?");
			values.add(Sql.parseId(resourceId));
			if (hasBooking) {
				query.append(" AND b.id = ?");
				values.add(Sql.parseId(bookingId));
			}

			if (isCreatePeriodicBooking(binding)) {
				// Check that the resource allows periodic_booking and that it is available
				query.append(" AND r.periodic_booking = ?")
					.append(" AND r.is_available = ?");
				values.add(true).add(true);
			}
			else if(isCreateBooking(binding)) {
				// Check that the resource is available
				query.append(" AND r.is_available = ?");
				values.add(true);
			}
			else if(isUpdateBooking(binding) || isUpdatePeriodicBooking(binding)) {
				// Check that the resource is available and that the user is the booking's owner
				query.append(" AND r.is_available = ?")
					.append(" AND b.owner = ?");
				values.add(true)
					.add(user.getUserId());
			}

			// Execute
			Sql.getInstance().prepared(query.toString(), values, new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> message) {
					request.resume();
					Either<String, JsonObject> result = SqlResult.validUniqueResult(message);

					if(result.isLeft()) {
						log.error(result.left());
						handler.handle(false);
					}
					else {
						JsonObject value = result.right().getValue();
						if(value == null) {
							log.error("Error : SQL request in filter TypeAndResourceAppendPolicy should not return null");
							handler.handle(false);
							return;
						}

						// Authorize if count > 0
						Long count = (Long) value.getNumber("count", 0);
						if(count != null && count > 0) {
							handler.handle(true);
							return;
						}


						// Else authorize if user is a local admin for the resourceType's school_id
						if(isUpdateBooking(binding) || isUpdatePeriodicBooking(binding)) {
							// Only the owner can update his booking. Local admins can't
							handler.handle(false);
							return;
						}

						String props = value.getString("properties", null);
						JsonObject properties = new JsonObject(props);

						String schoolId = properties.getString("school_id", null);
						if(schoolId == null || schoolId.trim().isEmpty()) {
							log.error("school_id not found");
							handler.handle(false);
							return;
						}

						Boolean isPeriodicBooking = properties.getBoolean("periodic_booking");
						Boolean isAvailable = properties.getBoolean("is_available");

						if (isCreatePeriodicBooking(binding)) {
							// Check that the resource allows periodic_booking and that it is available
							if(!isPeriodicBooking || !isAvailable) {
								handler.handle(false);
								return;
							}
						}
						else if(isCreateBooking(binding)) {
							// Check that the resource is available
							if(!isAvailable) {
								handler.handle(false);
								return;
							}
						}

						Map<String, UserInfos.Function> functions = user.getFunctions();
						if (functions != null  && functions.containsKey(DefaultFunctions.ADMIN_LOCAL)) {
							Function adminLocal = functions.get(DefaultFunctions.ADMIN_LOCAL);
							if(adminLocal != null && adminLocal.getScope() != null && adminLocal.getScope().contains(schoolId)) {
								handler.handle(true);
								return;
							}
						}

						handler.handle(false);
					}
				}
			});
		} else {
			handler.handle(false);
		}
	}

	private boolean isCreateBooking(final Binding binding) {
		return (HttpMethod.POST.equals(binding.getMethod())
				&& "net.atos.entng.rbs.controllers.BookingController|createBooking".equals(binding.getServiceMethod()));
	}

	private boolean isCreatePeriodicBooking(final Binding binding) {
		return (HttpMethod.POST.equals(binding.getMethod())
				&& "net.atos.entng.rbs.controllers.BookingController|createPeriodicBooking".equals(binding.getServiceMethod()));
	}

	private boolean isUpdateBooking(final Binding binding) {
		return (HttpMethod.PUT.equals(binding.getMethod())
				&& "net.atos.entng.rbs.controllers.BookingController|updateBooking".equals(binding.getServiceMethod()));
	}

	private boolean isUpdatePeriodicBooking(final Binding binding) {
		return (HttpMethod.PUT.equals(binding.getMethod())
				&& "net.atos.entng.rbs.controllers.BookingController|updatePeriodicBooking".equals(binding.getServiceMethod()));
	}

	private boolean isDeleteBooking(final Binding binding) {
		return (HttpMethod.DELETE.equals(binding.getMethod())
				&& "net.atos.entng.rbs.controllers.BookingController|deleteBooking".equals(binding.getServiceMethod()));
	}

}
