package net.atos.entng.rbs.service;

import java.util.List;

import org.entcore.common.service.CrudService;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public interface ResourceService extends CrudService {

	public void listResources(final List<String> groupsAndUserIds, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler);

	public void updateResource(final String id, final JsonObject data,
			final Handler<Either<String, JsonObject>> handler);

	public void getBookingOwnersIds(final long resourceId, final Handler<Either<String, JsonArray>> handler);

	/**
	 * Get max_delay and min_delay of resource, owner, school_id and managers (userIds and groupIds) of resourceType
	 */
	public void getDelaysAndTypeProperties(long resourceId, Handler<Either<String, JsonObject>> handler);
}
