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

import fr.wseduc.webutils.http.Binding;
import fr.wseduc.webutils.http.HttpMethod;

/* Authorize if user has rights (owner or shared) on resourceType or on resource,
 * or if he is a local administrator for the resourceType's school_id
 */
public class TypeAndResourceAppendPolicy implements ResourcesProvider {

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
			query.append("SELECT count(*)")
				.append(" FROM rbs.resource AS r")
				.append(" INNER JOIN rbs.resource_type AS t ON r.type_id = t.id");
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

			// Authorize user if he is a local administrator for the resourceType's school_id
			Map<String, UserInfos.Function> functions = user.getFunctions();
			if (functions != null  && functions.containsKey(DefaultFunctions.ADMIN_LOCAL)) {
				Function adminLocal = functions.get(DefaultFunctions.ADMIN_LOCAL);
				if(adminLocal != null && adminLocal.getScope() != null && !adminLocal.getScope().isEmpty()) {
					query.append(" OR t.school_id IN ").append(Sql.listPrepared(adminLocal.getScope().toArray()));
					for (String schoolId : adminLocal.getScope()) {
						values.addString(schoolId);
					}
				}
			}

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
					Long count = SqlResult.countResult(message);
					handler.handle(count != null && count > 0);
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
